"""
Forensic Intelligence & Campaign Clustering API — Phase 6.

Exposes endpoints for:
- POST /cluster/match   (Match an email against known threat campaigns)
- POST /url/predict     (Scan individual URLs for phishing indicators)
- GET  /campaigns       (List all active campaign prototypes)
- GET  /health          (System health and component status)
"""
import os
import sys
from typing import Dict, Any, List, Optional
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

# Ensure repository root is on sys.path
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

from src.models.url_reputation import predict_url
from src.fingerprinting.email_fingerprint import extract_email_fingerprint
from src.fingerprinting.clustering import CampaignManager

app = FastAPI(
    title="AI-Powered Email Threat & Forensic Campaign API",
    description="ML Dev B: URL Reputation, Email Fingerprinting, and Campaign Clustering",
    version="1.0.0",
)

# Global persistent Campaign Manager
campaign_manager = CampaignManager(eps=0.30, min_samples=2, match_threshold=0.70)


def _seed_initial_campaigns():
    """Pre-seed sample historical threat campaigns so the API works out-of-the-box."""
    seed_data = [
        # Campaign 1: PayPal credential harvesting
        {
            "email_id": "EMAIL-001",
            "subject": "URGENT: Your PayPal account has been restricted",
            "body": "Suspicious activity detected. Verify your identity immediately: https://secure-login-example.com/verify",
            "sender_domain": "paypal-notice.com",
            "return_path_domain": "relay1.net",
            "origin_ip": "192.168.1.10",
            "asn": "AS12345",
            "urls": ["https://secure-login-example.com/verify"],
        },
        {
            "email_id": "EMAIL-002",
            "subject": "ACTION REQUIRED: Your PayPal profile was limited",
            "body": "Unauthorized access detected. Please confirm credentials now: https://secure-login-example.com/verify",
            "sender_domain": "paypal-security.com",
            "return_path_domain": "relay1.net",
            "origin_ip": "192.168.1.10",
            "asn": "AS12345",
            "urls": ["https://secure-login-example.com/verify"],
        },
        # Campaign 2: Netflix billing scam
        {
            "email_id": "EMAIL-003",
            "subject": "Your Netflix membership payment failed",
            "body": "We were unable to process your subscription. Update billing: https://tommyhilfigersale.top",
            "sender_domain": "netflix-billing.com",
            "return_path_domain": "netflix-billing.com",
            "origin_ip": "10.0.0.5",
            "asn": "AS99999",
            "urls": ["https://tommyhilfigersale.top"],
        },
        {
            "email_id": "EMAIL-004",
            "subject": "Netflix: Immediate payment required to keep streaming",
            "body": "Your monthly subscription payment was declined. Update billing: https://tommyhilfigersale.top",
            "sender_domain": "netflix-notice.com",
            "return_path_domain": "netflix-billing.com",
            "origin_ip": "10.0.0.5",
            "asn": "AS99999",
            "urls": ["https://tommyhilfigersale.top"],
        },
    ]
    fps = [extract_email_fingerprint(e) for e in seed_data]
    campaign_manager.cluster_and_register(fps)


_seed_initial_campaigns()


# --- Pydantic Request/Response Models ---
# --- Pydantic Request/Response Models ---

class EmailMatchRequest(BaseModel):
    email_id: str = Field(..., examples=["EMAIL-100"])
    subject: str = Field(..., examples=["Your account requires verification"])
    body: str = Field(..., examples=["Click here to verify credentials: https://secure-login-example.com/verify"])
    sender_domain: str = Field(..., examples=["paypal-alerts.com"])
    return_path_domain: Optional[str] = Field(None, examples=["relay1.net"])
    dkim_domain: Optional[str] = Field(None, examples=["relay1.net"])
    origin_ip: Optional[str] = Field(None, examples=["192.168.1.10"])
    asn: Optional[str] = Field(None, examples=["AS12345"])
    urls: List[str] = Field(default_factory=list, examples=[["https://secure-login-example.com/verify"]])


class EmailMatchResponse(BaseModel):
    email_id: str
    campaign_id: Optional[str]
    match: bool
    similarity: float
    confidence: float
    related_emails: List[str]
    shared_signals: List[str]


class URLPredictRequest(BaseModel):
    url: str = Field(..., examples=["https://secure-login-example.com/verify"])


class URLPredictResponse(BaseModel):
    url: str
    prediction: str
    risk_score: float
    model_probability: float
    top_signals: List[str]
class URLPredictResponse(BaseModel):
    url: str
    prediction: str
    risk_score: float
    model_probability: float
    top_signals: List[str]


# --- API Routes ---

@app.get("/health")
def health_check() -> Dict[str, Any]:
    return {
        "status": "online",
        "service": "SIH-2026 Email Threat Intelligence & Clustering",
        "registered_campaigns": len(campaign_manager.campaigns),
    }


@app.post("/url/predict", response_model=URLPredictResponse)
def predict_url_endpoint(req: URLPredictRequest):
    try:
        result = predict_url(req.url)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/cluster/match", response_model=EmailMatchResponse)
def match_campaign_endpoint(req: EmailMatchRequest):
    try:
        email_data = req.model_dump()
        fp = extract_email_fingerprint(email_data)
        match_result = campaign_manager.match_email(fp)
        return match_result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/campaigns")
def list_campaigns():
    return {
        "total_campaigns": len(campaign_manager.campaigns),
        "campaigns": [c.to_dict() for c in campaign_manager.campaigns.values()],
    }


if __name__ == "__main__":
    import json
    from fastapi.testclient import TestClient

    client = TestClient(app)

    print("=" * 75)
    print("FASTAPI TEST SUITE (Self-Test)")
    print("=" * 75)

    # 1. Health test
    h_res = client.get("/health")
    print(f"1. GET /health -> Status: {h_res.status_code}")
    print(f"   Response: {h_res.json()}")

    # 2. URL predict test
    url_payload = {"url": "https://secure-login-example.com/verify"}
    u_res = client.post("/url/predict", json=url_payload)
    print(f"\n2. POST /url/predict -> Status: {u_res.status_code}")
    print(f"   Response: {u_res.json()}")

    # 3. Cluster match test (EMAIL-100)
    email_payload = {
        "email_id": "EMAIL-100",
        "subject": "Your account requires verification",
        "body": "Your profile has been temporarily disabled. Please verify credentials now: https://secure-login-example.com/verify",
        "sender_domain": "paypal-alerts.com",
        "return_path_domain": "relay1.net",
        "dkim_domain": "relay1.net",
        "origin_ip": "192.168.1.10",
        "asn": "AS12345",
        "urls": ["https://secure-login-example.com/verify"],
    }
    m_res = client.post("/cluster/match", json=email_payload)
    print(f"\n3. POST /cluster/match -> Status: {m_res.status_code}")
    print("   Response JSON:")
    print(json.dumps(m_res.json(), indent=4))
    print("=" * 75)