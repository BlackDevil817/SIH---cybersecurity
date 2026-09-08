import sys
import os

sys.path.append(
    os.path.dirname(
        os.path.dirname(
            os.path.abspath(__file__)
        )
    )
)

import pandas as pd
from src.models.url_reputation import extract_url_features


DATASET_PATH = "training/dataset/data_bal - 20000.xlsx"

df = pd.read_excel(DATASET_PATH)

features = df["URLs"].apply(extract_url_features)
feature_df = pd.DataFrame(features.tolist())

df = pd.concat([df, feature_df], axis=1)


print("\nLegitimate URLs with simple structure:")
print(
    df[
        (df["Labels"] == 0) &
        (df["num_digits"] == 0) &
        (df["num_subdomains"] == 0) &
        (df["num_dots"] <= 1) &
        (df["uses_https"] == 1) &
        (df["path_length"] == 0)
    ][["URLs", "Labels"]]
    .head(20)
)


print("\nPhishing URLs with simple structure:")
print(
    df[
        (df["Labels"] == 1) &
        (df["num_digits"] == 0) &
        (df["num_subdomains"] == 0) &
        (df["num_dots"] <= 1) &
        (df["uses_https"] == 1) &
        (df["path_length"] == 0)
    ][["URLs", "Labels"]]
    .head(20)
)