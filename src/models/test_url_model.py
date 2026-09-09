import sys
import os

sys.path.append(
    os.path.dirname(
        os.path.dirname(
            os.path.dirname(os.path.abspath(__file__))
        )
    )
)

import joblib
import pandas as pd

from src.features.url_features import extract_url_features, FEATURE_ORDER

# Load trained model
MODEL_PATH = "src/models/url_reputation_model.pkl"
model = joblib.load(MODEL_PATH)
print("Model loaded successfully!")


def predict_url(url: str):
    # Extract features using the shared 26-feature extractor
    features = extract_url_features(url)

    # Guarantee column order matches FEATURE_ORDER
    X = pd.DataFrame([features], columns=FEATURE_ORDER)

    # Prediction and probability
    prediction = int(model.predict(X)[0])
    probabilities = model.predict_proba(X)[0]
    phishing_probability = float(probabilities[1])

    label = "PHISHING" if prediction == 1 else "LEGITIMATE"
    return label, phishing_probability


if __name__ == "__main__":
    test_urls = [
        # Augmented legitimate domains (seen during training)
        "https://google.com",
        "https://www.google.com",
        # Unseen legitimate domains (never in training or augmented lists)
        "https://openai.com",
        "https://rust-lang.org",
        "https://news.ycombinator.com",
        # Obvious phishing/suspicious patterns
        "https://example.com/login",
        "http://192.168.1.10/verify/account",
        "https://secure-login-example.com/verify",
        "https://tommyhilfigersale.top",
    ]

    print("\n" + "=" * 65)
    print(f"{'URL':<42} | {'PREDICTION':<10} | {'RISK'}")
    print("=" * 65)
    for url in test_urls:
        label, prob = predict_url(url)
        print(f"{url:<42} | {label:<10} | {prob:.2%}")
    print("=" * 65)