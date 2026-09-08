"""
score_breakdown.py — Dev A Component.

Restructures the phishing classifier output into auth/content/URL sub-scores
and per-sub-score SHAP explanations.
"""
from __future__ import annotations
import os
import sys
from dataclasses import dataclass, asdict
from pathlib import Path
import numpy as np
import pandas as pd
import shap

# Ensure repository root is on sys.path
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

from src.classification.feature_extraction import FEATURE_NAMES

FEATURE_BUCKETS: dict[str, str] = {
    "spf_fail": "auth",
    "spf_softfail": "auth",
    "dkim_missing": "auth",
    "dkim_fail": "auth",
    "dmarc_fail": "auth",
    "sender_replyto_mismatch": "auth",
    "sender_returnpath_mismatch": "auth",
    "display_name_spoof": "auth",
    "dkim_domain_mismatch": "auth",

    "urgency_keyword_count": "content",
    "credential_keyword_count": "content",
    "subject_length": "content",
    "body_length": "content",
    "attachment_count": "content",
    "excessive_punctuation": "content",

    "num_urls": "url",
    "num_shortened_urls": "url",
    "num_distinct_url_domains": "url",
    "has_ip_literal_url": "url",
    "url_domain_sender_domain_mismatch": "url",

    "hop_count": "url",
    "sending_hour_utc": "content",
    "known_bad_asn": "url",
}

BUCKETS = ("auth", "content", "url")


@dataclass
class FeatureImpact:
    feature: str
    impact: float

    def to_dict(self) -> dict:
        return asdict(self)


class ScoreBreakdownEngine:
    """Computes SHAP sub-score explanations matching the Spring Boot API contract."""

    def __init__(self, model, background_data: pd.DataFrame | None = None):
        self._model = model
        self._explainer = shap.TreeExplainer(model, data=background_data)

    @classmethod
    def from_classifier_bundle(cls, model_path: Path | str, background_csv: Path | str | None = None):
        import joblib
        bundle = joblib.load(model_path)
        background = pd.read_csv(background_csv)[FEATURE_NAMES] if background_csv else None
        return cls(bundle["model"], background)

    def explain(self, feature_vector: dict[str, float], overall_score: float, top_k: int = 3) -> dict:
        X = pd.DataFrame([[feature_vector[name] for name in FEATURE_NAMES]], columns=FEATURE_NAMES)
        shap_values = self._explainer.shap_values(X)

        if isinstance(shap_values, list):
            values = np.array(shap_values[1][0]) if len(shap_values) > 1 else np.array(shap_values[0][0])
        elif isinstance(shap_values, np.ndarray) and shap_values.ndim == 2:
            values = np.array(shap_values[0])
        else:
            values = np.array(shap_values)

        contributions = dict(zip(FEATURE_NAMES, values))
        bucket_raw: dict[str, float] = {b: 0.0 for b in BUCKETS}
        bucket_features: dict[str, list[FeatureImpact]] = {b: [] for b in BUCKETS}

        for name, shap_val in contributions.items():
            bucket = FEATURE_BUCKETS[name]
            magnitude = abs(float(shap_val))
            bucket_raw[bucket] += magnitude
            bucket_features[bucket].append(FeatureImpact(feature=name, impact=magnitude))

        def squash(x: float, scale: float = 2.0) -> float:
            return float(1 - np.exp(-scale * x))

        sub_scores = {b: round(squash(bucket_raw[b]), 4) for b in BUCKETS}

        explanations = {}
        for b in BUCKETS:
            ranked = sorted(bucket_features[b], key=lambda fi: fi.impact, reverse=True)[:top_k]
            total = sum(fi.impact for fi in bucket_features[b]) or 1e-9
            explanations[f"{b}Score"] = [
                {"feature": fi.feature, "impact": round(fi.impact / total, 4)} for fi in ranked
            ]

        return {
            "authScore": sub_scores["auth"],
            "contentScore": sub_scores["content"],
            "urlScore": sub_scores["url"],
            "overallScore": round(float(overall_score), 4),
            "explanations": explanations,
        }


def build_risk_breakdown(classifier_result: dict, engine: ScoreBreakdownEngine) -> dict:
    return engine.explain(
        feature_vector=classifier_result["feature_vector"],
        overall_score=classifier_result["phishing_probability"],
    )