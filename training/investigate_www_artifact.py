"""
Step 6 - check whether the www-prefix artifact found in
max_repeated_char_run also leaks into other features (domain_length,
num_dots, num_subdomains, domain_entropy), since all of these are
computed from a hostname that systematically differs between classes
for a crawl-methodology reason, not a phishing-behavior reason.
"""
import os
import sys
import pandas as pd

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from src.features.url_features import extract_url_features

DATA_PATH = os.path.join("training", "dataset", "data_augmented.csv")


def main():
    df = pd.read_csv(DATA_PATH)
    feats = pd.DataFrame([extract_url_features(u) for u in df["URLs"]])
    feats["Labels"] = df["Labels"].values
    feats["source"] = df["source"].values
    feats["has_www"] = df["URLs"].astype(str).str.contains(r"://www\.", regex=True).astype(int)

    print("=" * 60)
    print("www-prefix rate BY LABEL (original rows only)")
    print("=" * 60)
    orig = feats[feats["source"] == "original"]
    print(orig.groupby("Labels")["has_www"].mean())
    print(orig.groupby("Labels")["has_www"].value_counts())

    print("\n" + "=" * 60)
    print("Feature means, ORIGINAL rows, split by has_www (ignoring label)")
    print("=" * 60)
    cols = ["max_repeated_char_run", "domain_length", "num_dots",
            "num_subdomains", "domain_entropy", "hostname_hyphen_count"]
    print(orig.groupby("has_www")[cols].mean())

    print("\n" + "=" * 60)
    print("Same features, but WITHIN Label=0 only (legit), split by has_www")
    print("=" * 60)
    legit_orig = orig[orig["Labels"] == 0]
    print(legit_orig.groupby("has_www")[cols].mean())

    print("\n" + "=" * 60)
    print("Same features, but WITHIN Label=1 only (phishing), split by has_www")
    print("=" * 60)
    phish_orig = orig[orig["Labels"] == 1]
    print(phish_orig.groupby("has_www")[cols].mean())


if __name__ == "__main__":
    main()