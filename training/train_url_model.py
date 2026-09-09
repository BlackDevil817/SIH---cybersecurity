"""
Train and evaluate RandomForest vs LogisticRegression on the augmented
dataset using the shared, normalized feature extractor and domain-grouped
splitting (zero domain leakage between train and test).

Saves:
  - src/models/url_reputation_model.pkl   (best-performing model)
  - src/models/feature_schema.pkl         (exact ordered feature list)
  - src/models/model_metadata.pkl         (honest domain-grouped metrics & metadata)
"""
import os
import sys
import re
from datetime import datetime
from urllib.parse import urlparse

import pandas as pd
import numpy as np
import joblib
from sklearn.model_selection import GroupShuffleSplit
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import (
    accuracy_score, precision_score, recall_score, f1_score,
    roc_auc_score, confusion_matrix, classification_report,
)

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from src.features.url_features import extract_url_features, FEATURE_ORDER

DATA_PATH = os.path.join("training", "dataset", "data_augmented.csv")
MODEL_DIR = os.path.join("src", "models")
RANDOM_STATE = 42


def get_clean_host(u: str) -> str:
    """Extract lowercase hostname with www. stripped for domain grouping."""
    target = u if "://" in str(u) else f"http://{u}"
    parsed = urlparse(target)
    return re.sub(r"^www\.", "", (parsed.hostname or "").lower())


def build_feature_matrix(df: pd.DataFrame) -> pd.DataFrame:
    """Apply the normalized shared extractor to every URL."""
    rows = [extract_url_features(u) for u in df["URLs"]]
    return pd.DataFrame(rows, columns=FEATURE_ORDER)


def evaluate(name, model, X_test, y_test, scaled=False, scaler=None):
    """Print an honest domain-grouped metrics report."""
    X_eval = scaler.transform(X_test) if scaled else X_test
    preds = model.predict(X_eval)
    probs = model.predict_proba(X_eval)[:, 1]

    acc = accuracy_score(y_test, preds)
    prec = precision_score(y_test, preds)
    rec = recall_score(y_test, preds)
    f1 = f1_score(y_test, preds)
    auc = roc_auc_score(y_test, probs)
    cm = confusion_matrix(y_test, preds)

    print(f"\n{'=' * 60}\n{name} (Evaluated on Unseen Domains)\n{'=' * 60}")
    print(f"Accuracy:  {acc:.4f}")
    print(f"Precision: {prec:.4f}  (of predicted phishing, how many truly are)")
    print(f"Recall:    {rec:.4f}  (of real phishing, how many we caught)")
    print(f"F1:        {f1:.4f}")
    print(f"ROC-AUC:   {auc:.4f}")
    print("Confusion matrix [[TN, FP], [FN, TP]]:")
    print(cm)
    print(f"False negatives (missed phishing): {cm[1][0]}  |  False positives (false alarms): {cm[0][1]}")

    return {
        "accuracy": float(acc),
        "precision": float(prec),
        "recall": float(rec),
        "f1": float(f1),
        "roc_auc": float(auc),
        "confusion_matrix": cm.tolist(),
    }


def main():
    print("Loading dataset...")
    df = pd.read_csv(DATA_PATH)
    print("Dataset shape:", df.shape)
    print("Label distribution:\n", df["Labels"].value_counts())

    print("\nExtracting normalized features (takes ~5 seconds)...")
    X = build_feature_matrix(df)
    y = df["Labels"].values
    df["clean_host"] = df["URLs"].apply(get_clean_host)

    # Domain-Grouped Split: Zero hostnames overlap between train and test
    gss = GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=RANDOM_STATE)
    train_idx, test_idx = next(gss.split(X, y, groups=df["clean_host"]))

    X_train, X_test = X.iloc[train_idx], X.iloc[test_idx]
    y_train, y_test = y[train_idx], y[test_idx]

    print(f"\nTrain samples: {len(train_idx)} across {df.iloc[train_idx]['clean_host'].nunique()} domains")
    print(f"Test samples:  {len(test_idx)} across {df.iloc[test_idx]['clean_host'].nunique()} domains")

    # ---- Model 1: Random Forest ----
    rf = RandomForestClassifier(
        n_estimators=300,
        max_depth=None,
        class_weight="balanced",
        random_state=RANDOM_STATE,
        n_jobs=-1,
    )
    rf.fit(X_train, y_train)
    rf_metrics = evaluate("RandomForest", rf, X_test, y_test)

    print("\nTop 10 feature importances (RandomForest):")
    importances = pd.Series(rf.feature_importances_, index=FEATURE_ORDER)
    print(importances.sort_values(ascending=False).head(10).to_string())

    # ---- Model 2: Logistic Regression Baseline ----
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)

    lr = LogisticRegression(
        max_iter=2000, class_weight="balanced", random_state=RANDOM_STATE
    )
    lr.fit(X_train_scaled, y_train)
    lr_metrics = evaluate("LogisticRegression", lr, X_test, y_test, scaled=True, scaler=scaler)

    # ---- Pick winner by Recall, then F1 ----
    if rf_metrics["recall"] >= lr_metrics["recall"]:
        winner_name, winner_model = "RandomForest", rf
    else:
        winner_name, winner_model = "LogisticRegression", lr
    print(f"\n>>> Selected model for deployment: {winner_name}")

    # ---- Save model + feature schema + honest metadata ----
    os.makedirs(MODEL_DIR, exist_ok=True)
    model_path = os.path.join(MODEL_DIR, "url_reputation_model.pkl")
    schema_path = os.path.join(MODEL_DIR, "feature_schema.pkl")
    meta_path = os.path.join(MODEL_DIR, "model_metadata.pkl")

    joblib.dump(winner_model, model_path)
    joblib.dump(FEATURE_ORDER, schema_path)

    metadata = {
        "model_type": winner_name,
        "trained_at": datetime.now().isoformat(),
        "dataset_path": DATA_PATH,
        "dataset_shape": list(df.shape),
        "split_strategy": "Domain-Grouped (GroupShuffleSplit 80/20, zero domain leakage)",
        "feature_count": len(FEATURE_ORDER),
        "normalization": "Hostname www-stripped, lowercase, path normalized",
        "metrics": rf_metrics if winner_name == "RandomForest" else lr_metrics,
        "requires_scaling": winner_name == "LogisticRegression",
    }
    if winner_name == "LogisticRegression":
        joblib.dump(scaler, os.path.join(MODEL_DIR, "feature_scaler.pkl"))
        metadata["scaler_path"] = os.path.join(MODEL_DIR, "feature_scaler.pkl")

    joblib.dump(metadata, meta_path)

    print(f"\nSaved model to:    {model_path}")
    print(f"Saved schema to:   {schema_path}")
    print(f"Saved metadata to: {meta_path}")


if __name__ == "__main__":
    main()