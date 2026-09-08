import sys
import os
import joblib
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import pandas as pd

from src.models.url_reputation import extract_url_features
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix
)


# --------------------------------------------------
# 1. Load dataset
# --------------------------------------------------

DATASET_PATH = "training/dataset/data_bal - 20000.xlsx"

df = pd.read_excel(DATASET_PATH)

print("Dataset loaded!")
print("Dataset shape:", df.shape)


# --------------------------------------------------
# 2. Convert URLs into numerical features
# --------------------------------------------------

features = df["URLs"].apply(extract_url_features)

X = pd.DataFrame(features.tolist())

y = df["Labels"]


print("\nFeature matrix:", X.shape)
print("Labels:", y.shape)


# --------------------------------------------------
# 3. Train/test split
# --------------------------------------------------

X_train, X_test, y_train, y_test = train_test_split(
    X,
    y,
    test_size=0.2,
    random_state=42,
    stratify=y
)


# --------------------------------------------------
# 4. Create Random Forest model
# --------------------------------------------------

model = RandomForestClassifier(
    n_estimators=200,
    random_state=42,
    n_jobs=-1
)


# --------------------------------------------------
# 5. Train
# --------------------------------------------------

print("\nTraining Random Forest...")

model.fit(X_train, y_train)

MODEL_PATH = "src/models/url_reputation_model.pkl"

joblib.dump(model, MODEL_PATH)

print(f"Model saved to: {MODEL_PATH}")
print("Training complete!")


# --------------------------------------------------
# 6. Make predictions
# --------------------------------------------------

y_pred = model.predict(X_test)


# --------------------------------------------------
# 7. Evaluate
# --------------------------------------------------

accuracy = accuracy_score(y_test, y_pred)

print("\nAccuracy:", accuracy)

print("\nClassification Report:")
print(classification_report(
    y_test,
    y_pred,
    target_names=["Legitimate", "Phishing"]
))

print("\nConfusion Matrix:")
print(confusion_matrix(y_test, y_pred))


# --------------------------------------------------
# 8. Feature importance
# --------------------------------------------------

print("\nFeature Importance:")

importance = pd.Series(
    model.feature_importances_,
    index=X.columns
).sort_values(ascending=False)

print(importance)
print("\nGoogle features:")
print(extract_url_features("https://google.com"))

print("\nGoogle model probability:")
google_features = extract_url_features("https://google.com")
google_df = pd.DataFrame([google_features])
print(model.predict_proba(google_df))