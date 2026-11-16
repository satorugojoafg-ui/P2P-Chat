from flask import Blueprint, g, request

from app.db import get_db
from app.extensions import limiter
from app.security import (
    hash_password,
    issue_token,
    require_auth,
    validate_password,
    validate_username,
    verify_password_timing_safe,
)
from app.utils import fail, now_iso, ok, user_dict

auth_bp = Blueprint("auth", __name__, url_prefix="/api")


@auth_bp.post("/register")
@limiter.limit("3 per 5 minutes")
def register():
    body = request.get_json(silent=True) or {}
    username = validate_username(body.get("username", ""))
    if not username:
        return fail("Username must be 3-30 characters of lowercase letters, numbers, or underscores", 400)

    password = body.get("password", "")
    pw_error = validate_password(password)
    if pw_error:
        return fail(pw_error, 400)

    display_name = (body.get("display_name") or username).strip()
    if not display_name or len(display_name) > 60:
        return fail("Display name must be between 1 and 60 characters", 400)

    db = get_db()
    existing = db.fetchone("SELECT id FROM users WHERE username = ?", (username,))
    if existing is not None:
        return fail("Username already taken", 409)

    now = now_iso()
    pw_hash = hash_password(password)
    user_id = db.insert_returning_id(
        """
        INSERT INTO users (username, display_name, password_hash, bio, token_version, last_seen, created_at)
        VALUES (?, ?, ?, '', 1, ?, ?)
        """,
        (username, display_name, pw_hash, now, now),
        commit=True,
    )
    user = db.fetchone("SELECT * FROM users WHERE id = ?", (user_id,))
    token = issue_token(user_id=user_id, token_version=1)
    return ok({"token": token, "user": user_dict(user)}, 201)


@auth_bp.post("/login")
@limiter.limit("5 per minute")
def login():
    body = request.get_json(silent=True) or {}
    raw_username = (body.get("username") or "").strip().lower()
    password = body.get("password") or ""

    db = get_db()
    user = db.fetchone("SELECT * FROM users WHERE username = ?", (raw_username,))
    stored_hash = user["password_hash"] if user is not None else None
    if not verify_password_timing_safe(stored_hash, password):
        return fail("Invalid username or password", 401)

    now = now_iso()
    db.execute("UPDATE users SET last_seen = ? WHERE id = ?", (now, user["id"]), commit=True)
    user = db.fetchone("SELECT * FROM users WHERE id = ?", (user["id"],))
    token = issue_token(user_id=int(user["id"]), token_version=int(user["token_version"]))
    return ok({"token": token, "user": user_dict(user)}, 200)


@auth_bp.post("/logout")
@require_auth
def logout():
    db = get_db()
    db.execute(
        "UPDATE users SET token_version = token_version + 1 WHERE id = ?",
        (g.current_user["id"],),
        commit=True,
    )
    return ok({"message": "Logged out; all active tokens revoked"}, 200)


@auth_bp.post("/password")
@require_auth
def change_password():
    body = request.get_json(silent=True) or {}
    old_password = body.get("old_password") or body.get("current_password") or ""
    new_password = body.get("new_password") or ""

    if not verify_password_timing_safe(g.current_user["password_hash"], old_password):
        return fail("Current password is incorrect", 401)

    pw_error = validate_password(new_password)
    if pw_error:
        return fail(pw_error, 400)

    new_hash = hash_password(new_password)
    db = get_db()
    db.execute(
        "UPDATE users SET password_hash = ?, token_version = token_version + 1 WHERE id = ?",
        (new_hash, g.current_user["id"]),
        commit=True,
    )
    return ok({"message": "Password changed; please log in again"}, 200)


@auth_bp.get("/me")
@require_auth
def get_me():
    return ok({"user": user_dict(g.current_user)}, 200)
