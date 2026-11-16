from datetime import datetime, timedelta, timezone
from flask import jsonify
from app.db import Database


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def future_iso(seconds: int) -> str:
    return (datetime.now(timezone.utc) + timedelta(seconds=seconds)).isoformat()


def ok(data: dict | None = None, status: int = 200):
    payload = {"success": True}
    if data:
        payload.update(data)
    return jsonify(payload), status


def fail(message: str, status: int = 400):
    return jsonify({"success": False, "error": message}), status


def user_dict(row: dict) -> dict:
    last_seen = row.get("last_seen")
    is_online = False
    if last_seen:
        try:
            dt = datetime.fromisoformat(last_seen)
            is_online = (datetime.now(timezone.utc) - dt) <= timedelta(seconds=90)
        except ValueError:
            is_online = False
    return {
        "id": int(row["id"]),
        "username": row["username"],
        "display_name": row["display_name"],
        "bio": row.get("bio") or "",
        "photo_url": row.get("photo_url"),
        "last_seen": last_seen,
        "is_online": is_online,
    }


def canonical_pair(user_a: int, user_b: int) -> tuple[int, int]:
    return (user_a, user_b) if user_a < user_b else (user_b, user_a)


def is_blocked(db: Database, user_a: int, user_b: int) -> bool:
    row = db.fetchone(
        """
        SELECT 1 AS blocked FROM blocks
        WHERE (blocker_id = ? AND blocked_id = ?)
           OR (blocker_id = ? AND blocked_id = ?)
        LIMIT 1
        """,
        (user_a, user_b, user_b, user_a),
    )
    return row is not None


def are_friends(db: Database, user_a: int, user_b: int) -> bool:
    u1, u2 = canonical_pair(user_a, user_b)
    row = db.fetchone(
        "SELECT 1 AS ok FROM friendships WHERE user1 = ? AND user2 = ? LIMIT 1",
        (u1, u2),
    )
    return row is not None


def parse_cursor_args(after_id_raw: str | None, limit_raw: str | None, default_limit: int = 50, max_limit: int = 100) -> tuple[int, int] | None:
    try:
        after_id = int(after_id_raw) if after_id_raw is not None and after_id_raw != "" else 0
        limit = int(limit_raw) if limit_raw is not None and limit_raw != "" else default_limit
    except ValueError:
        return None
    if after_id < 0 or limit < 1 or limit > max_limit:
        return None
    return after_id, limit
