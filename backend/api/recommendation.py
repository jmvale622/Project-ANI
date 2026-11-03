import json
import logging

import requests

from .config import OLLAMA_MODEL, OLLAMA_URL
from .schemas import Recommendations

logger = logging.getLogger(__name__)


def get_recommendation(
    crop: str,
    disease_label: str,
    disease_confidence: float,
    location: str = "Philippines"
) -> dict:

    prompt = f"""
You are an expert agricultural advisor for smallholder farmers in {location}.

Crop scan results:
- Crop: {crop}
- Detected condition: {disease_label} (confidence: {disease_confidence:.0%})

Respond ONLY in this exact JSON format with no extra text, no explanation, no markdown:
{{
  "disease": {{
    "action": "specific treatment in one sentence",
    "urgency": "high | medium | low",
    "product": "recommended product or method"
  }},

  "general_advice": "one plain-language tip for the farmer"
}}
"""

    response = requests.post(
        f"{OLLAMA_URL.rstrip('/')}/api/generate",
        json={
            "model":  OLLAMA_MODEL,
            "prompt": prompt,
            "stream": False,        # wait for full response
            "format": "json"        # force JSON output
        },
        timeout=60
    )

    response.raise_for_status()
    raw = response.json().get("response", "").strip()
    return Recommendations.model_validate(json.loads(raw)).model_dump()


def get_recommendation_safe(
    crop: str,
    disease_label: str,
    disease_confidence: float,
    location: str = "Philippines"
) -> dict:
    try:
        return get_recommendation(
            crop, disease_label, disease_confidence, location
        )
    except Exception:
        logger.exception("Ollama recommendation failed")
        return {
            "disease":    {"action": "Consult your local agricultural officer", "urgency": "medium", "product": "N/A"},
            "general_advice": "Please consult your nearest DA extension office for guidance."
        }