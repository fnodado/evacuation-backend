import sys
import json
import pickle
import numpy as np
import re

ZONE_TYPES = ["hallway", "stairwell", "classroom", "lobby", "exit", "office", "open_area"]
MOVEMENT_SPEEDS = ["slow", "normal", "fast"]

def clean_json_string(s):
    # Remove any surrounding quotes
    s = s.strip()
    if s.startswith("'") and s.endswith("'"):
        s = s[1:-1]
    # Replace single quotes with double quotes
    s = s.replace("'", '"')
    # Fix escaped quotes from Windows
    s = s.replace('\\"', '"')
    return s

def predict(input_data):
    with open("model.pkl", "rb") as f:
        model = pickle.load(f)
    with open("label_encoder.pkl", "rb") as f:
        le = pickle.load(f)

    people_count = int(input_data.get("people_count", 0))
    max_capacity = int(input_data.get("max_capacity", 100))
    zone_type = str(input_data.get("zone_type", "hallway")).lower()
    movement_speed = str(input_data.get("movement_speed", "normal")).lower()
    time_of_day = input_data.get("time_of_day", "12:00")
    emergency_flag = int(input_data.get("emergency_flag", 0))

    density_ratio = people_count / max_capacity if max_capacity > 0 else 0
    zone_type_num = ZONE_TYPES.index(zone_type) if zone_type in ZONE_TYPES else 0
    speed_num = MOVEMENT_SPEEDS.index(movement_speed) if movement_speed in MOVEMENT_SPEEDS else 1

    try:
        if ":" in str(time_of_day):
            hour = int(str(time_of_day).split(":")[0])
        else:
            hour = int(time_of_day)
    except:
        hour = 12

    features = np.array([[
        people_count, max_capacity, density_ratio,
        zone_type_num, speed_num, hour, emergency_flag
    ]])

    prediction = model.predict(features)[0]
    congestion_level = le.inverse_transform([prediction])[0]
    return {"congestion_level": congestion_level}

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"congestion_level": "Low", "error": "No input"}))
        sys.exit(0)

    try:
        # Join all args in case JSON was split
        raw = " ".join(sys.argv[1:])
        raw = clean_json_string(raw)
        input_data = json.loads(raw)
        result = predict(input_data)
        print(json.dumps(result))
    except json.JSONDecodeError as e:
        # Try extracting key-value pairs manually
        try:
            raw = " ".join(sys.argv[1:])
            # Use regex to extract values
            people = int(re.search(r'people_count["\s:]+(\d+)', raw).group(1)) if re.search(r'people_count["\s:]+(\d+)', raw) else 0
            capacity = int(re.search(r'max_capacity["\s:]+(\d+)', raw).group(1)) if re.search(r'max_capacity["\s:]+(\d+)', raw) else 100
            emergency = int(re.search(r'emergency_flag["\s:]+(\d+)', raw).group(1)) if re.search(r'emergency_flag["\s:]+(\d+)', raw) else 0
            zone = re.search(r'zone_type["\s:]+["\']?(\w+)["\']?', raw)
            zone_type = zone.group(1) if zone else "hallway"
            speed = re.search(r'movement_speed["\s:]+["\']?(\w+)["\']?', raw)
            movement_speed = speed.group(1) if speed else "normal"

            input_data = {
                "people_count": people,
                "max_capacity": capacity,
                "zone_type": zone_type,
                "movement_speed": movement_speed,
                "time_of_day": "12:00",
                "emergency_flag": emergency
            }
            result = predict(input_data)
            print(json.dumps(result))
        except Exception as e2:
            print(json.dumps({"congestion_level": "High", "error": str(e2)}))
    except Exception as e:
        print(json.dumps({"congestion_level": "Moderate", "error": str(e)}))
