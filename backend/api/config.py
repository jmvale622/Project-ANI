import os
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parents[1]
load_dotenv(BASE_DIR / ".env")

MODEL_PATH = Path(
    os.getenv(
        "ANI_MODEL_PATH",
        "models/ani_yolo11_crop_scoped_sugarcane_pests/weights/best.pt",
    )
).expanduser()
if not MODEL_PATH.is_absolute():
    MODEL_PATH = BASE_DIR / MODEL_PATH

MAX_IMAGE_BYTES = int(os.getenv("MAX_IMAGE_BYTES", str(10 * 1024 * 1024)))
if MAX_IMAGE_BYTES <= 0:
    raise ValueError("MAX_IMAGE_BYTES must be greater than zero")

MAX_IMAGE_PIXELS = int(os.getenv("MAX_IMAGE_PIXELS", "25000000"))
if MAX_IMAGE_PIXELS <= 0:
    raise ValueError("MAX_IMAGE_PIXELS must be greater than zero")

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.2")
CORS_ORIGINS = [
    origin.strip()
    for origin in os.getenv("CORS_ORIGINS", "*").split(",")
    if origin.strip()
]

CB_HOST = os.getenv("CB_HOST", "localhost")
CB_USER = os.getenv("CB_USER", "Administrator")
CB_PASSWORD = os.getenv("CB_PASSWORD")
CB_BUCKET = os.getenv("CB_BUCKET", "ANI")
