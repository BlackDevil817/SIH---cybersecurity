"""
Diagnostic script to compare URL model generalization under:
1. Random row-level split (with domain leakage)
2. Domain-grouped split (unseen domains)
3. Domain-grouped split with URL normalization (stripping www artifact)
"""
import os
import sys
import re
from urllib.parse import urlparse
import pandas as pd
from sklearn.model_selection import train_test_split, GroupShuffleSplit
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, confusion_matrix

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from src.features.url_features import extract_url_features, FEATURE_ORDER

DATA_PATH = os.path.join("training", "dataset", "data_augmented.csv")


def get_hostname(u: str) -> str:
    target = u if "://" in str(u) else f"http://{u}"
    parsed = urlparse(target)
    return (parsed.hostname or "").lower()


def get_clean_domain(u: str) -> str:
    host = get_hostname(u)
    return re.sub(r"^www\.", "", host)


def normalize_url(u: str) -> str:
    """Strip leading www. from hostname while preserving scheme, path, query."""
    target = u if "://" in str(u) else f"http://{u}"
    parsed = urlparse(target)
    clean_host = re.sub(r"^www\.", "", (parsed.hostname or "").lower())
    netloc = clean_host if not parsed.port else f"{clean_host}:{parsed.port}"
    rebuilt = f"{parsed.scheme}://{netloc}{parsed.path}"
    if parsed.query:
        rebuilt += f"?{parsed.query}"
    return rebuilt


def run_experiment(name, X_train, X_test, y_train, y_test, feature_names):
    rf = RandomForestClassifier(n_estimators=100, random_state=42, n_jobs=-1)
    rf.fit(X_train, y_train)

    preds = rf.predict(X_test)
    acc = accuracy_score(y_test, preds)
    prec = precision_score(y_test, preds)
    rec = recall_score(y_test, preds)
    f1 = f1_score(y_test, preds)
    cm = confusion_matrix(y_test, preds)

    print(f"\n{'=' * 65}")
    print(f"EXPERIMENT: {name}")
    print(f"{'=' * 65}")
    print(f"Accuracy:  {acc:.4f}")
    print(f"Precision: {prec:.4f}")
    print(f"Recall:    {rec:.4f}  (phishing caught)")
    print(f"F1-Score:  {f1:.4f}")
    print(f"Confusion Matrix: [[TN={cm[0][0]}, FP={cm[0][1]}], [FN={cm[1][0]}, TP={cm[1][1]}]]")

    top_feat = pd.Series(rf.feature_importances_, index=feature_names).sort_values(ascending=False).head(5)
    print("\nTop 5 Feature Importances:")
    for feat, val in top_feat.items():
        print(f"   {feat:<26} {val:.4f} ({val*100:.1f}%)")

    return rf


def main():
    print("Loading dataset...")
    df = pd.read_csv(DATA_PATH)
    y = df["Labels"].values

    df["raw_host"] = df["URLs"].apply(get_hostname)
    df["clean_host"] = df["URLs"].apply(get_clean_domain)

    # 1. Standard features (raw URLs)
    print("Extracting baseline features (takes ~5 seconds)...")
    X_raw = pd.DataFrame([extract_url_features(u) for u in df["URLs"]], columns=FEATURE_ORDER)

    # Experiment 1: Random Split (Current Baseline with domain leakage)
    X_tr1, X_te1, y_tr1, y_te1 = train_test_split(
        X_raw, y, test_size=0.2, random_state=42, stratify=y
    )
    rf1 = run_experiment("1. Random Split (Baseline - Leaky)", X_tr1, X_te1, y_tr1, y_te1, FEATURE_ORDER)

    # Experiment 2: Domain-Grouped Split (Unseen Domains, with www artifact)
    gss = GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=42)
    tr_idx2, te_idx2 = next(gss.split(X_raw, y, groups=df["raw_host"]))
    X_tr2, X_te2 = X_raw.iloc[tr_idx2], X_raw.iloc[te_idx2]
    y_tr2, y_te2 = y[tr_idx2], y[te_idx2]
    rf2 = run_experiment("2. Domain-Grouped Split (Unseen Domains, with www artifact)", X_tr2, X_te2, y_tr2, y_te2, FEATURE_ORDER)

    # Experiment 3: Domain-Grouped Split WITH www-normalization
    print("\nExtracting normalized features (www stripped from hostname)...")
    df["norm_url"] = df["URLs"].apply(normalize_url)
    X_norm = pd.DataFrame([extract_url_features(u) for u in df["norm_url"]], columns=FEATURE_ORDER)

    tr_idx3, te_idx3 = next(gss.split(X_norm, y, groups=df["clean_host"]))
    X_tr3, X_te3 = X_norm.iloc[tr_idx3], X_norm.iloc[te_idx3]
    y_tr3, y_te3 = y[tr_idx3], y[te_idx3]
    rf3 = run_experiment("3. Domain-Grouped Split (Unseen Domains + www-Normalized)", X_tr3, X_te3, y_tr3, y_te3, FEATURE_ORDER)

    # Sanity checks across models
    sanity_urls = [
        "https://google.com",
        "https://www.google.com",
        "https://rust-lang.org",
        "https://news.ycombinator.com",
        "https://example.com/login",
        "http://192.168.1.10/verify/account",
        "https://secure-login-example.com/verify",
    ]

    print("\n" + "=" * 65)
    print("SANITY CHECK PREDICTIONS ACROSS EXPERIMENTS (Phishing Probability)")
    print("=" * 65)
    print(f"{'URL':<40} | {'Exp 1 (Leaky)':<13} | {'Exp 2 (Grouped)':<15} | {'Exp 3 (Normalized)'}")
    print("-" * 85)

    for u in sanity_urls:
        f_raw = pd.DataFrame([extract_url_features(u)], columns=FEATURE_ORDER)
        f_norm = pd.DataFrame([extract_url_features(normalize_url(u))], columns=FEATURE_ORDER)

        p1 = rf1.predict_proba(f_raw)[0, 1]
        p2 = rf2.predict_proba(f_raw)[0, 1]
        p3 = rf3.predict_proba(f_norm)[0, 1]

        print(f"{u:<40} | {p1:>11.2%}   | {p2:>13.2%}   | {p3:>16.2%}")
    print("=" * 65)


if __name__ == "__main__":
    main()