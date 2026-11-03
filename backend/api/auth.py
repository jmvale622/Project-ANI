import hashlib
import hmac
import secrets
from datetime import datetime, timedelta, timezone

from fastapi import Depends, HTTPException, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from couchbase.exceptions import DocumentNotFoundException

PBKDF2_ITERATIONS = 600_000
SESSION_LIFETIME = timedelta(days=30)
bearer_scheme = HTTPBearer(auto_error=False)


def account_document_id(contact: str) -> str:
    normalized_contact = contact.strip().casefold()
    contact_hash = hashlib.sha256(normalized_contact.encode("utf-8")).hexdigest()
    return f"account::{contact_hash}"


def hash_password(password: str, salt: bytes | None = None) -> tuple[str, str]:
    salt = salt or secrets.token_bytes(16)
    password_hash = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), salt, PBKDF2_ITERATIONS
    )
    return salt.hex(), password_hash.hex()


def verify_password(password: str, salt_hex: str, expected_hash: str) -> bool:
    salt = bytes.fromhex(salt_hex)
    _, actual_hash = hash_password(password, salt)
    return hmac.compare_digest(actual_hash, expected_hash)


def create_session(collection, user_id: str) -> str:
    token = secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(token.encode("ascii")).hexdigest()
    expires_at = datetime.now(timezone.utc) + SESSION_LIFETIME
    collection.upsert(
        f"session::{token_hash}",
        {
            "type": "session",
            "user_id": user_id,
            "expires_at": expires_at.isoformat(),
        },
    )
    return token


def get_current_account(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
) -> dict:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=401,
            detail="Sign in is required.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if not credentials.credentials.isascii():
        raise HTTPException(
            status_code=401,
            detail="Session is invalid or has expired.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    token_hash = hashlib.sha256(credentials.credentials.encode("ascii")).hexdigest()
    collection = request.app.state.collection
    try:
        session = collection.get(f"session::{token_hash}").content_as[dict]
    except DocumentNotFoundException as exc:
        raise HTTPException(
            status_code=401,
            detail="Session is invalid or has expired.",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Account storage is unavailable.") from exc

    expires_at = datetime.fromisoformat(session["expires_at"])
    if expires_at <= datetime.now(timezone.utc):
        collection.remove(f"session::{token_hash}")
        raise HTTPException(
            status_code=401,
            detail="Session is invalid or has expired.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    try:
        return collection.get(f"user::{session['user_id']}").content_as[dict]
    except DocumentNotFoundException as exc:
        raise HTTPException(
            status_code=401,
            detail="Account no longer exists.",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Account storage is unavailable.") from exc
