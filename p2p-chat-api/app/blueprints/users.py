import glob
import io
import os
import secrets
from flask import Blueprint, current_app, g, request, send_file
from PIL import Image, UnidentifiedImageError

from app.db import get_db
from app.extensions import limiter
from app.security import require_auth
from app.utils import fail, ok, user_dict

users_bp = Blueprint("users", __name__, url_prefix="/api")

PIL_FORMAT_TO_EXT = {
    "JPEG": ("jpg", "image/jpeg"),
    "PNG": ("png", "image/png"),
    "WEBP": ("webp", "image/webp"),
}


@users_bp.get("/health")
def health():
    db_status = "ok"
    status_code = 200
    try:
        db = get_db()
        db.fetchone("SELECT 1 AS alive")
    except Exception:
        db_status = "error"
        status_code = 503

    return ok(
        {
            "status": "ok" if db_status == "ok" else "degraded",
            "db": db_status,
            "version": current_app.config["APP_VERSION"],
        },
        status_code,
    )


@users_bp.patch("/profile")
@require_auth
def update_profile():
    body = request.get_json(silent=True) or {}

    display_name = body.get("display_name")
    if display_name is not None:
        display_name = str(display_name).strip()
        if not display_name or len(display_name) > 60:
            return fail("Display name must be between 1 and 60 characters", 400)
    else:
        display_name = g.current_user["display_name"]

    bio = body.get("bio")
    if bio is not None:
        bio = str(bio).strip()
        if len(bio) > 300:
            return fail("Bio must be 300 characters or fewer", 400)
    else:
        bio = g.current_user["bio"]

    db = get_db()
    db.execute(
        "UPDATE users SET display_name = ?, bio = ? WHERE id = ?",
        (display_name, bio, g.current_user["id"]),
        commit=True,
    )
    updated = db.fetchone("SELECT * FROM users WHERE id = ?", (g.current_user["id"],))
    return ok({"user": user_dict(updated)}, 200)


@users_bp.post("/profile/photo")
@require_auth
@limiter.limit("10 per hour")
def upload_profile_photo():
    photo = request.files.get("photo")
    if photo is None or not photo.filename:
        return fail("Photo file is required", 400)

    raw_bytes = photo.read()
    if not raw_bytes:
        return fail("Uploaded photo is empty", 400)

    if len(raw_bytes) > current_app.config["MAX_CONTENT_LENGTH"]:
        return fail("Photo exceeds 10 MB limit", 413)

    try:
        with Image.open(io.BytesIO(raw_bytes)) as img:
            img_format = (img.format or "").upper()
            img.verify()
    except (UnidentifiedImageError, OSError, SyntaxError):
        return fail("Invalid or corrupted image file", 400)

    if img_format not in PIL_FORMAT_TO_EXT:
        return fail("Only JPEG, PNG, and WEBP images are supported", 400)

    ext, _mime = PIL_FORMAT_TO_EXT[img_format]
    upload_dir = current_app.config["P2P_UPLOAD_DIR"]
    os.makedirs(upload_dir, exist_ok=True)

    uid = int(g.current_user["id"])
    for old_path in glob.glob(os.path.join(upload_dir, f"{uid}_*")):
        try:
            os.remove(old_path)
        except OSError:
            pass

    filename = f"{uid}_{secrets.token_hex(12)}.{ext}"
    dest_path = os.path.join(upload_dir, filename)
    with open(dest_path, "wb") as handle:
        handle.write(raw_bytes)

    photo_url = f"/api/users/{uid}/photo"
    db = get_db()
    db.execute("UPDATE users SET photo_url = ? WHERE id = ?", (photo_url, uid), commit=True)
    updated = db.fetchone("SELECT * FROM users WHERE id = ?", (uid,))
    return ok({"photo_url": photo_url, "user": user_dict(updated)}, 200)


@users_bp.get("/users/<int:user_id>/photo")
def get_user_photo(user_id: int):
    db = get_db()
    user = db.fetchone("SELECT id, photo_url FROM users WHERE id = ?", (user_id,))
    if user is None or not user["photo_url"]:
        return fail("Photo not found", 404)

    upload_dir = current_app.config["P2P_UPLOAD_DIR"]
    matches = sorted(glob.glob(os.path.join(upload_dir, f"{user_id}_*")), reverse=True)
    if not matches:
        return fail("Photo not found", 404)

    safe_file = os.path.basename(matches[0])
    full_path = os.path.join(upload_dir, safe_file)
    ext = safe_file.rsplit(".", 1)[-1].lower()
    mime_map = {"jpg": "image/jpeg", "png": "image/png", "webp": "image/webp"}
    mimetype = mime_map.get(ext, "application/octet-stream")

    response = send_file(full_path, mimetype=mimetype, as_attachment=False)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["Content-Disposition"] = f'inline; filename="{safe_file}"'
    return response


@users_bp.get("/users")
@require_auth
@limiter.limit("60 per minute")
def search_users():
    q = (request.args.get("q") or "").strip().lower()
    like_pattern = f"%{q}%"
    uid = int(g.current_user["id"])

    db = get_db()
    rows = db.fetchall(
        """
        SELECT * FROM users
        WHERE id <> ?
          AND (LOWER(username) LIKE ? OR LOWER(display_name) LIKE ?)
          AND id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?)
          AND id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)
        ORDER BY username ASC
        LIMIT 30
        """,
        (uid, like_pattern, like_pattern, uid, uid),
    )
    return ok({"users": [user_dict(r) for r in rows]}, 200)
