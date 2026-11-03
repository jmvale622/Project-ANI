# ANI — Crop Disease Detection Backend

## Setup

1. Install dependencies:
   pip3 install -r requirements.txt

2. Set the environment variables needed for the services you use. Keep secrets
   in the deployment environment or a local, untracked `.env` file. The API
   requires the credentials for an existing Couchbase Server and a bucket
   configured in `CB_BUCKET`; it stores account records, scan results, and
   uploaded images there. The MinIO variables are used by the dataset export
   workflow. The API uses Ollama locally by default and does not require an
   Anthropic API key.

## Run with Docker Compose

The Compose stack builds the FastAPI API image and starts Ollama. The API
connects to the existing Couchbase Server on the host through
`host.docker.internal`; Couchbase is not started or initialized by Compose.
Make sure the host Couchbase service is running and the bucket in `CB_BUCKET`
already exists. From this directory:

1. Copy `.env.example` to `.env` and set the Couchbase host, username, password,
   and existing bucket name.
2. Build and start the services:
   `docker compose up --build -d`
3. Download the configured Ollama model into the persistent volume:
   `docker compose exec ollama ollama pull llama3.2`
   If `OLLAMA_MODEL` was changed in `.env`, use that model name instead.
4. Check the API at `http://localhost:8000/` or open
   `http://localhost:8000/docs`.

The first build downloads the CPU-only PyTorch runtime and can take several
minutes. The API uses the checked-in
`models/ani_yolo11_crop_scoped_sugarcane_pests/weights/best.pt` model.
Ollama stores downloaded models in the `ollama_data` Docker volume. Couchbase
stores accounts, hashed passwords, sessions, scan metadata/results, and image
bytes in the configured host bucket. These Couchbase records are independent of
the Compose lifecycle. Stop the API and Ollama with `docker compose down`.

The API port is bound to `127.0.0.1` by default. To access it from another
device on the local network, set `API_BIND_ADDRESS=0.0.0.0` and `API_PORT` in
`.env`, then use the host machine's LAN address. The API requires a registered
account for scans and history; account passwords are PBKDF2-hashed before
storage, and uploaded images are kept in Couchbase as Base64-encoded image
bytes. Each upload is limited by `MAX_IMAGE_BYTES`.

### Connect the Android app

The Android emulator uses `http://10.0.2.2:8000/` by default to reach the API
on the development computer. To install the app with a different API address,
run Gradle from the Android project directory:

`./gradlew installDebug -PaniApiBaseUrl=http://<host-address>:8000/`

For a physical phone, first set `API_BIND_ADDRESS=0.0.0.0` in the backend
`.env` and restart the stack with `docker compose up -d`. Use the development
computer's LAN IP for `<host-address>` and ensure the phone and computer are
on the same network. Use HTTPS and appropriate access controls before
deploying beyond a trusted network.

## Run Order

### Step 1 — Export images from MinIO → YOLO folders
   python3 data/export_to_yolo.py --update-existing
   (Creates data/datasets_crop_scoped with maize, sugarcane, rice disease and
   healthy classes; three Rice and four Sugarcane pest classes; merged Fall
   Armyworm images; and mango images as an out-of-scope class. The annotated
   Sugarcane pest dataset's YOLO boxes/polygons are converted to square,
   context-padded 640x640 crops, one crop per annotation. Its train and valid
   partitions supply 1,280 train crops and 75 validation crops. Its independent
   test partition is kept separate at data/sugarcane_pest_test (30 crops) and
   is never used for training. The --update-existing option adds mapped files
   without deleting existing data.)
   Dataset attribution: sugarcane-pest-v1, v10, Roboflow Universe, CC BY 4.0:
   https://universe.roboflow.com/sruthis-workspace-uocdg/sugarcane-pest-v1/dataset/10

### Step 2 — Train YOLOv11 model
   python3 training/train_yolo.py
   (Trains a new 26-class model from pretrained YOLOv11 classification
   weights and saves it under models/ani_yolo11_crop_scoped_sugarcane_pests.)

### Step 3 — Validate on the held-out split
   python3 training/validate_crop_scoped.py
   (Reports overall top-1/top-5 accuracy, Rice and Sugarcane pest-mode
   validation with the Android 70% confidence threshold, and Sugarcane pest
   results on the independent test crops.)

### Step 4 — Export models for Android
   python3.11 ani_export.py models/ani_yolo11_crop_scoped_sugarcane_pests/weights/best.pt
   (Writes best.onnx and best.tflite beside the trained best.pt.)

### Step 5 — Verify model outputs
   python3.11 verify_ani_models.py models/ani_yolo11_crop_scoped_sugarcane_pests/weights/best.pt /path/to/ANIKotlin/app/src/main/assets/models/ani_labels.txt /path/to/data/sugarcane_pest_test
   (Checks dataset/model class ordering, Android label ordering, ONNX and
   TFLite output dimensions, validation accuracy for both runtimes, and
   ONNX/LiteRT parity on the validation split and independent pest crops.)

### Step 6 — Update Android model assets
   Copy best.onnx, best.tflite, and best.pt into the Android app's
   app/src/main/assets/models directory. Keep ani_labels.txt in the exact model
   class order and set ani_model_version.txt to the SHA-256 hash of best.onnx.
   This refreshes the cached ONNX model in installed apps.
   Use an export environment with compatible versions; the validated setup used
   Python 3.11, PyTorch 2.14.1, torchvision 0.29.1, TorchAO 0.18.0,
   Ultralytics 8.4.183, LiteRT-Torch 0.10.0, and ai-edge-litert 2.3.0.

### Step 7 — Run Android tests and start the API
   Run ./gradlew :app:testDebugUnitTest in the Android project.
   From the `backend` directory, run:
   `uvicorn api.main:app --host 0.0.0.0 --port 8000`

## API deployment configuration

The API keeps the existing scan, recommendation, history, and weather-advisory
flow. Configure these optional environment variables for a deployment:

| Variable | Default | Purpose |
| --- | --- | --- |
| `ANI_MODEL_PATH` | `models/ani_yolo11_crop_scoped_sugarcane_pests/weights/best.pt` | Classification model weights |
| `MAX_IMAGE_BYTES` | `10485760` | Maximum uploaded image size |
| `MAX_IMAGE_PIXELS` | `25000000` | Maximum decoded image dimensions |
| `CORS_ORIGINS` | `*` | Comma-separated browser origins; set explicit origins for production |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama server base URL |
| `OLLAMA_MODEL` | `llama3.2` | Ollama model used for advice |
| `CB_HOST` | `host.docker.internal` in Compose | Couchbase host reachable by the API |
| `CB_USER`, `CB_PASSWORD` | `Administrator`, required | Couchbase credentials |
| `CB_BUCKET` | `ANI` | Existing Couchbase bucket for accounts, sessions, scans, and images |

Set `CORS_ORIGINS` to the exact browser origins that should call the API.
The wildcard default preserves local/mobile development compatibility; it is
not recommended for a public deployment. Account creation and sign-in use
`POST /auth/signup` and `POST /auth/signin`; scan uploads, history, image
retrieval, and password changes require the returned bearer token.

## Crop-Scoped Model Validation

See [MODEL_RUNTIME_RESULTS.md](MODEL_RUNTIME_RESULTS.md) for the Android
ONNX Runtime vs TensorFlow Lite timing comparison and recommendation.

The current model includes four annotated Sugarcane pest classes. Review both
classifier validation and the independent Sugarcane pest test results before
field use. This is a small annotated dataset; crop-level metrics do not
guarantee field-image performance. See
[MODEL_RUNTIME_RESULTS.md](MODEL_RUNTIME_RESULTS.md) for current runtime
accuracy and compatibility results.

For a physical Android phone, keep the phone and computer on the same Wi-Fi.
Set the Android API address in `mobile/ANIApp/src/services/api.js` to the
computer's current LAN IP address, and add that IP to
`mobile/ANIApp/android/app/src/main/res/xml/network_security_config.xml`.
The checked-in address is for the current development network; update it if
the computer's IP changes. Rebuild/reinstall the app after changing the address.

## API Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/` | Health check |
| `POST` | `/auth/signup` | Register a Couchbase-backed account |
| `POST` | `/auth/signin` | Sign in and receive a bearer token |
| `POST` | `/auth/change-password` | Change the signed-in account password |
| `POST` | `/predict` | Upload an image and save its classification |
| `GET` | `/history` | List the signed-in account's scans |
| `GET` | `/history/{scan_id}/image` | Retrieve an owned scan's original image |

Run the backend authentication unit tests with:

`docker compose exec api python -m unittest discover -s tests -v`

## Test in browser
   http://localhost:8000/docs