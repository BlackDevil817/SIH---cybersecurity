"""
Email Fingerprinting Module — Phase 2.

Extracts a reproducible, fixed-length composite fingerprint combining:
- Infrastructure indicators (IP, ASN, Sender Domain, Return-Path, DKIM, URL alignment)
- Content lexical indicators (length, casing, urgency, financial, credential triggers)
- Text vectorization (stateless, deterministic HashingVectorizer)
"""
import os
import sys
import re
from urllib.parse import urlparse
from typing import Dict, Any, List, Optional
import numpy as np
from sklearn.feature_extraction.text import HashingVectorizer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", ".."))
from src.models.url_reputation import predict_url

# Forensic keyword dictionaries
URGENCY_KEYWORDS = {"urgent", "immediate", "immediately", "action", "required", "suspend", "suspended", "24 hours", "terminate", "alert"}
FINANCIAL_KEYWORDS = {"invoice", "payment", "bank", "wire", "transfer", "billing", "receipt", "overdue", "crypto", "refund"}
CREDENTIAL_KEYWORDS = {"password", "credential", "verify", "verification", "login", "signin", "authenticate", "access", "reactivate"}

TEXT_VECTOR_DIM = 64
_vectorizer = HashingVectorizer(n_features=TEXT_VECTOR_DIM, alternate_sign=False, ngram_range=(1, 2))


def _extract_domain(url_or_email: str) -> str:
    """Extract clean domain without protocol or www."""
    s = str(url_or_email).strip().lower()
    if "@" in s:
        s = s.split("@")[-1]
    if "://" in s:
        p = urlparse(s)
        s = p.hostname or ""
    elif "/" in s:
        s = s.split("/")[0]
    return re.sub(r"^www\.", "", s)


class EmailFingerprint:
    """Encapsulates raw indicators and a fixed-length numerical representation."""

    def __init__(
        self,
        email_id: str,
        raw_infra: Dict[str, Any],
        numeric_features: Dict[str, float],
        text_vector: np.ndarray,
    ):
        self.email_id = email_id
        self.raw_infra = raw_infra
        self.numeric_features = numeric_features
        self.text_vector = text_vector
        
        # Concatenate normalized numeric indicators + text vector into fixed-length array
        num_vals = np.array(list(numeric_features.values()), dtype=np.float32)
        self.dense_vector = np.concatenate([num_vals, text_vector.astype(np.float32)])

    def to_dict(self) -> Dict[str, Any]:
        return {
            "email_id": self.email_id,
            "raw_infrastructure": self.raw_infra,
            "numeric_features": self.numeric_features,
            "vector_dimension": int(len(self.dense_vector)),
        }


def extract_email_fingerprint(email_data: Dict[str, Any]) -> EmailFingerprint:
    """
    Extract a comprehensive fingerprint from a raw email dictionary.
    
    Expected keys:
        - email_id (str)
        - subject (str)
        - body (str)
        - sender_domain (str)
        - return_path_domain (str, optional)
        - dkim_domain (str, optional)
        - origin_ip (str, optional)
        - asn (str, optional)
        - urls (list of str, optional)
    """
    email_id = str(email_data.get("email_id", "UNKNOWN"))
    subject = str(email_data.get("subject", ""))
    body = str(email_data.get("body", ""))
    full_text = f"{subject} {body}".lower()

    # 1. Infrastructure signals
    sender_dom = _extract_domain(email_data.get("sender_domain", ""))
    return_path_dom = _extract_domain(email_data.get("return_path_domain", sender_dom))
    dkim_dom = _extract_domain(email_data.get("dkim_domain", sender_dom))
    origin_ip = str(email_data.get("origin_ip", "")).strip()
    asn = str(email_data.get("asn", "")).strip()

    urls = email_data.get("urls", [])
    url_domains = list({_extract_domain(u) for u in urls if u})

    # Calculate max URL risk score using our Phase 1 model
    max_url_risk = 0.0
    for u in urls:
        try:
            res = predict_url(u)
            max_url_risk = max(max_url_risk, res["risk_score"])
        except Exception:
            pass

    # Domain mismatch / spoofing indicators
    domain_mismatch_return_path = 1.0 if (sender_dom and return_path_dom and sender_dom != return_path_dom) else 0.0
    domain_mismatch_dkim = 1.0 if (sender_dom and dkim_dom and sender_dom != dkim_dom) else 0.0
    url_domain_mismatch = 1.0 if (sender_dom and any(ud and ud != sender_dom for ud in url_domains)) else 0.0

    raw_infra = {
        "sender_domain": sender_dom,
        "return_path_domain": return_path_dom,
        "dkim_domain": dkim_dom,
        "origin_ip": origin_ip,
        "asn": asn,
        "url_domains": url_domains,
        "max_url_risk": max_url_risk,
    }

    # 2. Content lexical signals
    sub_len = float(len(subject))
    body_len = float(len(body))
    sub_upper_ratio = sum(1 for c in subject if c.isupper()) / max(sub_len, 1.0)
    body_upper_ratio = sum(1 for c in body if c.isupper()) / max(body_len, 1.0)
    sub_exclamations = float(subject.count("!"))
    body_exclamations = float(body.count("!"))

    urgency_hits = float(sum(1 for kw in URGENCY_KEYWORDS if kw in full_text))
    financial_hits = float(sum(1 for kw in FINANCIAL_KEYWORDS if kw in full_text))
    credential_hits = float(sum(1 for kw in CREDENTIAL_KEYWORDS if kw in full_text))

    # Numeric features (ordered and scaled to approx 0-1)
    numeric_features = {
        "domain_mismatch_return_path": domain_mismatch_return_path,
        "domain_mismatch_dkim": domain_mismatch_dkim,
        "url_domain_mismatch": url_domain_mismatch,
        "num_urls": min(float(len(urls)) / 5.0, 1.0),
        "max_url_risk": float(max_url_risk),
        "subject_length_norm": min(sub_len / 100.0, 1.0),
        "body_length_norm": min(body_len / 2000.0, 1.0),
        "subject_uppercase_ratio": sub_upper_ratio,
        "body_uppercase_ratio": body_upper_ratio,
        "subject_exclamation_norm": min(sub_exclamations / 3.0, 1.0),
        "body_exclamation_norm": min(body_exclamations / 5.0, 1.0),
        "urgency_score": min(urgency_hits / 3.0, 1.0),
        "financial_score": min(financial_hits / 3.0, 1.0),
        "credential_score": min(credential_hits / 3.0, 1.0),
    }

    # 3. Fixed-length text vector (64 dimensions)
    text_vec = _vectorizer.transform([full_text]).toarray()[0]

    return EmailFingerprint(
        email_id=email_id,
        raw_infra=raw_infra,
        numeric_features=numeric_features,
        text_vector=text_vec,
    )


if __name__ == "__main__":
    # Test sample email
    sample_email = {
        "email_id": "EMAIL-100",
        "subject": "URGENT: Your account requires immediate verification!",
        "body": "Dear customer, your banking profile has been suspended. Verify here: https://secure-login-example.com/verify",
        "sender_domain": "service-security.com",
        "return_path_domain": "attacker-relay.net",
        "dkim_domain": "attacker-relay.net",
        "origin_ip": "192.168.1.10",
        "asn": "AS12345",
        "urls": [
            "https://secure-login-example.com/verify"
        ]
    }

    fp = extract_email_fingerprint(sample_email)
    print("=" * 70)
    print("EMAIL FINGERPRINT EXTRACTION TEST")
    print("=" * 70)
    print(f"Email ID:               {fp.email_id}")
    print(f"Dense Vector Dimension: {len(fp.dense_vector)} floats (14 numeric + 64 text)")
    print("\nInfrastructure Signals:")
    for k, v in fp.raw_infra.items():
        print(f"  {k:<22}: {v}")
    print("\nExtracted Numeric Features:")
    for k, v in fp.numeric_features.items():
        print(f"  {k:<28}: {v:.4f}")
    print(f"\nText Vector L2 Norm:    {np.linalg.norm(fp.text_vector):.4f}")
    print("=" * 70)