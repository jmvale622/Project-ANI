import base64
import json
import logging
import os
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from typing import AsyncIterator

import requests
from fastapi import Depends, FastAPI, File, HTTPException, Query, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response

from .config import (
    CB_BUCKET,
    CB_HOST,
    CB_PASSWORD,
    CB_USER,
    CORS_ORIGINS,
    MAX_IMAGE_BYTES,
    OLLAMA_MODEL,
    OLLAMA_URL,
)
from .auth import (
    account_document_id,
    create_session,
    get_current_account,
    hash_password,
    verify_password,
)
from .predict import (
    InvalidImageError,
    NoSuitablePredictionError,
    run_prediction,
)
from .recommendation import get_recommendation_safe
from .schemas import (
    AuthResponse,
    ChangePasswordRequest,
    PredictResponse,
    SignInRequest,
    SignUpRequest,
    UserProfile,
)
from .weather import scrape_all_pagasa
from couchbase.exceptions import DocumentExistsException, DocumentNotFoundException

try:
    from couchbase.auth import PasswordAuthenticator
    from couchbase.cluster import Cluster
    from couchbase.options import ClusterOptions, QueryOptions
except ImportError:
    PasswordAuthenticator = None
    Cluster = None
    ClusterOptions = None
    QueryOptions = None

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    if not all((Cluster, ClusterOptions, PasswordAuthenticator, QueryOptions)):
        raise RuntimeError("Couchbase SDK is unavailable; database persistence is required")
    if not CB_PASSWORD:
        raise RuntimeError("CB_PASSWORD is required; database persistence is enabled")

    auth = PasswordAuthenticator(CB_USER, CB_PASSWORD)
    cluster = Cluster(f"couchbase://{CB_HOST}", ClusterOptions(auth))
    try:
        cluster.wait_until_ready(timedelta(seconds=30))
        index_result = cluster.query(
            f"CREATE PRIMARY INDEX IF NOT EXISTS ON `{CB_BUCKET}`._default._default"
        )
        list(index_result.rows())
        app.state.cluster = cluster
        app.state.collection = cluster.bucket(CB_BUCKET).default_collection()
        yield
    finally:
        cluster.close()

app = FastAPI(title="ANI Crop Disease Detection API", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)


@app.get("/")
def root():
    return {"message": "Running"}


def public_user(account: dict) -> UserProfile:
    return UserProfile(
        userId=account["user_id"],
        fullName=account["full_name"],
        contact=account["contact"],
        address=account.get("address", ""),
        plots=account.get("plots", "3"),
        hectares=account.get("hectares", "0.8"),
        crops=account.get("crops", ""),
    )


@app.post("/auth/signup", response_model=AuthResponse, status_code=201)
def sign_up(request: Request, payload: SignUpRequest):
    normalized_contact = payload.contact.strip()
    if not normalized_contact or not payload.fullName.strip():
        raise HTTPException(status_code=422, detail="Name and contact are required.")

    collection = request.app.state.collection
    account_id = account_document_id(normalized_contact)
    user_id = str(uuid.uuid4())
    salt, password_hash = hash_password(payload.password)
    account = {
        "type": "account",
        "user_id": user_id,
        "full_name": payload.fullName.strip(),
        "contact": normalized_contact,
        "normalized_contact": normalized_contact.casefold(),
        "address": payload.address.strip(),
        "plots": payload.plots,
        "hectares": payload.hectares,
        "crops": payload.crops,
        "password_salt": salt,
        "password_hash": password_hash,
        "created_at": datetime.now(timezone.utc).isoformat(),
    }

    try:
        collection.insert(account_id, account)
        collection.insert(f"user::{user_id}", account)
    except DocumentExistsException as exc:
        raise HTTPException(
            status_code=409,
            detail="An account with this phone/email already exists.",
        ) from exc
    except Exception as exc:
        logger.exception("Unable to save new account")
        try:
            collection.remove(account_id)
        except DocumentNotFoundException:
            pass
        raise HTTPException(status_code=503, detail="Account storage is unavailable.") from exc

    token = create_session(collection, user_id)
    return AuthResponse(access_token=token, user=public_user(account))


@app.post("/auth/signin", response_model=AuthResponse)
def sign_in(request: Request, payload: SignInRequest):
    collection = request.app.state.collection
    try:
        account = collection.get(account_document_id(payload.contact)).content_as[dict]
    except DocumentNotFoundException as exc:
        raise HTTPException(status_code=401, detail="Invalid phone/email or password.") from exc
    except Exception as exc:
        logger.exception("Unable to read account")
        raise HTTPException(status_code=503, detail="Account storage is unavailable.") from exc

    if not verify_password(
        payload.password,
        account["password_salt"],
        account["password_hash"],
    ):
        raise HTTPException(status_code=401, detail="Invalid phone/email or password.")

    profile = collection.get(f"user::{account['user_id']}").content_as[dict]
    token = create_session(collection, account["user_id"])
    return AuthResponse(access_token=token, user=public_user(profile))


@app.post("/auth/change-password")
def change_password(
    request: Request,
    payload: ChangePasswordRequest,
    account: dict = Depends(get_current_account),
):
    if not verify_password(
        payload.currentPassword,
        account["password_salt"],
        account["password_hash"],
    ):
        raise HTTPException(status_code=401, detail="Current PIN is incorrect.")

    salt, password_hash = hash_password(payload.newPassword)
    account["password_salt"] = salt
    account["password_hash"] = password_hash
    collection = request.app.state.collection
    try:
        collection.upsert(account_document_id(account["contact"]), account)
        collection.upsert(f"user::{account['user_id']}", account)
    except Exception as exc:
        logger.exception("Unable to update account password")
        raise HTTPException(status_code=503, detail="Account storage is unavailable.") from exc
    return {"message": "Password changed."}


@app.post("/predict", response_model=PredictResponse)
def predict(
    request: Request,
    file: UploadFile = File(...),
    location: str = Query(default="Philippines", max_length=200),
    scan_mode: str = Query(default="disease", pattern="^(disease|pest)$"),
    selected_crop: str = Query(default="Rice", max_length=100),
    account: dict = Depends(get_current_account),
):
    image_bytes = file.file.read(MAX_IMAGE_BYTES + 1)
    if len(image_bytes) > MAX_IMAGE_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"Image exceeds the {MAX_IMAGE_BYTES}-byte upload limit",
        )

    scan_id = str(uuid.uuid4())
    timestamp = datetime.now(timezone.utc).isoformat()
    scan_doc = {
        "type": "scan_result",
        "scan_id": scan_id,
        "user_id": account["user_id"],
        "timestamp": timestamp,
        "crop": selected_crop,
        "scan_mode": scan_mode,
        "status": "captured",
        "overall_accuracy": "0%",
        "disease": "",
        "confidence": 0,
        "classification_state": "captured",
        "result": None,
        "recommendations": None,
        "image_base64": base64.b64encode(image_bytes).decode("ascii"),
        "image_content_type": file.content_type or "application/octet-stream",
        "image_filename": file.filename or "capture",
    }
    collection = request.app.state.collection
    try:
        collection.insert(f"scan::{scan_id}", scan_doc)
    except Exception as exc:
        logger.exception("Unable to save captured image")
        raise HTTPException(status_code=503, detail="Could not save captured image.") from exc

    try:
        result = run_prediction(image_bytes, selected_crop, scan_mode)
    except InvalidImageError as exc:
        scan_doc.update({
            "status": "invalid_image",
            "classification_state": "failed",
            "failure_reason": str(exc),
        })
        collection.upsert(f"scan::{scan_id}", scan_doc)
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except NoSuitablePredictionError as exc:
        scan_doc.update({
            "status": "unclassified",
            "classification_state": "failed",
            "failure_reason": exc.reason,
        })
        collection.upsert(f"scan::{scan_id}", scan_doc)
        raise HTTPException(status_code=422, detail=exc.reason) from exc

    recommendations = get_recommendation_safe(
        crop=result["crop"],
        disease_label=result["detections"]["disease"]["label"],
        disease_confidence=result["detections"]["disease"]["confidence_raw"] / 100,
        location=location,
    )
    result["recommendations"] = recommendations
    result.pop("top_label", None)

    scan_doc.update({
        "status": result["status"],
        "classification_state": "classified",
        "crop": result["crop"],
        "overall_accuracy": result["overall_accuracy"],
        "disease": result["detections"]["disease"]["label"],
        "disease_detail": result["detections"]["disease"]["detail"],
        "confidence": result["detections"]["disease"]["confidence_raw"],
        "result": result,
        "recommendations": recommendations,
    })
    try:
        collection.upsert(f"scan::{scan_id}", scan_doc)
    except Exception as exc:
        logger.exception("Unable to save classification result")
        raise HTTPException(status_code=503, detail="Could not save classification result.") from exc

    return result


@app.get("/history")
def get_history(
    request: Request,
    limit: int = Query(default=20, ge=1, le=100),
    account: dict = Depends(get_current_account),
):
    query = f"""
        SELECT scan_id, user_id, crop, status, disease, confidence,
               timestamp, overall_accuracy, scan_mode, classification_state
        FROM `{CB_BUCKET}`._default._default
        WHERE type = $type
        AND user_id = $user_id
        ORDER BY timestamp DESC
        LIMIT $limit
    """
    rows = request.app.state.cluster.query(
        query,
        QueryOptions(
            named_parameters={
                "type": "scan_result",
                "user_id": account["user_id"],
                "limit": limit,
            }
        ),
    )
    return {"history": list(rows)}


@app.get("/history/{scan_id}/image")
def get_scan_image(
    request: Request,
    scan_id: str,
    account: dict = Depends(get_current_account),
):
    try:
        scan = request.app.state.collection.get(f"scan::{scan_id}").content_as[dict]
    except DocumentNotFoundException as exc:
        raise HTTPException(status_code=404, detail="Scan was not found.") from exc

    if scan["user_id"] != account["user_id"]:
        raise HTTPException(status_code=404, detail="Scan was not found.")

    content_type = scan["image_content_type"]
    if content_type not in {"image/jpeg", "image/png", "image/webp", "image/gif"}:
        content_type = "application/octet-stream"

    return Response(
        content=base64.b64decode(scan["image_base64"]),
        media_type=content_type,
        headers={"Content-Disposition": 'attachment; filename="capture"'},
    )


@app.get("/weather-advisory")
def weather_advisory(
    location: str = Query(default="Batangas", max_length=200),
    crop: str = Query(default="Maize", max_length=100),
):
    pagasa_data = scrape_all_pagasa(location, crop)
    context = f"""
DAILY FORECAST SYNOPSIS:
{pagasa_data['daily_forecast'].get('synopsis', 'N/A')}

FORECAST FOR {location.upper()}:
{pagasa_data['daily_forecast'].get('location_rows', [])}

WEATHER ADVISORY:
{pagasa_data['weather_advisory'].get('advisory_number', '')}
{pagasa_data['weather_advisory'].get('full_text', '')[:1000]}

CYCLONE ADVISORY:
{pagasa_data['cyclone_advisory'].get('text', 'None')}

FARM WEATHER FORECAST:
{pagasa_data['farm_forecast'].get('farm_text', 'N/A')}
{pagasa_data['farm_forecast'].get('location_rows', [])}

AGRICULTURAL IMPACT ASSESSMENT:
{pagasa_data['impact_assessment'].get('impact_text', 'N/A')}
{pagasa_data['impact_assessment'].get('location_rows', [])}
"""
    prompt = f"""
You are an agricultural weather advisor for smallholder farmers in the Philippines.

Here is the latest PAGASA weather data for {location}:
{context}

The farmer is growing: {crop}

Respond ONLY in this exact JSON format, no extra text:
{{
  "alert_level": "red | orange | yellow | green",
  "headline": "one short headline for the farmer",
  "farming_impact": "how this weather affects their {crop} specifically",
  "recommended_action": "concrete action the farmer should do today",
  "valid_until": "e.g. until tonight, next 24 hours",
  "cyclone_warning": true or false
}}
"""

    try:
        response = requests.post(
            f"{OLLAMA_URL.rstrip('/')}/api/generate",
            json={
                "model": OLLAMA_MODEL,
                "prompt": prompt,
                "stream": False,
                "format": "json",
            },
            timeout=60,
        )
        response.raise_for_status()
        advisory = json.loads(response.json().get("response", "{}"))
    except (requests.RequestException, json.JSONDecodeError, AttributeError) as exc:
        logger.warning("Weather advisory generation failed: %s", exc)
        advisory = {
            "alert_level": "yellow",
            "headline": "Check PAGASA for latest weather updates",
            "farming_impact": "Weather data temporarily unavailable",
            "recommended_action": "Visit pagasa.dost.gov.ph for latest advisory",
            "valid_until": "N/A",
            "cyclone_warning": False,
        }

    return {"pagasa_raw": pagasa_data, "advisory": advisory}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        app,
        host=os.getenv("HOST", "0.0.0.0"),
        port=int(os.getenv("PORT", "8000")),
        reload=False,
    )
