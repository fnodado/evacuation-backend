"""
Called by Spring Boot PredictionService via ProcessBuilder.
Usage:  python predict.py '<json_string>'
Prints: {"congestion_level": "High"}
"""

import sys
import json
import os
import pickle
import numpy as np


def load_artifacts():
    base = os.path.dirname(os.path.abspath(__file__))
    with open(os.path.join(base, "model.pkl"),         "rb") as f: model     = pickle.load(f)
    with open(os.path.join(base, "label_encoder.pkl"), "rb") as f: label_enc = pickle.load(f)
    with open(os.path.join(base, "zone_encoder.pkl"),  "rb") as f: zone_enc  = pickle.load(f)
    with open(os.path.join(base, "speed_encoder.pkl"), "rb") as f: speed_enc = pickle.load(f)
    return model, label_enc, zone_enc, speed_enc


def predict(input_json: str) -> dict:
    data = json.loads(input_json)

    people_count   = int(data.get("people_count", 0))
    max_capacity   = int(data.get("max_capacity", 100))
    zone_type      = data.get("zone_type", "hallway")
    movement_speed = data.get("movement_speed", "normal")
    time_of_day    = data.get("time_of_day", "12:00")
    emergency_flag = int(data.get("emergency_flag", 0))

    density_ratio = people_count / max_capacity if max_capacity > 0 else 0.0

    try:
        hour = int(str(time_of_day).split(":")[0])
    except Exception:
        hour = 12

    model, label_enc, zone_enc, speed_enc = load_artifacts()

    known_zones  = list(zone_enc.classes_)
    known_speeds = list(speed_enc.classes_)
    zt = zone_type      if zone_type      in known_zones  else "hallway"
    ms = movement_speed if movement_speed in known_speeds else "normal"

    zt_enc = zone_enc.transform([zt])[0]
    ms_enc = speed_enc.transform([ms])[0]

    X = np.array([[people_count, max_capacity, density_ratio, zt_enc, ms_enc, hour, emergency_flag]])
    pred  = model.predict(X)
    level = label_enc.inverse_transform(pred)[0]

    return {"congestion_level": level}


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No input JSON provided"}))
        sys.exit(1)

    try:
        result = predict(sys.argv[1])
        print(json.dumps(result))
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)
