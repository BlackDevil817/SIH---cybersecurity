import pandas as pd

DATASET_PATH = "training/dataset/data_bal - 20000.xlsx"

df = pd.read_excel(DATASET_PATH)

print("LEGITIMATE URL EXAMPLES:")
print(df[df["Labels"] == 0]["URLs"].head(10).to_string(index=False))

print("\nPHISHING URL EXAMPLES:")
print(df[df["Labels"] == 1]["URLs"].head(10).to_string(index=False))