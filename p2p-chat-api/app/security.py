import re
import time
from functools import wraps
from flask import current_app, g, jsonify, request
from itsdangerous import BadSignature, SignatureExpired
from werkzeug.security import check_password_hash, generate_password_hash

from app.db import get_db
from app.extensions import get_token_serializer
from app.utils import now_iso

USERNAME_RE = re.compile(r"^[a-z0-9_]{3,30}$")
_DUMMY_SCRYPT_HASH = generate_password_hash("p2p-chat-timing-constant-dummy-secret", method="scrypt")


def hash_password(password: str) -> str:
    return generate_password_hash(password, method="scrypt")


def verify_password_timing_safe(stored_hash: str | None, candidate_password: str) -> bool:
    if not stored_hash:
        check_password_hash(_DUMMY_SCRYPT_HASH, candidate_password)
        return False
    return check_password_hash(stored_hash, candidate_password)


def validate_username(username: str) -> str | None:
    cleaned = (username or "").strip().lower()
    if not USERNAME_RE.match(cleaned):
        return None
    return cleaned


def validate_password(password: str) -> str | None:
    if not isinstance(password, str):
        return "Password must be a string"
    if len(password) < 10:
        return "Password must be at least 10 characters long"
    if len(password) > 256:
        return "Password exceeds maximum length"
    return None


def issue_token(user_id: int, token_version: int) -> str:
    serializer = get_token_serializer(current_app)
    payload = {
        "uid": int(user_id),
        "tv": int(token_version),
        "iat": int(time.time()),
    }
    return serializer.dumps(payload)


def verify_token(token: str) -> dict | None:
    serializer = get_token_serializer(current_app)
    max_age = int(current_app.config["TOKEN_MAX_AGE_SECONDS"])
    try:
        data = serializer.loads(token, max_age=max_age)
    except (SignatureExpired, BadSignature):
        return None
    if not isinstance(data, dict) or "uid" not in data or "tv" not in data:
        return None
    return data


def require_auth(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            return jsonify({"success": False, "error": "Unauthorized"}), 401

        raw_token = auth_header[7:].strip()
        if not raw_token:
            return jsonify({"success": False, "error": "Unauthorized"}), 401

        payload = verify_token(raw_token)
        if payload is None:
            return jsonify({"success": False, "error": "Invalid or expired token"}), 401

        db = get_db()
        user = db.fetchone("SELECT * FROM users WHERE id = ?", (int(payload["uid"]),))
        if user is None:
            return jsonify({"success": False, "error": "Invalid or expired token"}), 401

        if int(user["token_version"]) != int(payload["tv"]):
            return jsonify({"success": False, "error": "Token has been revoked"}), 401

        db.execute("UPDATE users SET last_seen = ? WHERE id = ?", (now_iso(), user["id"]), commit=True)
        g.current_user = user
        return fn(*args, **kwargs)

    return wrapper
