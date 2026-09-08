"""
Step 1 audit script — inspect the real dataset and existing project files.
Read-only: does not modify anything.
"""
import os
import pandas as pd

DATASET_PATH = os.path.join("training", "dataset", "data_bal - 20000.xlsx")

def main():
    print("=" * 60)
    print("1. FILE STRUCTURE CHECK")
    print("=" * 60)
    for path in [
        "src/models/url_reputation.py",
        "src/models/url_reputation_model.pkl",
        "training/prepare_url_dataset.py",
        DATASET_PATH,
        "requirements.txt",
    ]:
        print(f"{'FOUND    ' if os.path.exists(path) else 'MISSING  '} {path}")

    print("\n" + "=" * 60)
    print("2. DATASET LOAD + SHAPE")
    print("=" * 60)
    df = pd.read_excel(DATASET_PATH)
    print("Shape:", df.shape)
    print("Columns:", list(df.columns))

    print("\n" + "=" * 60)
    print("3. LABEL DISTRIBUTION")
    print("=" * 60)
    print(df["Labels"].value_counts())
    print(df["Labels"].value_counts(normalize=True))

    print("\n" + "=" * 60)
    print("4. MISSING VALUES")
    print("=" * 60)
    print(df.isnull().sum())

    print("\n" + "=" * 60)
    print("5. DUPLICATES")
    print("=" * 60)
    print("Exact duplicate rows:", df.duplicated().sum())
    print("Duplicate URLs (ignoring label):", df["URLs"].duplicated().sum())

    print("\n" + "=" * 60)
    print("6. URL LENGTH STATS BY LABEL")
    print("=" * 60)
    df["url_len"] = df["URLs"].astype(str).str.len()
    print(df.groupby("Labels")["url_len"].describe())

    print("\n" + "=" * 60)
    print("7. DOT COUNT BY LABEL (proxy for subdomain complexity)")
    print("=" * 60)
    df["num_dots"] = df["URLs"].astype(str).str.count(r"\.")
    print(df.groupby("Labels")["num_dots"].describe())

    print("\n" + "=" * 60)
    print("8. 'SIMPLE URL' PATTERN CHECK (your key finding)")
    print("=" * 60)
    simple = df[
        (df["URLs"].astype(str).str.startswith("https://"))
        & (~df["URLs"].astype(str).str.contains(r"\d"))
        & (df["num_dots"] <= 1)
        & (~df["URLs"].astype(str).str.contains("/", regex=False).fillna(False)
           | (df["URLs"].astype(str).str.count("/") <= 2))
    ]
    print("Total 'simple' URLs matching pattern:", len(simple))
    print(simple["Labels"].value_counts())
    print("\nSample of these URLs:")
    print(simple[["URLs", "Labels"]].head(20).to_string(index=False))

    print("\n" + "=" * 60)
    print("9. SAMPLE LEGITIMATE URLS (random 15)")
    print("=" * 60)
    legit_sample = df[df["Labels"] == 0]["URLs"].sample(min(15, (df["Labels"]==0).sum()), random_state=42)
    for u in legit_sample:
        print(" ", u)

if __name__ == "__main__":
    main()