"""
phishing_classifier.py — Dev A Component.

Trains and serves the LightGBM phishing email classifier.
"""
from __future__ import annotations
import os
import sys
from pathlib import Path
import joblib
import numpy as np
import pandas as pd
from lightgbm import LGBMClassifier

# Ensure repository root is on sys.path
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

from src.classification.feature_extraction import FEATURE_NAMES, extract_features, vectorize

DEFAULT_MODEL_PATH = Path("models/phishing_classifier.pkl")


class PhishingClassifier:
    """Inference-time classifier wrapper."""

    def __init__(self, model, feature_names: list[str]):
        self._model = model
        self._feature_names = feature_names

    @classmethod
    def load(cls, path: Path | str = DEFAULT_MODEL_PATH) -> "PhishingClassifier":
        path = Path(path)
        if not path.exists():
            raise FileNotFoundError(f"Model file not found at {path}. Train the model first.")
        bundle = joblib.load(path)
        return cls(bundle["model"], bundle["feature_names"])

    def predict(self, fingerprint_payload: dict) -> dict:
        feats = extract_features(fingerprint_payload)
        vec = vectorize(feats)
        X = pd.DataFrame([vec], columns=self._feature_names)

        proba = self._model.predict_proba(X)[0]
        phishing_proba = float(proba[1])
        label = "phishing" if phishing_proba >= 0.5 else "legitimate"
        if 0.35 <= phishing_proba < 0.65:
            label = "suspicious"

        confidence = phishing_proba if label != "legitimate" else 1.0 - phishing_proba

        return {
            "label": label,
            "confidence": round(confidence, 4),
            "phishing_probability": round(phishing_proba, 4),
            "feature_vector": feats,
            "raw_vector": vec,
        }


def train_bootstrap_model(model_out: Path = DEFAULT_MODEL_PATH):
    """Generates a realistic bootstrap dataset and trains the LightGBM classifier."""
    np.random.seed(42)
    N = 1000

    rows = []
    # Legitimate emails
    for _ in range(N // 2):
        rows.append({
            "spf_fail": 0.0, "spf_softfail": float(np.random.choice([0.0, 1.0], p=[0.95, 0.05])),
            "dkim_missing": float(np.random.choice([0.0, 1.0], p=[0.85, 0.15])), "dkim_fail": 0.0, "dmarc_fail": 0.0,
            "sender_replyto_mismatch": float(np.random.choice([0.0, 1.0], p=[0.98, 0.02])),
            "sender_returnpath_mismatch": float(np.random.choice([0.0, 1.0], p=[0.95, 0.05])),
            "display_name_spoof": 0.0, "dkim_domain_mismatch": 0.0,
            "urgency_keyword_count": float(np.random.poisson(0.2)),
            "credential_keyword_count": float(np.random.poisson(0.1)),
            "subject_length": float(np.random.randint(15, 60)),
            "body_length": float(np.random.randint(200, 2000)),
            "attachment_count": float(np.random.choice([0, 1], p=[0.8, 0.2])),
            "excessive_punctuation": 0.0,
            "num_urls": float(np.random.choice([0, 1, 2], p=[0.4, 0.4, 0.2])),
            "num_shortened_urls": 0.0,
            "num_distinct_url_domains": float(np.random.choice([0, 1], p=[0.5, 0.5])),
            "has_ip_literal_url": 0.0,
            "url_domain_sender_domain_mismatch": float(np.random.choice([0.0, 1.0], p=[0.9, 0.1])),
            "hop_count": float(np.random.randint(2, 6)),
            "sending_hour_utc": float(np.random.randint(8, 20)),
            "known_bad_asn": 0.0,
            "label": 0
        })

    # Phishing emails
    for _ in range(N // 2):
        rows.append({
            "spf_fail": float(np.random.choice([0.0, 1.0], p=[0.3, 0.7])),
            "spf_softfail": float(np.random.choice([0.0, 1.0], p=[0.8, 0.2])),
            "dkim_missing": float(np.random.choice([0.0, 1.0], p=[0.3, 0.7])),
            "dkim_fail": float(np.random.choice([0.0, 1.0], p=[0.6, 0.4])),
            "dmarc_fail": float(np.random.choice([0.0, 1.0], p=[0.3, 0.7])),
            "sender_replyto_mismatch": float(np.random.choice([0.0, 1.0], p=[0.4, 0.6])),
            "sender_returnpath_mismatch": float(np.random.choice([0.0, 1.0], p=[0.3, 0.7])),
            "display_name_spoof": float(np.random.choice([0.0, 1.0], p=[0.4, 0.6])),
            "dkim_domain_mismatch": float(np.random.choice([0.0, 1.0], p=[0.4, 0.6])),
            "urgency_keyword_count": float(np.random.randint(1, 6)),
            "credential_keyword_count": float(np.random.randint(1, 5)),
            "subject_length": float(np.random.randint(20, 80)),
            "body_length": float(np.random.randint(150, 1200)),
            "attachment_count": float(np.random.choice([0, 1], p=[0.9, 0.1])),
            "excessive_punctuation": float(np.random.choice([0.0, 1.0], p=[0.5, 0.5])),
            "num_urls": float(np.random.randint(1, 5)),
            "num_shortened_urls": float(np.random.choice([0, 1, 2], p=[0.5, 0.3, 0.2])),
            "num_distinct_url_domains": float(np.random.randint(1, 4)),
            "has_ip_literal_url": float(np.random.choice([0.0, 1.0], p=[0.8, 0.2])),
            "url_domain_sender_domain_mismatch": 1.0,
            "hop_count": float(np.random.randint(1, 8)),
            "sending_hour_utc": float(np.random.randint(0, 24)),
            "known_bad_asn": float(np.random.choice([0.0, 1.0], p=[0.8, 0.2])),
            "label": 1
        })

    df = pd.DataFrame(rows)
    X = df[FEATURE_NAMES]
    y = df["label"]

    model = LGBMClassifier(n_estimators=100, max_depth=6, learning_rate=0.05, random_state=42, verbose=-1)
    model.fit(X, y)

    model_out.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": model, "feature_names": FEATURE_NAMES}, model_out)
    print(f"Trained and saved LightGBM model to: {model_out}")


if __name__ == "__main__":
    train_bootstrap_model()

    # Verify loading and prediction
    clf = PhishingClassifier.load()
    sample = {
        "senderDomain": "paypa1-secure.com",
        "replyToDomain": "mail-reply.ru",
        "returnPathDomain": "bounce.paypa1-secure.com",
        "dkimDomain": "",
        "originatingIp": "185.220.101.7",
        "asn": "AS44066",
        "urlDomains": ["paypa1-login-verify.com", "bit.ly"],
        "features": {
            "spfResult": "fail", "dkimResult": "none", "dmarcResult": "fail",
            "senderReplyToMismatch": True, "displayNameSpoof": True,
            "numUrls": 3, "numShortenedUrls": 1, "hasIpLiteralUrl": False,
            "urgencyKeywordCount": 4, "credentialKeywordCount": 2,
            "subjectLength": 47, "bodyLength": 812, "hopCount": 5, "sendingHourUtc": 3
        }
    }
    pred = clf.predict(sample)
    print("\nPrediction on Spring Boot test sample:")
    print(f"  Label:       {pred['label']}")
    print(f"  Confidence:  {pred['confidence']:.2%}")
    print(f"  Probability: {pred['phishing_probability']:.2%}")