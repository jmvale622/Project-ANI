
from pathlib import Path
import sys
from ultralytics import YOLO

MODEL_PATH = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    "models/ani_yolo11_crop_scoped_sugarcane_pests/weights/best.pt"
)
IMAGE_SIZE = 224


def main():
    if not MODEL_PATH.is_file():
        raise FileNotFoundError(
            f"Model not found: {MODEL_PATH.resolve()}"
        )

    # Export ONNX independently.
    print("Exporting ONNX...")
    onnx_path = YOLO(str(MODEL_PATH)).export(
        format="onnx",
        imgsz=IMAGE_SIZE,
        batch=1,
        device="cpu",
    )
    print(f"ONNX model: {onnx_path}")

    # Export LiteRT independently.
    print("Exporting LiteRT...")
    litert_path = YOLO(str(MODEL_PATH)).export(
        format="litert",
        imgsz=IMAGE_SIZE,
        batch=1,
        device="cpu",
    )
    print(f"LiteRT model: {litert_path}")


if __name__ == "__main__":
    main()
