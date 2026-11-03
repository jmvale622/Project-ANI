import boto3
import argparse
import os
import random
import hashlib
from collections import defaultdict
from PIL import Image
from io import BytesIO
from dotenv import load_dotenv

load_dotenv()

# ─── CONFIG ───────────────────────────────────────────
MINIO_ENDPOINT   = os.getenv("MINIO_ENDPOINT", "localhost:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY")
MINIO_BUCKET     = "projectani"

OUTPUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "data",
    "datasets_crop_scoped",
)
VAL_SPLIT  = 0.2
IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
SUGARCANE_PEST_PREFIX = (
    "sugarcane pest v1.v10-sugarcane_pest-v10.yolov8 (1)/"
)
SUGARCANE_PEST_TEST_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "data",
    "sugarcane_pest_test",
)
ANNOTATED_PEST_MARGIN = 0.2
parser = argparse.ArgumentParser()
parser.add_argument(
    "--update-existing",
    action="store_true",
    help="Add or refresh mapped images without deleting existing dataset files.",
)
args = parser.parse_args()

SUGARCANE_PEST_CLASSES = (
    "Sugarcane_Leaf_Hopper",
    "Sugarcane_Root_Base_Pest",
    "Sugarcane_Shoot_Borer",
    "Sugarcane_White_Sucking_Pest",
)
# ──────────────────────────────────────────────────────

# Map MinIO folder path → YOLO class name
# Key: exact MinIO prefix (bucket/folder/subfolder)
# Value: YOLO class label
FOLDER_TO_CLASS = {
    # MAIZE
    "maize/data/Blight/":                              "Maize_Blight",
    "maize/data/Common_Rust/":                         "Maize_Rust",
    "maize/data/Gray_Leaf_Spot/":                      "Maize_Gray_Leaf_Spot",
    "maize/data/Healthy/":                             "Maize_Healthy",
    "maize/archive/Corn Disease detection/Healthy corn/": "Maize_Healthy",
    "maize/archive/Corn Disease detection/Infected/":  "Maize_Blight",

    # SUGARCANE
    "Sugarcane/SUGARCANE IMAGES/BROWN LEAF SPOT (1,016)/": "Sugarcane_Brown_Spot",
    "Sugarcane/SUGARCANE IMAGES/HEALTHY (223)/":            "Sugarcane_Healthy",
    "Sugarcane/SUGARCANE IMAGES/LEAF SCORCH (100)/":        "Sugarcane_Scorch",
    "Sugarcane/SUGARCANE IMAGES/PINEAPPLE DISEASE (1,000)/":"Sugarcane_Pineapple",
    "Sugarcane/SUGARCANE IMAGES/RED ROT (1,010)/":          "Sugarcane_Red_Rot",

    # RICE
    "rice/Rice_Diseases/Bacterial Blight Disease/":      "Rice_Bacterial_Blight",
    "rice/rice_leaf_diseases/Bacterial leaf blight/":    "Rice_Bacterial_Blight",
    "rice/Rice_Diseases/Blast Disease/":                 "Rice_Leaf_Blast",
    "dif_plants_diseases/plant_diseases/Rice__leaf_blast/": "Rice_Leaf_Blast",
    "dif_plants_diseases/plant_diseases/Rice__neck_blast/": "Rice_Neck_Blast",
    "rice/Rice_Diseases/Brown Spot Disease/":            "Rice_Brown_Spot",
    "rice/rice_leaf_diseases/Brown spot/":               "Rice_Brown_Spot",
    "dif_plants_diseases/plant_diseases/Rice__brown_spot/": "Rice_Brown_Spot",
    "rice/Rice_Diseases/False Smut Disease/":            "Rice_False_Smut",
    "rice/rice_leaf_diseases/Leaf smut/":                "Rice_Leaf_Smut",
    "dif_plants_diseases/plant_diseases/Rice__healthy/":  "Rice_Healthy",

    # RICE PESTS
    "rice bug/":                                          "Rice_Bug",
    "rice leaf roller/":                                  "Rice_Leaf_Roller",
    "Rice stem borer/":                                  "Rice_Stem_Borer",

    # Out-of-scope crop examples for rejection.
    "mango/MangoLeafBD Dataset/":                         "Out_Of_Scope_Mango",

    # MAIZE PESTS
    "Corn Borers/":                                      "Corn_Borers",
    "Fall Armyworms/":                                   "Fall_Armyworm",
    "Maize fall armyworm/":                              "Fall_Armyworm",
}

# ── Connect MinIO ──────────────────────────────────────
print("🔌 Connecting to MinIO...")
s3 = boto3.client("s3",
    endpoint_url=f"http://{MINIO_ENDPOINT}",
    aws_access_key_id=MINIO_ACCESS_KEY,
    aws_secret_access_key=MINIO_SECRET_KEY
)

# ── Collect all images grouped by class ───────────────
print("Scanning MinIO folders...")
class_images = defaultdict(dict)
etag_classes = {}

for prefix, class_name in FOLDER_TO_CLASS.items():
    paginator = s3.get_paginator("list_objects_v2")
    pages     = paginator.paginate(Bucket=MINIO_BUCKET, Prefix=prefix)

    for page in pages:
        for obj in page.get("Contents", []):
            key = obj["Key"]
            ext = os.path.splitext(key)[1].lower()
            if ext in IMAGE_EXTS:
                etag = obj.get("ETag", key).strip('"')
                existing_class = etag_classes.get(etag)
                if existing_class is not None and existing_class != class_name:
                    continue
                etag_classes[etag] = class_name
                class_images[class_name].setdefault(etag, key)

# Print summary
print("\nImages found per class:")
total_found = 0
for cls, imgs in sorted(class_images.items()):
    print(f"  {cls}: {len(imgs)}")
    total_found += len(imgs)
print(f"  TOTAL: {total_found}\n")

if total_found == 0:
    raise RuntimeError(
        "No images found. Check FOLDER_TO_CLASS paths match MinIO exactly."
    )

missing_classes = set(FOLDER_TO_CLASS.values()) - set(class_images)
if missing_classes:
    raise RuntimeError(
        "No images found for required classes: "
        + ", ".join(sorted(missing_classes))
    )

if os.path.isdir(OUTPUT_DIR) and os.listdir(OUTPUT_DIR) and not args.update_existing:
    raise FileExistsError(
        f"Dataset output directory is not empty: {OUTPUT_DIR}. "
        "Use --update-existing to add mapped images without deleting existing data."
    )

def parse_annotation(line, label_key):
    fields = line.split()
    if not fields:
        return None

    try:
        class_id = int(fields[0])
        coordinates = [float(value) for value in fields[1:]]
    except ValueError as exc:
        raise ValueError(f"Invalid annotation in {label_key}: {line}") from exc

    if class_id < 0 or class_id >= len(SUGARCANE_PEST_CLASSES):
        raise ValueError(
            f"Unknown Sugarcane pest class {class_id} in {label_key}."
        )
    if any(value < -1e-6 or value > 1 + 1e-6 for value in coordinates):
        raise ValueError(
            f"Annotation coordinates are outside [0, 1] in {label_key}."
        )
    coordinates = [min(1.0, max(0.0, value)) for value in coordinates]
    if len(coordinates) == 4:
        center_x, center_y, width, height = coordinates
        bounds = (
            center_x - width / 2,
            center_y - height / 2,
            center_x + width / 2,
            center_y + height / 2,
        )
    elif len(coordinates) >= 6 and len(coordinates) % 2 == 0:
        x_values = coordinates[::2]
        y_values = coordinates[1::2]
        bounds = (
            min(x_values),
            min(y_values),
            max(x_values),
            max(y_values),
        )
    else:
        raise ValueError(
            f"Invalid box/polygon coordinates in {label_key}: {line}"
        )

    return class_id, bounds


def crop_annotation(image, bounds):
    image_width, image_height = image.size
    x_min, y_min, x_max, y_max = bounds
    left = x_min * image_width
    top = y_min * image_height
    right = x_max * image_width
    bottom = y_max * image_height
    box_width = right - left
    box_height = bottom - top
    if box_width <= 0 or box_height <= 0:
        raise ValueError("Sugarcane pest annotation has an empty crop region.")

    crop_size = min(
        max(box_width, box_height) * (1 + ANNOTATED_PEST_MARGIN),
        image_width,
        image_height,
    )
    center_x = (left + right) / 2
    center_y = (top + bottom) / 2
    crop_left = min(max(center_x - crop_size / 2, 0), image_width - crop_size)
    crop_top = min(max(center_y - crop_size / 2, 0), image_height - crop_size)
    crop_box = (
        int(crop_left),
        int(crop_top),
        int(crop_left + crop_size),
        int(crop_top + crop_size),
    )
    return image.crop(crop_box).resize((640, 640))


def export_sugarcane_pest_annotations():
    print("\nScanning Sugarcane pest polygon annotations...")
    objects = {}
    for page in s3.get_paginator("list_objects_v2").paginate(
        Bucket=MINIO_BUCKET,
        Prefix=SUGARCANE_PEST_PREFIX,
    ):
        for obj in page.get("Contents", []):
            objects[obj["Key"]] = obj

    image_keys = {
        ("/".join(key[len(SUGARCANE_PEST_PREFIX):].split("/")[:2]), os.path.splitext(os.path.basename(key))[0]): key
        for key in objects
        if "/images/" in key[len(SUGARCANE_PEST_PREFIX):]
        and os.path.splitext(key)[1].lower() in IMAGE_EXTS
    }
    annotation_counts = defaultdict(lambda: defaultdict(int))
    exported_counts = defaultdict(lambda: defaultdict(int))
    processed = 0

    for label_key in sorted(objects):
        relative_key = label_key[len(SUGARCANE_PEST_PREFIX):]
        if "/labels/" not in relative_key or not label_key.lower().endswith(".txt"):
            continue

        partition, _, filename = relative_key.split("/", 2)
        image_key = image_keys.get(
            (f"{partition}/images", os.path.splitext(filename)[0])
        )
        if image_key is None:
            raise FileNotFoundError(
                f"No paired Sugarcane pest image for annotation {label_key}."
            )

        label_body = s3.get_object(
            Bucket=MINIO_BUCKET, Key=label_key
        )["Body"].read().decode("utf-8")
        annotations = [
            parsed
            for line in label_body.splitlines()
            if (parsed := parse_annotation(line, label_key)) is not None
        ]
        if not annotations:
            continue

        source_hash = hashlib.sha256(image_key.encode()).hexdigest()[:16]
        image_obj = s3.get_object(
            Bucket=MINIO_BUCKET, Key=image_key
        )
        image = Image.open(BytesIO(image_obj["Body"].read())).convert("RGB")

        for annotation_index, (class_id, bounds) in enumerate(annotations):
            class_name = SUGARCANE_PEST_CLASSES[class_id]
            annotation_counts[partition][class_name] += 1
            if partition == "test":
                out_dir = os.path.join(SUGARCANE_PEST_TEST_DIR, class_name)
            elif partition == "train":
                out_dir = os.path.join(OUTPUT_DIR, "train", class_name)
            elif partition == "valid":
                out_dir = os.path.join(OUTPUT_DIR, "val", class_name)
            else:
                raise ValueError(
                    f"Unexpected Sugarcane pest dataset split: {partition}"
                )
            os.makedirs(out_dir, exist_ok=True)
            output_name = f"{source_hash}_{annotation_index:03d}.jpg"
            output_path = os.path.join(out_dir, output_name)
            if os.path.exists(output_path):
                exported_counts[partition][class_name] += 1
                continue

            cropped = crop_annotation(image, bounds)
            try:
                cropped.save(output_path, format="JPEG")
            finally:
                cropped.close()
            exported_counts[partition][class_name] += 1
            processed += 1

        image.close()

    print("Sugarcane pest annotation counts by source split:")
    for partition in ("train", "valid", "test"):
        expected = annotation_counts[partition]
        exported = exported_counts[partition]
        print(
            f"  {partition}: "
            + ", ".join(
                f"{name} {exported[name]}/{expected[name]}"
                for name in SUGARCANE_PEST_CLASSES
            )
        )
    print(
        f"Exported {processed} new Sugarcane pest crops. "
        f"Independent test crops: {SUGARCANE_PEST_TEST_DIR}"
    )

# ── Download and save into YOLO folder structure ──────
print("Downloading and exporting images...")
total, skipped = 0, 0

for class_name, keys in class_images.items():
    keys = sorted(keys.values())
    random.Random(42).shuffle(keys)
    split_idx  = int(len(keys) * (1 - VAL_SPLIT))
    train_keys = keys[:split_idx]
    val_keys   = keys[split_idx:]

    for split, split_keys in [("train", train_keys), ("val", val_keys)]:
        out_dir = os.path.join(OUTPUT_DIR, split, class_name)
        os.makedirs(out_dir, exist_ok=True)

        for key in split_keys:
            try:
                extension = os.path.splitext(key)[1].lower()
                filename = f"{hashlib.sha256(key.encode()).hexdigest()[:16]}{extension}"
                out_path = os.path.join(out_dir, filename)

                # Skip if already exported
                if os.path.exists(out_path):
                    total += 1
                    continue

                obj = s3.get_object(Bucket=MINIO_BUCKET, Key=key)
                img = Image.open(BytesIO(obj["Body"].read())).convert("RGB")
                img = img.resize((640, 640))
                img.save(out_path)
                total += 1

                if total % 200 == 0:
                    print(f"  Exported {total} images...")

            except Exception as e:
                print(f" Skipped {key}: {e}")
                skipped += 1

    print(f" [{class_name}] train: {len(train_keys)} | val: {len(val_keys)}")

print(f"\nDone! {total} exported, {skipped} skipped")
print(f"Saved to: {OUTPUT_DIR}")
export_sugarcane_pest_annotations()