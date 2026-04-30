"""
Run this ONCE to generate model.pkl, label_encoder.pkl, zone_encoder.pkl,
speed_encoder.pkl, and model_metadata.json.

Usage:
  cd python-model
  pip install scikit-learn numpy
  python train_model.py
"""

import json
import pickle
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import LabelEncoder

ZONE_TYPES  = ["hallway", "stairwell", "classroom", "lobby", "exit", "office", "open_area"]
SPEEDS      = ["slow", "normal", "fast"]

zone_enc  = LabelEncoder().fit(ZONE_TYPES)
speed_enc = LabelEncoder().fit(SPEEDS)

np.random.seed(42)
N = 10000
rows, labels = [], []

for _ in range(N):
    people   = np.random.randint(0, 150)
    capacity = np.random.randint(20, 200)
    density  = people / capacity
    zt       = np.random.choice(ZONE_TYPES)
    ms       = np.random.choice(SPEEDS)
    hour     = np.random.randint(0, 24)
    ef       = np.random.choice([0, 1], p=[0.3, 0.7])

    zt_enc_val = zone_enc.transform([zt])[0]
    ms_enc_val = speed_enc.transform([ms])[0]

    # Labels match spec thresholds (Section 13.3)
    if ef == 1:
        if density >= 0.90 or (zt in ["hallway", "lobby"] and density >= 0.70):
            label = "Critical"
        elif density >= 0.65 or zt in ["stairwell", "exit"]:
            label = "High"
        elif density >= 0.40:
            label = "Moderate"
        else:
            label = "Low"
    else:
        if density >= 0.85:
            label = "Critical"
        elif density >= 0.65:
            label = "High"
        elif density >= 0.40:
            label = "Moderate"
        else:
            label = "Low"

    rows.append([people, capacity, density, zt_enc_val, ms_enc_val, hour, ef])
    labels.append(label)

X = np.array(rows)
y = np.array(labels)

label_enc = LabelEncoder().fit(y)
y_enc     = label_enc.transform(y)

model = RandomForestClassifier(
    n_estimators=200,
    max_depth=15,
    class_weight="balanced",
    random_state=42,
    n_jobs=-1
)
model.fit(X, y_enc)

with open("model.pkl",         "wb") as f: pickle.dump(model,     f)
with open("label_encoder.pkl", "wb") as f: pickle.dump(label_enc, f)
with open("zone_encoder.pkl",  "wb") as f: pickle.dump(zone_enc,  f)
with open("speed_encoder.pkl", "wb") as f: pickle.dump(speed_enc, f)

meta = {
    "algorithm":    "RandomForestClassifier",
    "n_estimators": 200,
    "max_depth":    15,
    "features":     7,
    "feature_names": ["people_count", "max_capacity", "density_ratio",
                      "zone_type_enc", "movement_speed_enc", "hour", "emergency_flag"],
    "classes":      list(label_enc.classes_),
    "training_samples": N
}
with open("model_metadata.json", "w") as f:
    json.dump(meta, f, indent=2)

print("Model trained successfully.")
print("Classes:", label_enc.classes_)
