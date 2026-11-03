from __future__ import annotations

import logging
from datetime import datetime, timezone
from io import BytesIO

import pdfplumber
import requests
from bs4 import BeautifulSoup

HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; ANI-AgriBot/1.0)"}
logger = logging.getLogger(__name__)

URLS = {
    "daily_forecast":      "https://pagasa.dost.gov.ph/weather",
    "weather_advisory":    "https://pagasa.dost.gov.ph/weather/weather-advisory",
    "cyclone_advisory":    "https://pagasa.dost.gov.ph/tropical-cyclone-advisory-iframe",
    "farm_forecast":       "https://pagasa.dost.gov.ph/agri-weather",
    "impact_assessment":   "https://pagasa.dost.gov.ph/agri-weather/impact-assessment-for-agriculture",
    "advisory_pdf":        "https://pubfiles.pagasa.dost.gov.ph/tamss/weather/advisory.pdf",
    "daily_forecast_pdf":  "https://pubfiles.pagasa.dost.gov.ph/tamss/weather/pf.pdf",
}


def _get_soup(url: str) -> BeautifulSoup | None:
    try:
        r = requests.get(url, headers=HEADERS, timeout=10)
        r.raise_for_status()
        return BeautifulSoup(r.text, "html.parser")
    except requests.RequestException:
        logger.exception("Failed to fetch %s", url)
        return None


def _get_pdf_text(url: str) -> str:
    """Download PDF and extract all text."""
    try:
        r = requests.get(url, headers=HEADERS, timeout=15)
        r.raise_for_status()
        with pdfplumber.open(BytesIO(r.content)) as pdf:
            return "\n".join(
                page.extract_text() or "" for page in pdf.pages
            ).strip()
    except (requests.RequestException, OSError, ValueError):
        logger.exception("Failed to fetch PDF %s", url)
        return ""


def _extract_table_rows(soup: BeautifulSoup) -> list[dict]:
    """Extract all tables as list of row dicts."""
    rows = []
    for table in soup.find_all("table"):
        headers = [th.get_text(strip=True) for th in table.find_all("th")]
        for tr in table.find_all("tr"):
            cells = [td.get_text(strip=True) for td in tr.find_all("td")]
            if cells and headers:
                rows.append(dict(zip(headers, cells)))
    return rows


def _filter_rows_for_location(rows: list[dict], location: str) -> list[dict]:
    """Filter table rows that mention the target location."""
    return [
        row for row in rows
        if any(location.lower() in str(v).lower() for v in row.values())
    ]


# ── Individual scrapers ────────────────────────────────────────────────────────

def scrape_daily_forecast(location: str = "Batangas") -> dict:
    soup = _get_soup(URLS["daily_forecast"])
    if not soup:
        return {"error": "unavailable"}

    # Synopsis
    synopsis = ""
    for tag in soup.find_all(["p", "div"]):
        text = tag.get_text(strip=True)
        if "monsoon" in text.lower() or "synopsis" in text.lower():
            synopsis = text
            break

    # Forecast table — filter rows mentioning location
    all_rows = _extract_table_rows(soup)
    location_rows = _filter_rows_for_location(all_rows, location)

    return {
        "source":        "PAGASA Daily Weather Forecast",
        "synopsis":      synopsis,
        "location_rows": location_rows,   # rows specifically mentioning location
        "all_rows":      all_rows,        # full table for Ollama context
    }


def scrape_weather_advisory() -> dict:
    soup = _get_soup(URLS["weather_advisory"])
    advisory_number = ""
    if soup:
        # Extract advisory number from heading
        for tag in soup.find_all(["h4", "h3", "h2", "strong", "b"]):
            text = tag.get_text(strip=True)
            if "advisory" in text.lower() and "no." in text.lower():
                advisory_number = text
                break

    # Get full text from the PDF
    pdf_text = _get_pdf_text(URLS["advisory_pdf"])

    return {
        "source":          "PAGASA Weather Advisory",
        "advisory_number": advisory_number,
        "full_text":       pdf_text[:3000],  # cap at 3000 chars for Ollama context
    }


def scrape_cyclone_advisory() -> dict:
    soup = _get_soup(URLS["cyclone_advisory"])
    if not soup:
        return {"source": "PAGASA Tropical Cyclone Advisory", "text": "No active cyclone advisory"}

    text = soup.get_text(separator=" ", strip=True)
    # Look for cyclone-related content
    relevant = ""
    for line in text.split("\n"):
        if any(kw in line.lower() for kw in ["cyclone", "typhoon", "storm", "signal", "warning"]):
            relevant += line + "\n"

    return {
        "source": "PAGASA Tropical Cyclone Advisory",
        "text":   relevant.strip() or "No active tropical cyclone advisory",
    }


def scrape_farm_forecast(location: str = "Batangas") -> dict:
    soup = _get_soup(URLS["farm_forecast"])
    if not soup:
        return {"error": "unavailable"}

    all_rows = _extract_table_rows(soup)
    location_rows = _filter_rows_for_location(all_rows, location)

    # Also grab any paragraph text near "farm weather"
    farm_text = ""
    for tag in soup.find_all(["p", "div"]):
        text = tag.get_text(strip=True)
        if "farm" in text.lower() and len(text) > 50:
            farm_text = text
            break

    return {
        "source":        "PAGASA Farm Weather Forecast",
        "farm_text":     farm_text,
        "location_rows": location_rows,
        "all_rows":      all_rows,
    }


def scrape_impact_assessment(location: str = "Batangas") -> dict:
    soup = _get_soup(URLS["impact_assessment"])
    if not soup:
        return {"error": "unavailable"}

    all_rows = _extract_table_rows(soup)
    location_rows = _filter_rows_for_location(all_rows, location)

    # Grab any impact-related paragraphs
    impact_text = ""
    for tag in soup.find_all(["p", "div"]):
        text = tag.get_text(strip=True)
        if any(kw in text.lower() for kw in ["impact", "agriculture", "crop", "damage"]) and len(text) > 50:
            impact_text = text
            break

    return {
        "source":        "PAGASA Impact Assessment for Agriculture",
        "impact_text":   impact_text,
        "location_rows": location_rows,
    }


# ── Master scraper — combines all 5 sources ────────────────────────────────────

def scrape_all_pagasa(location: str = "Batangas", crop: str = "Maize") -> dict:
    return {
        "timestamp":        datetime.now(timezone.utc).isoformat(),
        "location":         location,
        "crop":             crop,
        "daily_forecast":   scrape_daily_forecast(location),
        "weather_advisory": scrape_weather_advisory(),
        "cyclone_advisory": scrape_cyclone_advisory(),
        "farm_forecast":    scrape_farm_forecast(location),
        "impact_assessment":scrape_impact_assessment(location),
    }