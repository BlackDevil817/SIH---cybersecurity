"""
URL Reputation Classifier — Production Inference Module.

Provides explainable, structured risk predictions for URLs detected in email bodies.
Uses the retrained Random Forest model with normalized, domain-invariant features.
"""
import os
import sys
from typing import Dict, Any, List
import joblib
import pandas as pd

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", ".."))
from src.features.url_features import extract_url_features, FEATURE_ORDER, SUSPICIOUS_TLDS


class URLReputationClassifier:
    """Production URL reputation classifier with interpretable signal attribution."""

    def __init__(self, model_dir: str = None):
        if model_dir is None:
            model_dir = os.path.dirname(os.path.abspath(__file__))
        
        self.model_path = os.path.join(model_dir, "url_reputation_model.pkl")
        self.schema_path = os.path.join(model_dir, "feature_schema.pkl")
        
        if not os.path.exists(self.model_path):
            raise FileNotFoundError(f"Trained model not found at: {self.model_path}")
        
        self.model = joblib.load(self.model_path)
        self.feature_schema = joblib.load(self.schema_path) if os.path.exists(self.schema_path) else FEATURE_ORDER

    def _explain_signals(self, f: Dict[str, float]) -> List[str]:
        """Generate human-readable forensic indicators for why a URL received its risk score."""
        signals = []
        if f.get("uses_https") == 0:
            signals.append("Insecure HTTP protocol")
        if f.get("has_ip_address") == 1:
            signals.append("Raw IP address used as hostname")
        if f.get("has_login_keyword") == 1:
            signals.append("Contains credential-harvesting keyword (e.g. login/verify/account)")
        if f.get("has_suspicious_tld") == 1:
            signals.append("Registered under frequently abused/cheap TLD")
        if f.get("is_shortened_url") == 1:
            signals.append("URL shortener obscures destination")
        if f.get("has_at_symbol") == 1:
            signals.append("Contains @ symbol (URL obfuscation trick)")
        if f.get("has_double_slash") == 1:
            signals.append("Double slash in path (redirect trick)")
        if f.get("digit_ratio", 0.0) > 0.15:
            signals.append(f"Elevated numeric character ratio ({f['digit_ratio']:.1%})")
        if f.get("domain_entropy", 0.0) > 3.5:
            signals.append(f"High domain entropy ({f['domain_entropy']:.2f} bits)")
        if f.get("num_subdomains", 0) >= 2:
            signals.append(f"Multiple subdomains ({int(f['num_subdomains'])})")
        if f.get("hostname_hyphen_count", 0) >= 2:
            signals.append("Multiple hyphens in hostname (lookalike/typosquatting indicator)")
        
        if not signals:
            signals.append("Standard clean URL structure")
        return signals

    def predict_url(self, url: str, threshold: float = 0.50) -> Dict[str, Any]:
        """
        Analyze a URL and return a structured threat assessment.
        
        Returns:
            dict containing:
                - url: Original URL evaluated
                - prediction: "phishing" or "legitimate"
                - risk_score: Model probability estimate (0.0 to 1.0)
                - model_probability: Model score estimate (0.0 to 1.0)
                - top_signals: List of explainable forensic indicators
        """
        features = extract_url_features(url)
        X = pd.DataFrame([features], columns=self.feature_schema)

        # Probabilities: [prob_legit, prob_phishing]
        probabilities = self.model.predict_proba(X)[0]
        phish_prob = float(probabilities[1])

        prediction = "phishing" if phish_prob >= threshold else "legitimate"
        signals = self._explain_signals(features)

        return {
            "url": url,
            "prediction": prediction,
            "risk_score": round(phish_prob, 4),
            "model_probability": round(phish_prob, 4),
            "top_signals": signals,
        }


# Convenience singleton for simple imports
_default_classifier = None

def predict_url(url: str, threshold: float = 0.50) -> Dict[str, Any]:
    global _default_classifier
    if _default_classifier is None:
        _default_classifier = URLReputationClassifier()
    return _default_classifier.predict_url(url, threshold)


if __name__ == "__main__":
    clf = URLReputationClassifier()
    test_urls = [
        "https://google.com",
        "https://www.google.com",
        "https://openai.com",
        "https://rust-lang.org",
        "https://example.com/login",
        "http://192.168.1.10/verify/account",
        "https://secure-login-example.com/verify",
        "https://tommyhilfigersale.top",
    ]

    print("=" * 75)
    print("URL REPUTATION FORENSIC ASSESSMENT")
    print("=" * 75)
    for u in test_urls:
        res = clf.predict_url(u)
        print(f"\nURL:         {res['url']}")
        print(f"Verdict:     {res['prediction'].upper()} (Risk: {res['risk_score']:.2%})")
        print(f"Top Signals: {', '.join(res['top_signals'])}")
    print("\n" + "=" * 75)