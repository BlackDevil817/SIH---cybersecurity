"""
app.py — Unified ML Service Entry Point.

Combines:
- Dev A: Phishing classification & SHAP risk breakdown (auth, content, url)
- Dev B: URL reputation, infrastructure fingerprinting & DBSCAN campaign clustering

Implements: POST /api/ml/cluster (Spring Boot API Contract)
"""
from __future__ import annotations
import os
import sys
import json
import logging
from pathlib import Path
from typing import List, Optional, Dict, Any

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

# Ensure project root is on sys.path
REPO_ROOT = os.path.dirname(os.path.abspath(__file__))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

from src.classification.feature_extraction import FeatureExtractionError
from src.classification.phishing_classifier import PhishingClassifier
from src.classification.score_breakdown import ScoreBreakdownEngine, build_risk_breakdown
from src.fingerprinting.email_fingerprint import extract_email_fingerprint
from src.fingerprinting.similarity import compute_hybrid_similarity
from src.fingerprinting.clustering import CampaignManager, Campaign

logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"))
log = logging.getLogger("ml_service")

from contextlib import asynccontextmanager

MODEL_PATH = Path(os.environ.get("MODEL_PATH", "models/phishing_classifier.pkl"))
BACKGROUND_CSV = os.environ.get("SHAP_BACKGROUND_CSV")
PORT = int(os.environ.get("PORT", 8000))

_classifier: PhishingClassifier | None = None
_score_engine: ScoreBreakdownEngine | None = None
_campaign_manager: CampaignManager = CampaignManager(match_threshold=0.65)


def _seed_active_campaigns():
    """Pre-seed sample active threat campaigns (e.g. campaign-2026-0084)."""
    seed_payload = {
        "email_id": "HIST-0084",
        "subject": "",
        "body": "",
        "sender_domain": "paypa1-secure.com",
        "return_path_domain": "bounce.paypa1-secure.com",
        "origin_ip": "185.220.101.7",
        "asn": "AS44066",
        "urls": ["https://paypa1-login-verify.com", "https://bit.ly"],
    }
    fp = extract_email_fingerprint(seed_payload)
    _campaign_manager.campaigns["campaign-2026-0084"] = Campaign("campaign-2026-0084", fp, ["HIST-0084"])


def load_models():
    global _classifier, _score_engine
    try:
        _classifier = PhishingClassifier.load(MODEL_PATH)
        _score_engine = ScoreBreakdownEngine.from_classifier_bundle(MODEL_PATH, BACKGROUND_CSV)
        _seed_active_campaigns()
        log.info("Models and campaigns initialized successfully!")
    except FileNotFoundError as exc:
        log.error("Model unavailable at startup: %s", exc)


@asynccontextmanager
async def lifespan(app: FastAPI):
    load_models()
    yield


app = FastAPI(
    title="AI-Powered Email Threat Intel & Campaign Clustering Service",
    description="Unified Dev A (Classifier + SHAP) & Dev B (URL + Clustering) Service",
    version="1.0.0",
    lifespan=lifespan,
)


# --- Request & Response Models (Spring Boot Contract) ---

class ClusterRequest(BaseModel):
    senderDomain: str
    replyToDomain: str = ""
    returnPathDomain: str = ""
    dkimDomain: str = ""
    originatingIp: str = ""
    asn: str = ""
    ipRange: str = ""
    organization: str = ""
    urlDomains: List[str] = Field(default_factory=list)
    features: Dict[str, Any] = Field(default_factory=dict)


@app.get("/health")
def health():
    return {
        "status": "ok" if _classifier is not None else "model_unavailable",
        "model_path": str(MODEL_PATH),
        "registered_campaigns": len(_campaign_manager.campaigns),
    }


@app.post("/api/ml/cluster")
def cluster(request: ClusterRequest):
    if _classifier is None or _score_engine is None:
        raise HTTPException(status_code=503, detail="model_unavailable")

    payload = request.model_dump()

    # 1. Dev A: Classification
    try:
        result = _classifier.predict(payload)
    except FeatureExtractionError as exc:
        raise HTTPException(status_code=422, detail={"error": "feature_extraction_failed", "detail": str(exc)})
    except Exception as exc:
        log.exception("Prediction failed")
        raise HTTPException(status_code=500, detail={"error": "internal_error", "detail": str(exc)})

    # 2. Dev A: SHAP Score Breakdown
    risk_breakdown = build_risk_breakdown(result, _score_engine)

    # 3. Dev B: Campaign Clustering & Matching (Structured Payload Mode)
    urls = [f"https://{d}" for d in payload.get("urlDomains", [])]
    incoming_fp = extract_email_fingerprint({
        "email_id": "INCOMING",
        "subject": "",
        "body": "",
        "sender_domain": payload.get("senderDomain", ""),
        "return_path_domain": payload.get("returnPathDomain", ""),
        "dkim_domain": payload.get("dkimDomain", ""),
        "origin_ip": payload.get("originatingIp", ""),
        "asn": payload.get("asn", ""),
        "urls": urls,
    })

    # Structured weights: 70% infrastructure + 30% structural behavior
    backend_weights = {"content": 0.0, "infra": 0.70, "struct": 0.30}
    cluster_id = None
    similar_campaigns = []

    for cid, camp in _campaign_manager.campaigns.items():
        sim_res = compute_hybrid_similarity(incoming_fp, camp.prototype, weights=backend_weights)
        sim_score = sim_res["overall_similarity"]

        shared_ind = []
        if incoming_fp.raw_infra.get("url_domains") and set(incoming_fp.raw_infra["url_domains"]).intersection(set(camp.prototype.raw_infra.get("url_domains", []))):
            shared_ind.append("urlDomains")
        if incoming_fp.raw_infra.get("asn") and incoming_fp.raw_infra["asn"] == camp.prototype.raw_infra.get("asn"):
            shared_ind.append("asn")
        if incoming_fp.raw_infra.get("origin_ip") and incoming_fp.raw_infra["origin_ip"] == camp.prototype.raw_infra.get("origin_ip"):
            shared_ind.append("originatingIp")
        if incoming_fp.raw_infra.get("sender_domain") and incoming_fp.raw_infra["sender_domain"] == camp.prototype.raw_infra.get("sender_domain"):
            shared_ind.append("senderDomain")

        if sim_score >= 0.50:
            similar_campaigns.append({
                "campaignId": cid,
                "similarityScore": round(sim_score, 2),
                "sharedIndicators": shared_ind,
            })

    if similar_campaigns:
        similar_campaigns.sort(key=lambda x: x["similarityScore"], reverse=True)
        if similar_campaigns[0]["similarityScore"] >= _campaign_manager.match_threshold:
            cluster_id = similar_campaigns[0]["campaignId"]

    return {
        "clusterId": cluster_id,
        "confidence": result["confidence"],
        "label": result["label"],
        "similarCampaigns": similar_campaigns,
        "riskBreakdown": risk_breakdown,
    }


if __name__ == "__main__":
    from fastapi.testclient import TestClient

    # Explicitly load models for direct test runner
    load_models()
    client = TestClient(app)

    print("=" * 75)
    print("SPRING BOOT CONTRACT END-TO-END INTEGRATION TEST")
    print("=" * 75)

    sample_request = {
        "senderDomain": "paypa1-secure.com",
        "replyToDomain": "mail-reply.ru",
        "returnPathDomain": "bounce.paypa1-secure.com",
        "dkimDomain": "",
        "originatingIp": "185.220.101.7",
        "asn": "AS44066",
        "ipRange": "185.220.101.0/24",
        "organization": "Unknown Hosting Ltd",
        "urlDomains": ["paypa1-login-verify.com", "bit.ly"],
        "features": {
            "spfResult": "fail",
            "dkimResult": "none",
            "dmarcResult": "fail",
            "senderReplyToMismatch": True,
            "displayNameSpoof": True,
            "numUrls": 3,
            "numShortenedUrls": 1,
            "hasIpLiteralUrl": False,
            "urgencyKeywordCount": 4,
            "credentialKeywordCount": 2,
            "attachmentCount": 0,
            "subjectLength": 47,
            "bodyLength": 812,
            "hopCount": 5,
            "sendingHourUtc": 3,
        }
    }

    res = client.post("/api/ml/cluster", json=sample_request)
    print(f"Status Code: {res.status_code}")
    print("\nFull JSON Response (Spring Boot Contract Compliant):")
    print(json.dumps(res.json(), indent=2))
    print("=" * 75)