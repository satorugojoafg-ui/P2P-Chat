from flask import Blueprint, g, request

from app.db import get_db
from app.security import require_auth
from app.utils import canonical_pair, fail, now_iso, ok, user_dict

blocks_bp = Blueprint("blocks", __name__, url_prefix="/api/blocks")


@blocks_bp.post("")
@require_auth
def block_user():
    body = request.get_json(silent=True) or {}
    target_id = body.get("user_id")
    if not isinstance(target_id, int) or isinstance(target_id, bool):
        return fail("Valid integer user_id is required", 400)

    me = int(g.current_user["id"])
    if target_id == me:
        return fail("Cannot block yourself", 400)

    db = get_db()
    target = db.fetchone("SELECT id FROM users WHERE id = ?", (target_id,))
    if target is None:
        return fail("User not found", 404)

    now = now_iso()
    db.execute(db.insert_ignore_sql("blocks", ["blocker_id", "blocked_id", "created_at"]), (me, target_id, now))

    # Remove friendship and any friend requests in both directions
    u1, u2 = canonical_pair(me, target_id)
    db.execute("DELETE FROM friendships WHERE user1 = ? AND user2 = ?", (u1, u2))
    db.execute(
        """
        DELETE FROM friend_requests
        WHERE (sender_id = ? AND receiver_id = ?)
           OR (sender_id = ? AND receiver_id = ?)
        """,
        (me, target_id, target_id, me),
    )
    db.commit()
    return ok({"blocked": True, "user_id": target_id}, 200)


@blocks_bp.delete("/<int:user_id>")
@require_auth
def unblock_user(user_id: int):
    me = int(g.current_user["id"])
    db = get_db()
    existing = db.fetchone(
        "SELECT 1 FROM blocks WHERE blocker_id = ? AND blocked_id = ?",
        (me, user_id),
    )
    if existing is None:
        return fail("Block record not found", 404)

    db.execute(
        "DELETE FROM blocks WHERE blocker_id = ? AND blocked_id = ?",
        (me, user_id),
        commit=True,
    )
    return ok({"blocked": False, "user_id": user_id}, 200)


@blocks_bp.get("")
@require_auth
def list_blocked_users():
    me = int(g.current_user["id"])
    db = get_db()
    rows = db.fetchall(
        """
        SELECT u.*, b.created_at AS blocked_at
        FROM blocks b
        JOIN users u ON u.id = b.blocked_id
        WHERE b.blocker_id = ?
        ORDER BY b.created_at DESC
        """,
        (me,),
    )
    blocked = [
        {
            "user": user_dict(r),
            "blocked_at": r["blocked_at"],
        }
        for r in rows
    ]
    return ok({"blocked": blocked}, 200)
