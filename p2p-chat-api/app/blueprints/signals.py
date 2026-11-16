import json
from flask import Blueprint, current_app, g, request

from app.db import get_db
from app.security import require_auth
from app.utils import are_friends, fail, future_iso, is_blocked, now_iso, ok

signals_bp = Blueprint("signals", __name__, url_prefix="/api/signals")

ALLOWED_SIGNAL_TYPES = {"offer", "answer", "ice"}


@signals_bp.post("")
@require_auth
def send_signal():
    body = request.get_json(silent=True) or {}
    to_user_id = body.get("to_user_id")
    if not isinstance(to_user_id, int) or isinstance(to_user_id, bool):
        return fail("Valid integer to_user_id is required", 400)

    me = int(g.current_user["id"])
    if to_user_id == me:
        return fail("Cannot signal yourself", 400)

    sig_type = (body.get("type") or "").strip().lower()
    if sig_type not in ALLOWED_SIGNAL_TYPES:
        return fail("Signal type must be one of: offer, answer, ice", 400)

    raw_payload = body.get("payload")
    if isinstance(raw_payload, (dict, list)):
        serialized_payload = json.dumps(raw_payload, separators=(",", ":"))
    elif isinstance(raw_payload, str) and raw_payload.strip():
        try:
            parsed = json.loads(raw_payload)
        except json.JSONDecodeError:
            return fail("Signal payload must be valid structured JSON", 400)
        if not isinstance(parsed, (dict, list)):
            return fail("Signal payload must be a JSON object or array", 400)
        serialized_payload = json.dumps(parsed, separators=(",", ":"))
    else:
        return fail("Structured JSON payload is required", 400)

    if len(serialized_payload.encode("utf-8")) > current_app.config["MAX_SIGNAL_PAYLOAD_BYTES"]:
        return fail("Signal payload exceeds 20 KB limit", 400)

    db = get_db()
    target = db.fetchone("SELECT id FROM users WHERE id = ?", (to_user_id,))
    if target is None:
        return fail("Target user not found", 404)

    if is_blocked(db, me, to_user_id):
        return fail("Cannot send signal to blocked user", 403)

    if not are_friends(db, me, to_user_id):
        return fail("Signals can only be exchanged between friends", 403)

    created_at = now_iso()
    expires_at = future_iso(int(current_app.config["SIGNAL_TTL_SECONDS"]))
    sig_id = db.insert_returning_id(
        """
        INSERT INTO signals (sender_id, receiver_id, type, payload, created_at, expires_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (me, to_user_id, sig_type, serialized_payload, created_at, expires_at),
        commit=True,
    )
    return ok({"signal_id": sig_id, "expires_at": expires_at}, 201)


@signals_bp.get("")
@require_auth
def poll_signals():
    me = int(g.current_user["id"])
    now = now_iso()
    db = get_db()

    if db.backend == "sqlite":
        db.execute("BEGIN IMMEDIATE")
    # Purge expired signals (older than 5 min TTL)
    db.execute("DELETE FROM signals WHERE expires_at <= ?", (now,))

    rows = db.fetchall(
        """
        SELECT s.*
        FROM signals s
        WHERE s.receiver_id = ?
          AND s.expires_at > ?
          AND s.sender_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?)
          AND s.sender_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)
        ORDER BY s.id ASC
        LIMIT 100
        """,
        (me, now, me, me),
    )

    if rows:
        ids = [int(r["id"]) for r in rows]
        placeholders = ", ".join("?" for _ in ids)
        db.execute(f"DELETE FROM signals WHERE id IN ({placeholders})", ids)
    db.commit()

    signals_out = []
    for row in rows:
        try:
            payload_obj = json.loads(row["payload"])
        except json.JSONDecodeError:
            payload_obj = row["payload"]
        signals_out.append(
            {
                "id": int(row["id"]),
                "sender_id": int(row["sender_id"]),
                "receiver_id": int(row["receiver_id"]),
                "type": row["type"],
                "payload": payload_obj,
                "created_at": row["created_at"],
                "expires_at": row["expires_at"],
            }
        )

    return ok({"signals": signals_out}, 200)
