
from pathlib import Path
import json
import sys

import numpy as np
from PIL import Image
from ultralytics import YOLO

ANDROID_LABELS = {
    "Corn_Borers": "Maize|Pest|Corn Borer",
    "Fall_Armyworm": "Maize|Pest|Fall Armyworm",
    "Maize_Blight": "Maize|Disease|Maize Blight",
    "Maize_Gray_Leaf_Spot": "Maize|Disease|Maize Gray Leaf Spot",
    "Maize_Healthy": "Maize|Health|Healthy Maize",
    "Maize_Rust": "Maize|Disease|Maize Rust",
    "Out_Of_Scope_Mango": "OutOfScope|Other|Mango",
    "Rice_Bacterial_Blight": "Rice|Disease|Rice Bacterial Blight",
    "Rice_Brown_Spot": "Rice|Disease|Rice Brown Spot",
    "Rice_Bug": "Rice|Pest|Rice Bug",
    "Rice_False_Smut": "Rice|Disease|Rice False Smut",
    "Rice_Healthy": "Rice|Health|Healthy Rice",
    "Rice_Leaf_Blast": "Rice|Disease|Rice Leaf Blast",
    "Rice_Leaf_Roller": "Rice|Pest|Rice Leaf Roller",
    "Rice_Leaf_Smut": "Rice|Disease|Rice Leaf Smut",
    "Rice_Neck_Blast": "Rice|Disease|Rice Neck Blast",
    "Rice_Stem_Borer": "Rice|Pest|Rice Stem Borer",
    "Sugarcane_Brown_Spot": "Sugarcane|Disease|Sugarcane Brown Spot",
    "Sugarcane_Healthy": "Sugarcane|Health|Healthy Sugarcane",
    "Sugarcane_Leaf_Hopper": "Sugarcane|Pest|Sugarcane Leaf Hopper",
    "Sugarcane_Pineapple": "Sugarcane|Disease|Sugarcane Pineapple",
    "Sugarcane_Red_Rot": "Sugarcane|Disease|Sugarcane Red Rot",
    "Sugarcane_Root_Base_Pest": "Sugarcane|Pest|Sugarcane Root and Base Pest",
    "Sugarcane_Scorch": "Sugarcane|Disease|Sugarcane Scorch",
    "Sugarcane_Shoot_Borer": "Sugarcane|Pest|Sugarcane Shoot Borer",
    "Sugarcane_White_Sucking_Pest": "Sugarcane|Pest|Sugarcane White Sucking Pest",
}

model_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    "models/ani_yolo11_crop_scoped_sugarcane_pests/weights/best.pt"
)
weights_dir = model_path.parent
pt_path = model_path
dataset_dir = Path("data/datasets_crop_scoped")
android_labels_path = Path(sys.argv[2]) if len(sys.argv) > 2 else None

if not pt_path.exists():
    raise FileNotFoundError(f"Missing model: {pt_path.resolve()}")

# 1. Original model: class names and task
model = YOLO(str(pt_path))
expected_classes = sorted(
    path.name for path in (dataset_dir / "train").iterdir() if path.is_dir()
)
model_classes = [model.names[index] for index in range(len(model.names))]
if model_classes != expected_classes:
    raise ValueError(
        f"Model class order differs from the Android label order: "
        f"{model_classes} != {expected_classes}"
    )
if android_labels_path is not None:
    if set(model_classes) != set(ANDROID_LABELS):
        raise ValueError(
            "Android class-label mapping does not cover the trained model."
        )
    actual_android_labels = [
        line.strip()
        for line in android_labels_path.read_text().splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    expected_android_labels = [
        ANDROID_LABELS[name] for name in model_classes
    ]
    if actual_android_labels != expected_android_labels:
        raise ValueError(
            f"Android label order differs from the model class order: "
            f"{actual_android_labels} != {expected_android_labels}"
        )
    print(f"Android labels match: {android_labels_path}")

print("\n=== ORIGINAL MODEL ===")
print("Task:", model.task)
print("Classes:", json.dumps(model.names, indent=2))

# 2. ONNX input/output details
print("\n=== ONNX MODEL ===")
try:
    import onnxruntime as ort

    onnx_path = weights_dir / "best.onnx"
    session = ort.InferenceSession(
        str(onnx_path),
        providers=["CPUExecutionProvider"],
    )

    for item in session.get_inputs():
        print("Input:", item.name, item.shape, item.type)

    for item in session.get_outputs():
        print("Output:", item.name, item.shape, item.type)
        if item.shape != [1, len(model.names)]:
            raise ValueError(
                f"ONNX output shape is {item.shape}; "
                f"expected [1, {len(model.names)}]."
            )

except ImportError as exc:
    print("ONNX inspection error:", exc)

# 3. TFLite input/output details
print("\n=== TFLITE MODEL ===")
try:
    from ai_edge_litert.interpreter import Interpreter
except ImportError:
    try:
        from tensorflow.lite import Interpreter
    except ImportError as exc:
        print("TFLite inspection unavailable:", exc)
        Interpreter = None

if Interpreter is not None:
    tflite_path = weights_dir / "best.tflite"
    interpreter = Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()

    for item in interpreter.get_input_details():
        print(
            "Input:",
            item["name"],
            item["shape"].tolist(),
            item["dtype"],
            "quantization:",
            item["quantization"],
        )

    for item in interpreter.get_output_details():
        shape = item["shape"].tolist()
        print(
            "Output:",
            item["name"],
            shape,
            item["dtype"],
            "quantization:",
            item["quantization"],
        )
        if shape != [1, len(model.names)]:
            raise ValueError(
                f"TFLite output shape is {shape}; "
                f"expected [1, {len(model.names)}]."
            )

    if 'session' in locals():
        validation_images = sorted(
            path
            for class_dir in (dataset_dir / "val").iterdir()
            if class_dir.is_dir()
            for path in class_dir.iterdir()
            if path.is_file()
        )
        if not validation_images:
            raise ValueError("No held-out validation images are available.")

        independent_test_images = []
        if len(sys.argv) > 3:
            independent_test_images = sorted(
                path
                for class_dir in Path(sys.argv[3]).iterdir()
                if class_dir.is_dir()
                for path in class_dir.iterdir()
                if path.is_file()
            )
        validation_image_set = set(validation_images)
        images_to_compare = validation_images + independent_test_images
        class_indices = {
            class_name: index for index, class_name in enumerate(model_classes)
        }

        input_name = session.get_inputs()[0].name
        output_name = session.get_outputs()[0].name
        input_details = interpreter.get_input_details()[0]
        output_details = interpreter.get_output_details()[0]
        max_absolute_difference = 0.0
        matching_top1 = 0
        metrics = {
            "onnx": {"top1": 0, "top5": 0},
            "litert": {"top1": 0, "top5": 0},
        }
        for image_path in images_to_compare:
            with Image.open(image_path) as image:
                image_array = np.asarray(
                    image.convert("RGB").resize((224, 224)),
                    dtype=np.float32,
                ) / 255.0
            model_input = np.ascontiguousarray(
                np.transpose(image_array, (2, 0, 1))[np.newaxis, ...]
            )
            onnx_output = session.run(
                [output_name], {input_name: model_input}
            )[0]
            interpreter.set_tensor(
                input_details["index"], model_input
            )
            interpreter.invoke()
            tflite_output = interpreter.get_tensor(
                output_details["index"]
            )
            difference = float(
                np.max(np.abs(onnx_output - tflite_output))
            )
            max_absolute_difference = max(
                max_absolute_difference, difference
            )
            onnx_top1 = int(np.argmax(onnx_output))
            litert_top1 = int(np.argmax(tflite_output))
            matching_top1 += int(onnx_top1 == litert_top1)
            if image_path in validation_image_set:
                expected_index = class_indices[image_path.parent.name]
                metrics["onnx"]["top1"] += int(
                    onnx_top1 == expected_index
                )
                metrics["litert"]["top1"] += int(
                    litert_top1 == expected_index
                )
                metrics["onnx"]["top5"] += int(
                    expected_index in np.argsort(onnx_output[0])[-5:]
                )
                metrics["litert"]["top5"] += int(
                    expected_index in np.argsort(tflite_output[0])[-5:]
                )

        if matching_top1 != len(images_to_compare):
            raise ValueError(
                f"ONNX and TFLite top-1 predictions differ on "
                f"{len(images_to_compare) - matching_top1}/"
                f"{len(images_to_compare)} held-out images."
            )
        if max_absolute_difference > 1e-3:
            raise ValueError(
                "ONNX and TFLite output difference exceeds 1e-3: "
                f"{max_absolute_difference}."
            )
        print(
            f"ONNX/LiteRT top-1 parity: {matching_top1}/"
            f"{len(images_to_compare)} images; maximum absolute "
            f"output difference {max_absolute_difference:.3g}."
        )
        for runtime, runtime_metrics in metrics.items():
            print(
                f"{runtime.upper()} validation accuracy: "
                f"top-1 {runtime_metrics['top1']}/"
                f"{len(validation_images)} "
                f"({runtime_metrics['top1'] / len(validation_images):.2%}); "
                f"top-5 {runtime_metrics['top5']}/"
                f"{len(validation_images)} "
                f"({runtime_metrics['top5'] / len(validation_images):.2%})."
            )
