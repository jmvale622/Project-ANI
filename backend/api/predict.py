from io import BytesIO

from PIL import Image, UnidentifiedImageError
from PIL.Image import open as _PIL_IMAGE_OPEN
from ultralytics import YOLO

from .config import MAX_IMAGE_PIXELS, MODEL_PATH

MIN_PREDICTION_CONFIDENCE = 0.70
PEST_LABELS = {
    "Corn_Borers",
    "Fall_Armyworm",
    "Rice_Bug",
    "Rice_Leaf_Roller",
    "Rice_Stem_Borer",
    "Sugarcane_Leaf_Hopper",
    "Sugarcane_Root_Base_Pest",
    "Sugarcane_Shoot_Borer",
    "Sugarcane_White_Sucking_Pest",
}
DISPLAY_LABELS = {
    "Corn_Borers": "Corn Borer",
    "Fall_Armyworm": "Fall Armyworm",
    "Maize_Blight": "Maize Blight",
    "Maize_Gray_Leaf_Spot": "Maize Gray Leaf Spot",
    "Maize_Healthy": "Healthy Maize",
    "Maize_Rust": "Maize Rust",
    "Out_Of_Scope_Mango": "Mango",
    "Rice_Bacterial_Blight": "Rice Bacterial Blight",
    "Rice_Brown_Spot": "Rice Brown Spot",
    "Rice_Bug": "Rice Bug",
    "Rice_False_Smut": "Rice False Smut",
    "Rice_Healthy": "Healthy Rice",
    "Rice_Leaf_Blast": "Rice Leaf Blast",
    "Rice_Leaf_Roller": "Rice Leaf Roller",
    "Rice_Leaf_Smut": "Rice Leaf Smut",
    "Rice_Neck_Blast": "Rice Neck Blast",
    "Rice_Stem_Borer": "Rice Stem Borer",
    "Sugarcane_Brown_Spot": "Sugarcane Brown Spot",
    "Sugarcane_Healthy": "Healthy Sugarcane",
    "Sugarcane_Leaf_Hopper": "Sugarcane Leaf Hopper",
    "Sugarcane_Pineapple": "Sugarcane Pineapple",
    "Sugarcane_Red_Rot": "Sugarcane Red Rot",
    "Sugarcane_Root_Base_Pest": "Sugarcane Root and Base Pest",
    "Sugarcane_Scorch": "Sugarcane Scorch",
    "Sugarcane_Shoot_Borer": "Sugarcane Shoot Borer",
    "Sugarcane_White_Sucking_Pest": "Sugarcane White Sucking Pest",
}


class InvalidImageError(ValueError):
    pass


class NoSuitablePredictionError(ValueError):
    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason


if not MODEL_PATH.is_file():
    raise FileNotFoundError(f"ANI model weights not found: {MODEL_PATH}")

model = YOLO(str(MODEL_PATH))

DEFICIENCY_SIGNS = {
    "Maize_Blight":          ("NPK imbalance",        "Yellowing lower leaves"),
    "Maize_Rust":            ("Nitrogen deficiency",  "Pale green leaves"),
    "Maize_Gray_Leaf_Spot":  ("Zinc deficiency",      "Stunted growth"),
    "Maize_Healthy":         ("None detected",        "Crop looks healthy"),
    "Sugarcane_Brown_Spot":  ("Potassium deficiency", "Weak cell walls"),
    "Sugarcane_Healthy":     ("None detected",        "Crop looks healthy"),
    "Sugarcane_Scorch":      ("Potassium deficiency", "Browning leaf edges"),
    "Sugarcane_Pineapple":   ("Iron deficiency",      "Yellowing leaf tips"),
    "Sugarcane_Red_Rot":     ("Iron deficiency",      "Yellowing leaf tips"),
    "Pest":                  ("Pest detected",        "Inspect leaves and stems"),
}

PEST_SIGNS = {
    "Maize_Blight":          ("Armyworm risk",     "Check whorl leaves"),
    "Maize_Rust":            ("Aphid colony risk", "Check leaf undersides"),
    "Maize_Gray_Leaf_Spot":  ("Corn borer risk",   "Check mid-stalk"),
    "Maize_Healthy":         ("No pest detected",  "Continue monitoring"),
    "Sugarcane_Brown_Spot":  ("Leaf hopper risk",  "Check leaf axils"),
    "Sugarcane_Healthy":     ("No pest detected",  "Continue monitoring"),
    "Sugarcane_Scorch":      ("Whitefly risk",     "Check leaf undersides"),
    "Sugarcane_Pineapple":   ("Borer risk",        "Check at soil level"),
    "Sugarcane_Red_Rot":     ("Borer risk",        "Check at soil level"),
    "Pest":                  ("Pest detected",     "Inspect leaves and stems"),
}

def get_crop_name(label: str) -> str:
    if label.startswith("Maize"):     return "Maize"
    if label.startswith("Sugarcane"): return "Sugarcane"
    if label.startswith("Rice"):      return "Rice"
    if label in {"Corn_Borers", "Fall_Armyworm"}: return "Maize"
    if label == "Out_Of_Scope_Mango": return "OutOfScope"
    return "Unknown"

def format_label(label: str) -> str:
    return DISPLAY_LABELS.get(label, label.replace("_", " ").title())


def normalize_crop(crop: str) -> str:
    normalized = "".join(character for character in crop.lower() if character.isalpha())
    return "maize" if normalized in {"corn", "maize"} else normalized


def run_prediction(
    image_bytes: bytes,
    selected_crop: str = "Rice",
    scan_mode: str = "disease",
) -> dict:
    try:
        with _PIL_IMAGE_OPEN(BytesIO(image_bytes)) as image:
            if image.width * image.height > MAX_IMAGE_PIXELS:
                raise InvalidImageError(
                    f"Image dimensions exceed the {MAX_IMAGE_PIXELS}-pixel limit"
                )
            image.verify()
        with _PIL_IMAGE_OPEN(BytesIO(image_bytes)) as image:
            img = image.convert("RGB")
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError) as exc:
        raise InvalidImageError("Upload must be a valid, supported image") from exc

    results = model(img)[0]
    if results.probs is None:
        raise RuntimeError("Configured YOLO model does not return classification results")

    normalized_crop = normalize_crop(selected_crop)
    if normalized_crop not in {"maize", "rice", "sugarcane"}:
        raise NoSuitablePredictionError("UNSUPPORTED_CROP")

    mode = scan_mode.strip().lower()
    if mode not in {"disease", "pest"}:
        raise ValueError(f"Unsupported scan mode: {scan_mode}")

    candidate_indices = []
    for index, class_label in results.names.items():
        class_crop = normalize_crop(get_crop_name(class_label))
        is_pest = class_label in PEST_LABELS
        is_healthy = "healthy" in class_label.lower()
        if class_crop != normalized_crop:
            continue
        if (mode == "pest" and is_pest) or (
            mode == "disease" and not is_pest and (is_healthy or class_crop != "unknown")
        ):
            candidate_indices.append(index)

    if not candidate_indices:
        raise NoSuitablePredictionError("NO_CLASSES_FOR_SELECTION")

    probabilities = results.probs.data.tolist()
    top_idx = max(candidate_indices, key=lambda index: probabilities[index])
    confidence = float(probabilities[top_idx])
    if confidence < MIN_PREDICTION_CONFIDENCE:
        raise NoSuitablePredictionError("LOW_CONFIDENCE")

    label = results.names[top_idx]
    is_healthy = "healthy" in label.lower()


    return {
        "crop":             get_crop_name(label),
        "overall_accuracy": f"{confidence:.1%}",
        "status":           "healthy" if is_healthy else "diseased",
        "top_label":        label,
        "detections": {
            "disease": {
                "label":          format_label(label),
                "detail":         label.replace("_", " "),
                "confidence":     f"{confidence:.0%}",
                "confidence_raw": round(confidence * 100)
            }
        }
    }