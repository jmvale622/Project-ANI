import os
from ultralytics import YOLO

# Absolute path to datasets — works no matter which folder you run from
BASE_DIR    = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATASET_DIR = os.path.join(BASE_DIR, "data", "datasets_crop_scoped")
MODEL_PATH  = os.path.join(BASE_DIR, "training", "yolo11m-cls.pt")

print(f"📁 Dataset path: {DATASET_DIR}")

# Load YOLOv11 classification base model
model = YOLO(MODEL_PATH)

results = model.train(
    data    = DATASET_DIR,
    epochs  = 20,
    imgsz   = 224,
    batch   = 16,
    name    = "ani_yolo11_crop_scoped_sugarcane_pests",
    project = os.path.join(BASE_DIR, "models"),
    device  = "mps",
    workers = 4,
    exist_ok = False,
)

print("\n✅ Training complete!")
print(f"📁 Best model: {model.trainer.save_dir}/weights/best.pt")