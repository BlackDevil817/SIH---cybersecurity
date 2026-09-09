"""
Step 5 — investigate why max_repeated_char_run dominates RF feature
importance (33%, ~3.5x the next feature). Check whether this is a
real phishing signal or a dataset-collection artifact, following the
same discipline we used to catch the google.com bug.
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
    feats["URLs"] = df["URLs"].values

    print("=" * 60)
    print("max_repeated_char_run distribution BY LABEL")
    print("=" * 60)
    print(feats.groupby("Labels")["max_repeated_char_run"].describe())

    print("\n" + "=" * 60)
    print("max_repeated_char_run BY SOURCE (original vs augmented)")
    print("=" * 60)
    print(feats.groupby("source")["max_repeated_char_run"].describe())

    print("\n" + "=" * 60)
    print("max_repeated_char_run BY SOURCE + LABEL")
    print("=" * 60)
    print(feats.groupby(["source", "Labels"])["max_repeated_char_run"].describe())

    print("\n" + "=" * 60)
    print("TOP 15 URLs with HIGHEST max_repeated_char_run")
    print("=" * 60)
    top = feats.sort_values("max_repeated_char_run", ascending=False).head(15)
    print(top[["URLs", "Labels", "source", "max_repeated_char_run"]].to_string(index=False))

    print("\n" + "=" * 60)
    print("Value counts of max_repeated_char_run, split by label")
    print("=" * 60)
    for label in [0, 1]:
        print(f"\nLabel={label}:")
        print(feats[feats["Labels"] == label]["max_repeated_char_run"].value_counts().sort_index())


if __name__ == "__main__":
    main()