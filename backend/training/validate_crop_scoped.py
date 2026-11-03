from pathlib import Path
import argparse

from ultralytics import YOLO

BASE_DIR = Path(__file__).resolve().parents[1]
DATASET_DIR = BASE_DIR / "data" / "datasets_crop_scoped"
DEFAULT_MODEL_PATH = (
    BASE_DIR / "models" / "ani_yolo11_crop_scoped_sugarcane_pests"
    / "weights" / "best.pt"
)
PEST_CLASSES_BY_CROP = {
    "Rice": {
        "Rice_Bug",
        "Rice_Leaf_Roller",
        "Rice_Stem_Borer",
    },
    "Sugarcane": {
        "Sugarcane_Leaf_Hopper",
        "Sugarcane_Root_Base_Pest",
        "Sugarcane_Shoot_Borer",
        "Sugarcane_White_Sucking_Pest",
    },
}
MIN_PREDICTION_CONFIDENCE = 0.70


def evaluate_pest_mode(
    model: YOLO,
    classes: list[str],
    crop: str,
    validation_images: list[Path],
    independent_test: bool = False,
) -> None:
    pest_classes = PEST_CLASSES_BY_CROP[crop]
    pest_indices = {
        name: classes.index(name)
        for name in pest_classes
        if name in classes
    }
    if set(pest_indices) != pest_classes:
        raise ValueError(f"The trained model is missing {crop} pest classes.")

    total_accepted = 0
    accepted_correct = 0
    false_positives = 0
    per_class = {
        name: {"total": 0, "accepted": 0, "correct": 0}
        for name in sorted(pest_classes)
    }
    for start in range(0, len(validation_images), 64):
        batch_paths = validation_images[start:start + 64]
        predictions = model.predict(
            source=[str(path) for path in batch_paths],
            imgsz=224,
            batch=16,
            device="mps",
            verbose=False,
        )
        for prediction in predictions:
            expected_class = Path(prediction.path).parent.name
            scores = prediction.probs.data.cpu().tolist()
            best_class = max(
                pest_indices,
                key=lambda name: scores[pest_indices[name]],
            )
            confidence = scores[pest_indices[best_class]]
            if confidence >= MIN_PREDICTION_CONFIDENCE:
                total_accepted += 1
                if best_class == expected_class:
                    accepted_correct += 1
                elif expected_class not in pest_classes:
                    false_positives += 1
            if expected_class in pest_classes:
                class_metrics = per_class[expected_class]
                class_metrics["total"] += 1
                if confidence >= MIN_PREDICTION_CONFIDENCE:
                    class_metrics["accepted"] += 1
                    if best_class == expected_class:
                        class_metrics["correct"] += 1

    if independent_test:
        print(
            f"{crop} Pest independent test at "
            f"{MIN_PREDICTION_CONFIDENCE:.0%} confidence: "
            f"{accepted_correct}/{total_accepted} accepted predictions "
            f"correct; {total_accepted}/{len(validation_images)} crops "
            "accepted. This pest-only split does not measure non-pest "
            "false-positive rate."
        )
    else:
        print(
            f"{crop} Pest validation at "
            f"{MIN_PREDICTION_CONFIDENCE:.0%} confidence: "
            f"{accepted_correct}/{total_accepted} accepted predictions "
            f"correct ({false_positives} false positives from non-pest "
            f"classes); {total_accepted}/{len(validation_images)} images "
            "accepted."
        )
    for name, metrics in per_class.items():
        print(
            f"  {name}: {metrics['correct']}/{metrics['total']} correct; "
            f"{metrics['accepted']}/{metrics['total']} accepted."
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path, default=DEFAULT_MODEL_PATH)
    parser.add_argument(
        "--sugarcane-test-dir",
        type=Path,
        default=BASE_DIR / "data" / "sugarcane_pest_test",
        help="Independent Sugarcane pest test crops exported from MinIO.",
    )
    args = parser.parse_args()

    model = YOLO(str(args.model))
    model_classes = [
        model.names[index] for index in range(len(model.names))
    ]
    expected_classes = sorted(
        path.name
        for path in (DATASET_DIR / "train").iterdir()
        if path.is_dir()
    )
    if model_classes != expected_classes:
        raise ValueError(
            f"Model classes differ from the dataset: "
            f"{model_classes} != {expected_classes}"
        )

    validation_results = model.val(
        data=str(DATASET_DIR),
        split="val",
        imgsz=224,
        batch=16,
        device="mps",
    )
    print(f"Top-1 accuracy: {validation_results.top1:.2%}")
    print(f"Top-5 accuracy: {validation_results.top5:.2%}")

    validation_images = sorted(
        path
        for class_dir in (DATASET_DIR / "val").iterdir()
        if class_dir.is_dir()
        for path in class_dir.iterdir()
        if path.is_file()
    )
    for crop in PEST_CLASSES_BY_CROP:
        evaluate_pest_mode(model, model_classes, crop, validation_images)

    test_images = sorted(
        path
        for class_dir in args.sugarcane_test_dir.iterdir()
        if class_dir.is_dir()
        for path in class_dir.iterdir()
        if path.is_file()
    )
    if not test_images:
        raise ValueError(
            f"No independent Sugarcane pest test crops found in "
            f"{args.sugarcane_test_dir}."
        )
    evaluate_pest_mode(
        model,
        model_classes,
        "Sugarcane",
        test_images,
        independent_test=True,
    )


if __name__ == "__main__":
    main()
