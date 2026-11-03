import os
import random
import shutil
from collections import defaultdict
from PIL import Image, ImageEnhance, ImageOps
import numpy as np
from typing import Optional

# ─── CONFIG ───────────────────────────────────────────
SOURCE_DIR  = "data/datasets"        # output of export_for_yolo.py
OUTPUT_DIR  = "data/datasets_clean"  # new cleaned + augmented dataset
TARGET_SIZE = (224, 224)
MIN_FILE_SIZE_KB = 5          # below this, treat as corrupt/empty
BLUR_THRESHOLD    = 100       # lower variance = blurrier (Laplacian method)
TARGET_PER_CLASS  = 600       # balance target — classes below this get augmented up
# ──────────────────────────────────────────────────────


def is_corrupt_or_tiny(path: str) -> bool:
    try:
        size_kb = os.path.getsize(path) / 1024
        if size_kb < MIN_FILE_SIZE_KB:
            return True
        img = Image.open(path)
        img.verify()
        return False
    except Exception:
        return True


def laplacian_variance(img: Image.Image) -> float:
    """Simple blur detector without OpenCV — edge variance via numpy gradient."""
    gray = np.array(img.convert("L"), dtype=np.float64)
    gy, gx = np.gradient(gray)
    return float(np.var(gx) + np.var(gy))


def is_too_blurry(img: Image.Image) -> bool:
    return laplacian_variance(img) < BLUR_THRESHOLD


def clean_image(path: str) -> Optional[Image.Image]:
    """Load, verify, resize, normalize orientation. Returns None if unusable."""
    if is_corrupt_or_tiny(path):
        return None
    try:
        img = Image.open(path).convert("RGB")
        img = ImageOps.exif_transpose(img)   # fix phone camera rotation
        if is_too_blurry(img):
            return None
        img = img.resize(TARGET_SIZE)
        return img
    except Exception:
        return None


def augment_image(img: Image.Image, seed: int) -> Image.Image:
    """Apply one random augmentation — flip, rotate, or brightness shift."""
    rng = random.Random(seed)
    choice = rng.choice(["flip", "rotate", "brightness", "contrast"])

    if choice == "flip":
        return ImageOps.mirror(img)
    elif choice == "rotate":
        angle = rng.choice([-15, -10, 10, 15])
        return img.rotate(angle, fillcolor=(255, 255, 255))
    elif choice == "brightness":
        factor = rng.uniform(0.7, 1.3)
        return ImageEnhance.Brightness(img).enhance(factor)
    else:
        factor = rng.uniform(0.8, 1.2)
        return ImageEnhance.Contrast(img).enhance(factor)


def process_split(split: str):
    split_dir = os.path.join(SOURCE_DIR, split)
    if not os.path.exists(split_dir):
        print(f"⚠️ Skipping missing split: {split}")
        return

    classes = [d for d in os.listdir(split_dir)
               if os.path.isdir(os.path.join(split_dir, d))]

    print(f"\n📂 Processing split: {split} ({len(classes)} classes)")

    for class_name in classes:
        in_dir  = os.path.join(split_dir, class_name)
        out_dir = os.path.join(OUTPUT_DIR, split, class_name)
        os.makedirs(out_dir, exist_ok=True)

        files = [f for f in os.listdir(in_dir)
                 if f.lower().endswith((".jpg", ".jpeg", ".png", ".bmp", ".webp"))]

        cleaned_count, rejected_count = 0, 0
        cleaned_images = []

        for fname in files:
            in_path = os.path.join(in_dir, fname)
            img = clean_image(in_path)

            if img is None:
                rejected_count += 1
                continue

            out_path = os.path.join(out_dir, fname)
            img.save(out_path, quality=90)
            cleaned_images.append(out_path)
            cleaned_count += 1

        # ── Balance: augment up to TARGET_PER_CLASS (train split only) ──
        added_count = 0
        if split == "train" and 0 < cleaned_count < TARGET_PER_CLASS:
            needed = TARGET_PER_CLASS - cleaned_count
            for i in range(needed):
                src_path = random.choice(cleaned_images)
                img = Image.open(src_path)
                aug_img = augment_image(img, seed=i)
                aug_name = f"aug_{i}_{os.path.basename(src_path)}"
                aug_img.save(os.path.join(out_dir, aug_name), quality=90)
                added_count += 1

        print(f"  [{class_name}] kept: {cleaned_count} | rejected: {rejected_count} | augmented: {added_count}")


if __name__ == "__main__":
    if os.path.exists(OUTPUT_DIR):
        shutil.rmtree(OUTPUT_DIR)

    process_split("train")
    process_split("val")

    print(f"\n✅ Done! Cleaned dataset saved to: {OUTPUT_DIR}")
    print("   Update train_yolo.py to point DATASET_DIR here instead.")