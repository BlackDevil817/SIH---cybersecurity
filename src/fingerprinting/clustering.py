"""
Campaign Clustering & Stable ID Assignment — Phases 4 & 5.

Implements:
1. DBSCAN clustering on precomputed hybrid distance matrices.
2. Stable Campaign ID assignment (campaign-2026-xxxx) with cluster prototypes.
3. Spring Boot contract adapter matching incoming payloads to active campaigns.
"""
import os
import sys
from typing import Dict, Any, List, Optional, Tuple
import numpy as np
from sklearn.cluster import DBSCAN

# Ensure repository root is on sys.path
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

from src.fingerprinting.email_fingerprint import EmailFingerprint, extract_email_fingerprint
from src.fingerprinting.similarity import compute_pairwise_distance_matrix, compute_hybrid_similarity


class Campaign:
    """Represents a discovered phishing campaign with a stable prototype."""

    def __init__(self, campaign_id: str, prototype: EmailFingerprint, member_ids: List[str]):
        self.campaign_id = campaign_id
        self.prototype = prototype
        self.member_ids = list(member_ids)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "campaign_id": self.campaign_id,
            "member_count": len(self.member_ids),
            "member_ids": self.member_ids,
            "dominant_asn": self.prototype.raw_infra.get("asn"),
            "dominant_ip": self.prototype.raw_infra.get("origin_ip"),
            "target_domains": self.prototype.raw_infra.get("url_domains", []),
        }


class CampaignManager:
    """Manages persistent campaign registries and prototype matching."""

    def __init__(
        self,
        eps: float = 0.30,
        min_samples: int = 2,
        match_threshold: float = 0.65,
        id_prefix: str = "campaign-2026-",
    ):
        self.eps = eps
        self.min_samples = min_samples
        self.match_threshold = match_threshold
        self.id_prefix = id_prefix
        self.campaigns: Dict[str, Campaign] = {}
        self._next_campaign_num = 1

    def cluster_and_register(self, fingerprints: List[EmailFingerprint]) -> Dict[str, Any]:
        """Run DBSCAN on a batch of fingerprints and mint stable campaign identifiers."""
        if len(fingerprints) < self.min_samples:
            return {"num_campaigns": len(self.campaigns), "assignments": {fp.email_id: None for fp in fingerprints}}

        dist_matrix = compute_pairwise_distance_matrix(fingerprints)
        db = DBSCAN(eps=self.eps, min_samples=self.min_samples, metric="precomputed")
        cluster_labels = db.fit_predict(dist_matrix)

        assignments = {}
        unique_labels = set(cluster_labels)

        for label in unique_labels:
            if label == -1:
                for idx, l in enumerate(cluster_labels):
                    if l == -1:
                        assignments[fingerprints[idx].email_id] = None
                continue

            member_indices = [i for i, l in enumerate(cluster_labels) if l == label]
            cluster_fps = [fingerprints[i] for i in member_indices]
            member_ids = [fp.email_id for fp in cluster_fps]

            sub_matrix = dist_matrix[np.ix_(member_indices, member_indices)]
            center_idx = int(np.argmin(np.mean(sub_matrix, axis=1)))
            prototype = cluster_fps[center_idx]

            matched_existing_id = None
            for existing_id, campaign in self.campaigns.items():
                sim = compute_hybrid_similarity(prototype, campaign.prototype)
                if sim["overall_similarity"] >= self.match_threshold:
                    matched_existing_id = existing_id
                    break

            if matched_existing_id:
                cid = matched_existing_id
                self.campaigns[cid].member_ids.extend(
                    [m for m in member_ids if m not in self.campaigns[cid].member_ids]
                )
            else:
                cid = f"{self.id_prefix}{self._next_campaign_num:04d}"
                self._next_campaign_num += 1
                self.campaigns[cid] = Campaign(cid, prototype, member_ids)

            for mid in member_ids:
                assignments[mid] = cid

        return {
            "num_campaigns": len(self.campaigns),
            "assignments": assignments,
        }

    def match_for_backend_contract(self, payload: Dict[str, Any]) -> Tuple[Optional[str], List[Dict[str, Any]]]:
        """
        Adapts the Spring Boot backend payload, runs fingerprint comparison against
        active campaigns, and returns (clusterId, similarCampaigns).
        """
        # Convert Spring Boot camelCase payload to EmailFingerprint input
        f = payload.get("features", {})
        urls = [f"https://{d}/login" for d in payload.get("urlDomains", [])]

        synthetic_body = " ".join(
            ["verify account password credentials security urgent"] * int(f.get("urgencyKeywordCount", 1))
        )

        fp_data = {
            "email_id": payload.get("email_id", "INCOMING"),
            "subject": f"Notice regarding {payload.get('senderDomain', 'account')}",
            "body": synthetic_body,
            "sender_domain": payload.get("senderDomain", ""),
            "return_path_domain": payload.get("returnPathDomain", ""),
            "dkim_domain": payload.get("dkimDomain", ""),
            "origin_ip": payload.get("originatingIp", ""),
            "asn": payload.get("asn", ""),
            "urls": urls,
        }
        fp = extract_email_fingerprint(fp_data)

        scored_campaigns = []
        for cid, campaign in self.campaigns.items():
            sim_res = compute_hybrid_similarity(fp, campaign.prototype)
            sim_score = sim_res["overall_similarity"]

            # Compute shared indicator names matching the contract:
            shared_ind = []
            if fp.raw_infra.get("url_domains") and set(fp.raw_infra["url_domains"]).intersection(set(campaign.prototype.raw_infra.get("url_domains", []))):
                shared_ind.append("urlDomains")
            if fp.raw_infra.get("asn") and fp.raw_infra["asn"] == campaign.prototype.raw_infra.get("asn"):
                shared_ind.append("asn")
            if fp.raw_infra.get("origin_ip") and fp.raw_infra["origin_ip"] == campaign.prototype.raw_infra.get("origin_ip"):
                shared_ind.append("originatingIp")
            if fp.raw_infra.get("sender_domain") and fp.raw_infra["sender_domain"] == campaign.prototype.raw_infra.get("sender_domain"):
                shared_ind.append("senderDomain")

            scored_campaigns.append({
                "campaignId": cid,
                "similarityScore": round(sim_score, 2),
                "sharedIndicators": shared_ind,
            })

        # Sort by similarity descending
        scored_campaigns.sort(key=lambda x: x["similarityScore"], reverse=True)

        cluster_id = None
        similar_campaigns = []

        if scored_campaigns and scored_campaigns[0]["similarityScore"] >= self.match_threshold:
            cluster_id = scored_campaigns[0]["campaignId"]
            similar_campaigns = [sc for sc in scored_campaigns if sc["similarityScore"] >= 0.50]
        elif scored_campaigns:
            similar_campaigns = [sc for sc in scored_campaigns if sc["similarityScore"] >= 0.50]

        return cluster_id, similar_campaigns


if __name__ == "__main__":
    cm = CampaignManager()

    # Pre-seed a known campaign (campaign-2026-0084)
    seed_email = {
        "email_id": "EMAIL-HIST-01",
        "subject": "PayPal Security Alert",
        "body": "Immediate verification required for credentials",
        "sender_domain": "paypa1-secure.com",
        "return_path_domain": "bounce.paypa1-secure.com",
        "origin_ip": "185.220.101.7",
        "asn": "AS44066",
        "urls": ["https://paypa1-login-verify.com/verify"],
    }
    fp_seed = extract_email_fingerprint(seed_email)
    cm.campaigns["campaign-2026-0084"] = Campaign("campaign-2026-0084", fp_seed, ["EMAIL-HIST-01"])

    # Test with Spring Boot payload
    backend_payload = {
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
            "spfResult": "fail", "dkimResult": "none", "dmarcResult": "fail",
            "senderReplyToMismatch": True, "displayNameSpoof": True,
            "numUrls": 3, "numShortenedUrls": 1, "hasIpLiteralUrl": False,
            "urgencyKeywordCount": 4, "credentialKeywordCount": 2,
            "attachmentCount": 0, "subjectLength": 47, "bodyLength": 812,
            "hopCount": 5, "sendingHourUtc": 3,
        }
    }

    cid, similar = cm.match_for_backend_contract(backend_payload)
    print("=" * 65)
    print("BACKEND CONTRACT CLUSTERING ADAPTER TEST")
    print("=" * 65)
    print(f"Matched clusterId: {cid}")
    print(f"similarCampaigns:  {similar}")
    print("=" * 65)