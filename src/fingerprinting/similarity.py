"""
Similarity Engine — Phase 3.

Computes multi-dimensional hybrid similarity and pairwise distance matrices
between EmailFingerprint instances for campaign clustering.
"""
"""
Similarity Engine — Phase 3.

Computes multi-dimensional hybrid similarity and pairwise distance matrices
between EmailFingerprint instances for campaign clustering.
"""
import os
import sys

# Ensure repository root is on sys.path so 'src' can always be imported
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

from typing import Dict, Any, List, Set
import numpy as np
from src.fingerprinting.email_fingerprint import EmailFingerprint
DEFAULT_WEIGHTS = {
    "content": 0.50,
    "infra": 0.35,
    "struct": 0.15,
}


def compute_content_similarity(fp1: EmailFingerprint, fp2: EmailFingerprint) -> float:
    """Cosine similarity between L2-normalized 64-dim text vectors."""
    dot = float(np.dot(fp1.text_vector, fp2.text_vector))
    return max(0.0, min(dot, 1.0))


def compute_infrastructure_similarity(fp1: EmailFingerprint, fp2: EmailFingerprint) -> float:
    """Calculates shared technical infrastructure score (0.0 to 1.0)."""
    score = 0.0
    i1, i2 = fp1.raw_infra, fp2.raw_infra

    # 1. Matching ASN (+0.25)
    if i1.get("asn") and i2.get("asn") and i1["asn"] == i2["asn"]:
        score += 0.25

    # 2. Matching Originating IP (+0.25)
    if i1.get("origin_ip") and i2.get("origin_ip") and i1["origin_ip"] == i2["origin_ip"]:
        score += 0.25

    # 3. Matching Return-Path or Sender Domain (+0.25)
    rp_match = i1.get("return_path_domain") and i1["return_path_domain"] == i2.get("return_path_domain")
    sd_match = i1.get("sender_domain") and i1["sender_domain"] == i2.get("sender_domain")
    if rp_match or sd_match:
        score += 0.25

    # 4. Jaccard similarity of target URL domains (+0.25)
    u1: Set[str] = set(i1.get("url_domains", []))
    u2: Set[str] = set(i2.get("url_domains", []))
    if u1 and u2:
        jaccard = len(u1.intersection(u2)) / float(len(u1.union(u2)))
        score += 0.25 * jaccard

    return min(score, 1.0)


def compute_structural_similarity(fp1: EmailFingerprint, fp2: EmailFingerprint) -> float:
    """Normalized Manhattan distance over the 14 bounded numerical features."""
    keys = list(fp1.numeric_features.keys())
    if not keys:
        return 1.0
    v1 = np.array([fp1.numeric_features[k] for k in keys], dtype=np.float32)
    v2 = np.array([fp2.numeric_features[k] for k in keys], dtype=np.float32)
    
    # Each feature is scaled between 0 and 1; average absolute error:
    mean_diff = float(np.mean(np.abs(v1 - v2)))
    return max(0.0, 1.0 - mean_diff)


def compute_hybrid_similarity(
    fp1: EmailFingerprint,
    fp2: EmailFingerprint,
    weights: Dict[str, float] = None,
) -> Dict[str, Any]:
    """
    Computes overall weighted similarity and identifies shared forensic indicators.
    """
    if weights is None:
        weights = DEFAULT_WEIGHTS

    s_content = compute_content_similarity(fp1, fp2)
    s_infra = compute_infrastructure_similarity(fp1, fp2)
    s_struct = compute_structural_similarity(fp1, fp2)

    overall = (
        weights["content"] * s_content +
        weights["infra"] * s_infra +
        weights["struct"] * s_struct
    )
    overall = max(0.0, min(overall, 1.0))
    distance = max(0.0, 1.0 - overall)

    # Explain shared forensic indicators
    shared = []
    if s_content >= 0.70:
        shared.append("Highly similar email wording / lure template")
    if fp1.raw_infra.get("asn") and fp1.raw_infra["asn"] == fp2.raw_infra.get("asn"):
        shared.append(f"Shared network ASN ({fp1.raw_infra['asn']})")
    if fp1.raw_infra.get("origin_ip") and fp1.raw_infra["origin_ip"] == fp2.raw_infra.get("origin_ip"):
        shared.append(f"Shared originating IP ({fp1.raw_infra['origin_ip']})")
    common_urls = set(fp1.raw_infra.get("url_domains", [])).intersection(set(fp2.raw_infra.get("url_domains", [])))
    if common_urls:
        shared.append(f"Shared phishing target domain(s): {list(common_urls)}")

    return {
        "overall_similarity": round(overall, 4),
        "distance": round(distance, 4),
        "content_similarity": round(s_content, 4),
        "infra_similarity": round(s_infra, 4),
        "structural_similarity": round(s_struct, 4),
        "shared_indicators": shared,
    }


def compute_pairwise_distance_matrix(fingerprints: List[EmailFingerprint]) -> np.ndarray:
    """Compute N x N pairwise distance matrix for clustering algorithms."""
    n = len(fingerprints)
    dist_matrix = np.zeros((n, n), dtype=np.float32)

    for i in range(n):
        for j in range(i + 1, n):
            res = compute_hybrid_similarity(fingerprints[i], fingerprints[j])
            dist = res["distance"]
            dist_matrix[i, j] = dist
            dist_matrix[j, i] = dist
    return dist_matrix


if __name__ == "__main__":
    from src.fingerprinting.email_fingerprint import extract_email_fingerprint

    # Case A: Phishing Campaign 1 (PayPal lure)
    e1 = {
        "email_id": "EMAIL-001",
        "subject": "URGENT: Your PayPal account has been restricted",
        "body": "Dear user, suspicious activity was detected. Verify your identity immediately: https://secure-login-example.com/verify",
        "sender_domain": "paypal-notice.com",
        "return_path_domain": "relay1.net",
        "origin_ip": "192.168.1.10",
        "asn": "AS12345",
        "urls": ["https://secure-login-example.com/verify"]
    }
    # Case B: Same PayPal campaign, varied wording
    e2 = {
        "email_id": "EMAIL-002",
        "subject": "ACTION REQUIRED: Your PayPal profile was limited",
        "body": "Dear customer, unauthorized access detected on your account. Please confirm your credentials now: https://secure-login-example.com/verify",
        "sender_domain": "paypal-security.com",
        "return_path_domain": "relay1.net",
        "origin_ip": "192.168.1.10",
        "asn": "AS12345",
        "urls": ["https://secure-login-example.com/verify"]
    }
    # Case C: Completely unrelated Netflix lure
    e3 = {
        "email_id": "EMAIL-003",
        "subject": "Your Netflix membership payment failed",
        "body": "We were unable to process your subscription. Update your credit card billing information: https://tommyhilfigersale.top",
        "sender_domain": "netflix-billing.com",
        "return_path_domain": "netflix-billing.com",
        "origin_ip": "10.0.0.5",
        "asn": "AS99999",
        "urls": ["https://tommyhilfigersale.top"]
    }

    fp1 = extract_email_fingerprint(e1)
    fp2 = extract_email_fingerprint(e2)
    fp3 = extract_email_fingerprint(e3)

    sim_1_2 = compute_hybrid_similarity(fp1, fp2)
    sim_1_3 = compute_hybrid_similarity(fp1, fp3)

    print("=" * 70)
    print("HYBRID SIMILARITY ENGINE TEST")
    print("=" * 70)
    print(f"\n1. Comparison: EMAIL-001 vs EMAIL-002 (Same Campaign - PayPal):")
    print(f"   Overall Similarity: {sim_1_2['overall_similarity']:.2%}  (Distance: {sim_1_2['distance']:.4f})")
    print(f"   - Content Sim:      {sim_1_2['content_similarity']:.2%}")
    print(f"   - Infra Sim:        {sim_1_2['infra_similarity']:.2%}")
    print(f"   - Structural Sim:   {sim_1_2['structural_similarity']:.2%}")
    print(f"   Shared Signals:     {sim_1_2['shared_indicators']}")

    print(f"\n2. Comparison: EMAIL-001 vs EMAIL-003 (Different Campaigns):")
    print(f"   Overall Similarity: {sim_1_3['overall_similarity']:.2%}  (Distance: {sim_1_3['distance']:.4f})")
    print(f"   - Content Sim:      {sim_1_3['content_similarity']:.2%}")
    print(f"   - Infra Sim:        {sim_1_3['infra_similarity']:.2%}")
    print(f"   - Structural Sim:   {sim_1_3['structural_similarity']:.2%}")
    print(f"   Shared Signals:     {sim_1_3['shared_indicators']}")
    print("\n" + "=" * 70)