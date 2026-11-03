import cv2
from ultralytics import YOLO
import os

BASE_DIR   = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(BASE_DIR, "models", "ani_yolo11_cls-11", "weights", "best.pt")

model = YOLO(MODEL_PATH)

CONF_THRESHOLD = 0.4

cap = cv2.VideoCapture(0)   # 0 = default Mac webcam

if not cap.isOpened():
    print("Could not open webcam")
    exit()

print("Webcam started. Press 'q' to quit.")

while True:
    ret, frame = cap.read()
    if not ret:
        break

    results = model(frame, verbose=False)[0]
    top_idx    = results.probs.top1
    confidence = float(results.probs.top1conf)
    label      = results.names[top_idx]

    is_healthy = "healthy" in label.lower()
    color = (0, 200, 0) if is_healthy else (0, 0, 220)  # BGR: green / red

    h, w = frame.shape[:2]

    # Since this is a classification model (whole-frame label, no real box),
    # draw a framing rectangle around the full center region as a visual cue —
    # NOT a true detected bounding box. Switch to YOLO detect model for that.
    margin_x, margin_y = int(w * 0.15), int(h * 0.15)
    cv2.rectangle(frame, (margin_x, margin_y), (w - margin_x, h - margin_y), color, 2)

    text = f"{label.replace('_', ' ')} ({confidence:.0%})"
    if confidence < CONF_THRESHOLD:
        text = "Analyzing..."

    cv2.putText(frame, text, (margin_x, margin_y - 12),
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, color, 2)

    cv2.imshow("ANI — Real-Time Crop Scan (press q to quit)", frame)

    if cv2.waitKey(1) & 0xFF == ord("q"):
        break

cap.release()
cv2.destroyAllWindows()