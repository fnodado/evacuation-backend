"""
detect_zones.py - OpenCV rectangle detection for building blueprints
Usage: python detect_zones.py <image_path>
Output: JSON array of detected zones
"""
import sys
import json
import os

def detect_zones(image_path):
    try:
        import cv2
        import numpy as np
    except ImportError:
        sys.stderr.write("opencv-python not installed. Run: pip install opencv-python\n")
        return []

    img = cv2.imread(image_path)
    if img is None:
        sys.stderr.write(f"Could not read image: {image_path}\n")
        return []

    h, w = img.shape[:2]
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # Threshold: walls are dark lines on white background
    _, thresh = cv2.threshold(gray, 200, 255, cv2.THRESH_BINARY_INV)

    # Close small gaps in wall lines
    kernel = np.ones((3, 3), np.uint8)
    closed = cv2.morphologyEx(thresh, cv2.MORPH_CLOSE, kernel, iterations=2)

    contours, _ = cv2.findContours(closed, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)

    min_area = (h * w) * 0.003   # at least 0.3% of image area
    max_area = (h * w) * 0.35    # at most 35% of image area

    candidates = []
    for contour in contours:
        area = cv2.contourArea(contour)
        if area < min_area or area > max_area:
            continue

        x, y, rw, rh = cv2.boundingRect(contour)
        if rw * rh == 0:
            continue

        # Keep only shapes that are mostly rectangular
        fill_ratio = area / (rw * rh)
        if fill_ratio < 0.55:
            continue

        candidates.append((x, y, rw, rh, area))

    # Remove duplicates: if one box is almost entirely inside another, keep the smaller one
    filtered = []
    for i, (x1, y1, w1, h1, a1) in enumerate(candidates):
        dominated = False
        for j, (x2, y2, w2, h2, a2) in enumerate(candidates):
            if i == j:
                continue
            # Check overlap
            ox = max(0, min(x1+w1, x2+w2) - max(x1, x2))
            oy = max(0, min(y1+h1, y2+h2) - max(y1, y2))
            overlap = ox * oy
            if overlap > 0.85 * a1 and a2 > a1:
                dominated = True
                break
        if not dominated:
            filtered.append((x1, y1, w1, h1, a1))

    # Sort top-left to bottom-right
    filtered.sort(key=lambda z: (z[1] // (h // 6), z[0]))

    zones = []
    for i, (x, y, rw, rh, area) in enumerate(filtered):
        cx, cy = x + rw // 2, y + rh // 2
        ratio = area / (h * w)
        aspect = rw / rh if rh > 0 else 1

        # Position label
        row = "north" if cy < h / 3 else ("south" if cy > 2 * h / 3 else "center")
        col = "west"  if cx < w / 3 else ("east"  if cx > 2 * w / 3 else "")
        position = row + ("-" + col if col else "")

        # Zone type from shape
        if aspect > 3.5 or aspect < 0.29:
            zone_type = "hallway"
        elif ratio > 0.07:
            zone_type = "lobby"
        elif ratio < 0.012:
            zone_type = "office"
        else:
            zone_type = "classroom"

        capacity = max(10, int(ratio * 600))

        zones.append({
            "name": f"Zone {i + 1}",
            "type": zone_type,
            "capacity": capacity,
            "position": position
        })

    return zones


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps([]))
        sys.exit(0)

    path = sys.argv[1]
    if not os.path.exists(path):
        sys.stderr.write(f"File not found: {path}\n")
        print(json.dumps([]))
        sys.exit(1)

    result = detect_zones(path)
    print(json.dumps(result))
