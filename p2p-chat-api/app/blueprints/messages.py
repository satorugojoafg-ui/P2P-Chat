from flask import Blueprint, current_app, g, request

from app.db import get_db
from app.extensions import limiter
from app.security import require_auth
from app.utils import are_friends, fail, is_blocked, now_iso, ok, parse_cursor_args

messages_bp = Blueprint("messages", __name__, url_prefix="/api/messages")

ALLOWED_MESSAGE_TYPES = {"text", "audio", "image", "video", "file"}


def _serialize_message(row: dict) -> dict:
    return {
        "id": int(row["id"]),
        "sender_id": int(row["sender_id"]),
        "receiver_id": int(row["receiver_id"]),
        "type": row["type"],
        "content": row["content"],
        "media_size": int(row["media_size"]),
        "created_at": row["created_at"],
        "read_at": row["read_at"],
    }


@messages_bp.post("")
@require_auth
@limiter.limit("120 per minute")
def send_message():
    body = request.get_json(silent=True) or {}
    receiver_id = body.get("receiver_id")
    if not isinstance(receiver_id, int) or isinstance(receiver_id, bool):
        return fail("Valid integer receiver_id is required", 400)

    me = int(g.current_user["id"])
    if receiver_id == me:
        return fail("Cannot send messages to yourself", 400)

    msg_type = (body.get("type") or "text").strip().lower()
    if msg_type not in ALLOWED_MESSAGE_TYPES:
        return fail("Unsupported message type", 400)

    content = body.get("content")
    if not isinstance(content, str) or not content.strip():
        return fail("Message content is required", 400)
    if len(content) > current_app.config["MAX_MESSAGE_LENGTH"]:
        return fail("Message content exceeds 8000 characters", 400)

    media_size = body.get("media_size", 0)
    if not isinstance(media_size, int) or isinstance(media_size, bool) or media_size < 0:
        return fail("Invalid media_size", 400)
    if media_size > current_app.config["MAX_MEDIA_SIZE_BYTES"]:
        return fail("Media size exceeds 100 MB limit", 400)

    db = get_db()
    receiver = db.fetchone("SELECT id FROM users WHERE id = ?", (receiver_id,))
    if receiver is None:
        return fail("Recipient not found", 404)

    if is_blocked(db, me, receiver_id):
        return fail("Cannot message blocked user", 403)

    if not are_friends(db, me, receiver_id):
        return fail("Messages can only be sent to friends", 403)

    now = now_iso()
    msg_id = db.insert_returning_id(
        """
        INSERT INTO messages (sender_id, receiver_id, type, content, media_size, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (me, receiver_id, msg_type, content, media_size, now),
        commit=True,
    )
    created = db.fetchone("SELECT * FROM messages WHERE id = ?", (msg_id,))
    return ok({"message": _serialize_message(created)}, 201)


@messages_bp.get("/<int:peer_id>")
@require_auth
def fetch_messages(peer_id: int):
    me = int(g.current_user["id"])
    if peer_id == me:
        return fail("Invalid peer_id", 400)

    cursor = parse_cursor_args(request.args.get("after_id"), request.args.get("limit"), default_limit=50, max_limit=100)
    if cursor is None:
        return fail("Invalid pagination parameters (after_id >= 0, 1 <= limit <= 100)", 400)
    after_id, limit = cursor

    db = get_db()
    peer = db.fetchone("SELECT id FROM users WHERE id = ?", (peer_id,))
    if peer is None:
        return fail("Peer user not found", 404)

    if is_blocked(db, me, peer_id):
        return fail("Access denied due to block status", 403)

    if not are_friends(db, me, peer_id):
        return fail("Conversation is only available between friends", 403)

    # Mark incoming messages from peer as read before fetching
    read_timestamp = now_iso()
    db.execute(
        """
        UPDATE messages
        SET read_at = ?
        WHERE sender_id = ? AND receiver_id = ? AND read_at IS NULL
        """,
        (read_timestamp, peer_id, me),
        commit=True,
    )

    rows = db.fetchall(
        """
        SELECT * FROM messages
        WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?))
          AND id > ?
        ORDER BY id ASC
        LIMIT ?
        """,
        (me, peer_id, peer_id, me, after_id, limit),
    )

    messages_list = [_serialize_message(r) for r in rows]
    next_after_id = messages_list[-1]["id"] if messages_list else after_id
    return ok({"messages": messages_list, "next_after_id": next_after_id}, 200)
