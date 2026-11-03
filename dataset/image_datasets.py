
# from minio import Minio
# from couchbase.cluster import Cluster
# from couchbase.options import ClusterOptions
# from couchbase.auth import PasswordAuthenticator
# from datetime import timedelta
# import os

# # ─── CONFIG ───────────────────────────────────────────
# MINIO_ENDPOINT   = "localhost:9000"
# MINIO_ACCESS_KEY = "minioadmin"
# MINIO_SECRET_KEY = "minioadmin"
# MINIO_BUCKET     = "projectani"

# CB_HOST      = "localhost"
# CB_USER      = "Administrator"
# CB_PASSWORD  = "ani12345"
# CB_BUCKET    = "ANI"

# # Only import images from this folder (e.g. "rice/", "corn/", "rice/bacterial_blight/")
# # Set to "" or None to import everything
# PREFIX_FILTER = "Corn Borers/"
# # ──────────────────────────────────────────────────────

# # Connect to MinIO
# minio_client = Minio(
#     MINIO_ENDPOINT,
#     access_key=MINIO_ACCESS_KEY,
#     secret_key=MINIO_SECRET_KEY,
#     secure=False
# )

# # Connect to Couchbase
# auth    = PasswordAuthenticator(CB_USER, CB_PASSWORD)
# cluster = Cluster(f"couchbase://{CB_HOST}", ClusterOptions(auth))
# cluster.wait_until_ready(timedelta(seconds=5))
# collection = cluster.bucket(CB_BUCKET).default_collection()

# IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".webp"}

# def get_crop_type(object_name):
#     parts = object_name.split("/")
#     return parts[-2] if len(parts) > 1 else "unknown"

# # List objects under the specific folder only
# objects = minio_client.list_objects(
#     MINIO_BUCKET,
#     prefix=PREFIX_FILTER or "",
#     recursive=True
# )
# count = 0

# for obj in objects:
#     name = obj.object_name
#     ext  = os.path.splitext(name)[1].lower()

#     if ext not in IMAGE_EXTENSIONS:
#         continue

#     crop   = get_crop_type(name)
#     pest   = os.path.basename(os.path.dirname(name))  # Get the immediate parent folder as pest type
#     doc_id = name.replace("/", "_").replace(".", "_")

#     metadata = {
#         "type":      "image_metadata",
#         "crop_type": crop,
#         "pest_type": pest,
#         "details": {
#             "filename":   os.path.basename(name),
#             "label":      pest,
#             "size_bytes": obj.size,
#             "file_type":  ext.lstrip("."),
#             "minio_path": f"{MINIO_BUCKET}/{name}",
#             "minio_url":  f"http://{MINIO_ENDPOINT}/{MINIO_BUCKET}/{name}",
#             "timestamp":  str(obj.last_modified)
#         }
#     }

#     collection.upsert(doc_id, metadata)
#     print(f"[{crop}] {os.path.basename(name)} → imported")
#     count += 1

# print(f"\nDone! {count} image records imported to Couchbase.")
# print(f"Filter applied: '{PREFIX_FILTER or '(none — full bucket)'}'")




from minio import Minio
from couchbase.cluster import Cluster
from couchbase.options import ClusterOptions
from couchbase.auth import PasswordAuthenticator
from datetime import timedelta
import os

# ─── CONFIG ───────────────────────────────────────────
MINIO_ENDPOINT   = "localhost:9000"
MINIO_ACCESS_KEY = "minioadmin"
MINIO_SECRET_KEY = "minioadmin"
MINIO_BUCKET     = "projectani"

CB_HOST      = "localhost"
CB_USER      = "Administrator"
CB_PASSWORD  = "ani12345"
CB_BUCKET    = "ANI"

# Prefix is relative to the bucket root (do NOT include the bucket name here)
PREFIX_FILTER = "Maize fall armyworm/"

# What kind of data this import run contains: "pest" or "disease"
DATA_CATEGORY = "pest"

# Set to True to see what would be imported without writing to Couchbase
DRY_RUN = False
# ──────────────────────────────────────────────────────

# Connect to MinIO
minio_client = Minio(
    MINIO_ENDPOINT,
    access_key=MINIO_ACCESS_KEY,
    secret_key=MINIO_SECRET_KEY,
    secure=False
)

# Connect to Couchbase (skip entirely on dry runs so you don't need it running)
collection = None
if not DRY_RUN:
    auth    = PasswordAuthenticator(CB_USER, CB_PASSWORD)
    cluster = Cluster(f"couchbase://{CB_HOST}", ClusterOptions(auth))
    cluster.wait_until_ready(timedelta(seconds=5))
    collection = cluster.bucket(CB_BUCKET).default_collection()

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".webp"}


def get_label_parts(object_name, prefix):
    """
    Figures out the classification label(s) from the path *relative* to
    the prefix you searched on, so it works whether your data is flat
    (e.g. "Corn Borers/img.jpg") or nested (e.g. "Corn/Corn Borers/img.jpg").

    Returns (top_level_label, full_relative_folder_path)
    """
    relative = object_name[len(prefix):] if prefix and object_name.startswith(prefix) else object_name
    parts = relative.split("/")
    folder_parts = parts[:-1]  # drop the filename itself

    if not folder_parts:
        # No subfolder at all — fall back to the prefix's last segment
        label = prefix.strip("/").split("/")[-1] if prefix else "unknown"
    else:
        label = folder_parts[-1]  # innermost folder = specific class (e.g. "Corn Borers")

    return label, "/".join(folder_parts)


objects = minio_client.list_objects(
    MINIO_BUCKET,
    prefix=PREFIX_FILTER or "",
    recursive=True
)
count = 0
skipped = 0

for obj in objects:
    name = obj.object_name
    ext  = os.path.splitext(name)[1].lower()

    if ext not in IMAGE_EXTENSIONS:
        skipped += 1
        continue

    label, folder_path = get_label_parts(name, PREFIX_FILTER)
    doc_id = name.replace("/", "_").replace(" ", "_").replace(".", "_")

    metadata = {
        "type":     "image_metadata",
        "category": DATA_CATEGORY,          # "pest" or "disease"
        "label":    label,                  # e.g. "Corn Borers"
        "details": {
            "filename":    os.path.basename(name),
            "folder_path": folder_path,      # full class path, useful if nested
            "size_bytes":  obj.size,
            "file_type":   ext.lstrip("."),
            "minio_path":  f"{MINIO_BUCKET}/{name}",
            "minio_url":   f"http://{MINIO_ENDPOINT}/{MINIO_BUCKET}/{name}",
            "timestamp":   str(obj.last_modified)
        }
    }

    if DRY_RUN:
        print(f"[DRY RUN][{DATA_CATEGORY}:{label}] {os.path.basename(name)}")
    else:
        collection.upsert(doc_id, metadata)
        print(f"[{DATA_CATEGORY}:{label}] {os.path.basename(name)} → imported")

    count += 1

print(f"\nDone! {count} image records {'would be ' if DRY_RUN else ''}imported to Couchbase.")
if skipped:
    print(f"Skipped {skipped} non-image file(s).")
print(f"Filter applied: '{PREFIX_FILTER or '(none — full bucket)'}'")
print(f"Category: '{DATA_CATEGORY}'")