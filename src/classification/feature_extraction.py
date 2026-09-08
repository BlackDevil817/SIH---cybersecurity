"""
feature_extraction.py — Dev A Component.

Converts the structured fingerprint payload sent by the Spring Boot backend
(POST /api/ml/cluster) into a fixed-order numeric feature vector.
"""
from __future__ import annotations
from dataclasses import dataclass
from typing import Any
import math

# Canonical, ordered feature list (23 features).
FEATURE_NAMES: list[str] = [
    # --- auth-related ---
    "spf_fail",
    "spf_softfail",
    "dkim_missing",
    "dkim_fail",
    "dmarc_fail",
    "sender_replyto_mismatch",
    "sender_returnpath_mismatch",
    "display_name_spoof",
    "dkim_domain_mismatch",
    # --- content-related ---
    "urgency_keyword_count",
    "credential_keyword_count",
    "subject_length",
    "body_length",
    "attachment_count",
    "excessive_punctuation",
    # --- url-related ---
    "num_urls",
    "num_shortened_urls",
    "num_distinct_url_domains",
    "has_ip_literal_url",
    "url_domain_sender_domain_mismatch",
    # --- infra / routing ---
    "hop_count",
    "sending_hour_utc",
    "known_bad_asn",
]


@dataclass
class FeatureExtractionError(Exception):
    detail: str
    field_name: str | None = None

    def __str__(self) -> str:
        return f"{self.detail}" + (f" (field={self.field_name})" if self.field_name else "")


DEFAULT_KNOWN_BAD_ASNS: set[str] = set()


def _safe_get(d: dict, key: str, default=0):
    val = d.get(key, default)
    return default if val is None else val


def extract_features(payload: dict[str, Any], known_bad_asns: set[str] | None = None) -> dict[str, float]:
    """
    Takes the raw request body and returns a dict of feature_name -> numeric value.
    """
    if "features" not in payload or not isinstance(payload["features"], dict):
        raise FeatureExtractionError("Missing or invalid 'features' object", "features")

    f = payload["features"]
    known_bad_asns = known_bad_asns or DEFAULT_KNOWN_BAD_ASNS

    spf = str(_safe_get(f, "spfResult", "none")).lower()
    dkim = str(_safe_get(f, "dkimResult", "none")).lower()
    dmarc = str(_safe_get(f, "dmarcResult", "none")).lower()

    sender_domain = (payload.get("senderDomain") or "").lower()
    reply_to_domain = (payload.get("replyToDomain") or "").lower()
    return_path_domain = (payload.get("returnPathDomain") or "").lower()
    dkim_domain = (payload.get("dkimDomain") or "").lower()
    url_domains = [d.lower() for d in payload.get("urlDomains", []) if d]
    asn = str(payload.get("asn") or "")

    subject_len = int(_safe_get(f, "subjectLength", 0))
    body_len = int(_safe_get(f, "bodyLength", 0))

    vector = {
        "spf_fail": 1.0 if spf == "fail" else 0.0,
        "spf_softfail": 1.0 if spf == "softfail" else 0.0,
        "dkim_missing": 1.0 if dkim in ("none", "") else 0.0,
        "dkim_fail": 1.0 if dkim == "fail" else 0.0,
        "dmarc_fail": 1.0 if dmarc == "fail" else 0.0,
        "sender_replyto_mismatch": 1.0 if (reply_to_domain and reply_to_domain != sender_domain) else 0.0,
        "sender_returnpath_mismatch": 1.0 if (return_path_domain and return_path_domain != sender_domain) else 0.0,
        "display_name_spoof": 1.0 if bool(_safe_get(f, "displayNameSpoof", False)) else 0.0,
        "dkim_domain_mismatch": 1.0 if (dkim_domain and dkim_domain != sender_domain) else 0.0,

        "urgency_keyword_count": float(_safe_get(f, "urgencyKeywordCount", 0)),
        "credential_keyword_count": float(_safe_get(f, "credentialKeywordCount", 0)),
        "subject_length": float(subject_len),
        "body_length": float(body_len),
        "attachment_count": float(_safe_get(f, "attachmentCount", 0)),
        "excessive_punctuation": 1.0 if float(_safe_get(f, "urgencyKeywordCount", 0)) >= 3 else 0.0,

        "num_urls": float(_safe_get(f, "numUrls", len(url_domains))),
        "num_shortened_urls": float(_safe_get(f, "numShortenedUrls", 0)),
        "num_distinct_url_domains": float(len(set(url_domains))),
        "has_ip_literal_url": 1.0 if bool(_safe_get(f, "hasIpLiteralUrl", False)) else 0.0,
        "url_domain_sender_domain_mismatch": 1.0 if (
            url_domains and sender_domain and all(sender_domain not in d for d in url_domains)
        ) else 0.0,

        "hop_count": float(_safe_get(f, "hopCount", 0)),
        "sending_hour_utc": float(_safe_get(f, "sendingHourUtc", 12)),
        "known_bad_asn": 1.0 if asn in known_bad_asns else 0.0,
    }

    for k, v in vector.items():
        if not math.isfinite(v):
            raise FeatureExtractionError(f"Non-finite value computed for feature '{k}'", k)

    return vector


def vectorize(feature_dict: dict[str, float]) -> list[float]:
    """Convert a feature dict into the fixed-order vector the model expects."""
    return [feature_dict[name] for name in FEATURE_NAMES]


if __name__ == "__main__":
    # Test with Spring Boot contract sample
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

    feats = extract_features(sample_request)
    vec = vectorize(feats)
    print("=" * 65)
    print("DEV A FEATURE EXTRACTION TEST")
    print("=" * 65)
    print(f"Total features extracted: {len(vec)} / 23")
    print(f"SPF Fail:                 {feats['spf_fail']}")
    print(f"DMARC Fail:               {feats['dmarc_fail']}")
    print(f"Sender-ReplyTo Mismatch:  {feats['sender_replyto_mismatch']}")
    print(f"Urgency Keyword Count:    {feats['urgency_keyword_count']}")
    print(f"Shortened URLs:           {feats['num_shortened_urls']}")
    print("=" * 65)