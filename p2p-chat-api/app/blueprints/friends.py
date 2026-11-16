from flask import Blueprint, g, request

from app.db import get_db
from app.extensions import limiter
from app.security import require_auth
from app.utils import are_friends, canonical_pair, fail, is_blocked, now_iso, ok, user_dict

friends_bp = Blueprint("friends", __name__, url_prefix="/api/friends")


@friends_bp.post("/request")
@require_auth
@limiter.limit("20 per hour")
def send_friend_request():
    body = request.get_json(silent=True) or {}
    target_id = body.get("user_id")
    if not isinstance(target_id, int) or isinstance(target_id, bool):
        return fail("Valid integer user_id is required", 400)

    me = int(g.current_user["id"])
    if target_id == me:
        return fail("Cannot send friend request to yourself", 400)

    db = get_db()
    target = db.fetchone("SELECT id FROM users WHERE id = ?", (target_id,))
    if target is None:
        return fail("User not found", 404)

    if is_blocked(db, me, target_id):
        return fail("Action not allowed due to block status", 403)

    if are_friends(db, me, target_id):
        return fail("Users are already friends", 409)

    now = now_iso()

    # Auto-accept if peer already sent an incoming pending request
    reverse_req = db.fetchone(
        """
        SELECT id FROM friend_requests
        WHERE sender_id = ? AND receiver_id = ? AND status = 'pending'
        """,
        (target_id, me),
    )
    if reverse_req is not None:
        u1, u2 = canonical_pair(me, target_id)
        db.execute(
            "UPDATE friend_requests SET status = 'accepted', updated_at = ? WHERE id = ?",
            (now, reverse_req["id"]),
        )
        db.execute(db.insert_ignore_sql("friendships", ["user1", "user2", "created_at"]), (u1, u2, now))
        db.commit()
        return ok({"status": "accepted", "auto_accepted": True, "request_id": int(reverse_req["id"])}, 200)

    existing = db.fetchone(
        "SELECT id, status FROM friend_requests WHERE sender_id = ? AND receiver_id = ?",
        (me, target_id),
    )
    if existing is not None:
        if existing["status"] == "pending":
            return fail("Friend request already pending", 409)
        db.execute(
            "UPDATE friend_requests SET status = 'pending', updated_at = ? WHERE id = ?",
            (now, existing["id"]),
            commit=True,
        )
        return ok({"status": "pending", "auto_accepted": False, "request_id": int(existing["id"])}, 201)

    req_id = db.insert_returning_id(
        """
        INSERT INTO friend_requests (sender_id, receiver_id, status, created_at, updated_at)
        VALUES (?, ?, 'pending', ?, ?)
        """,
        (me, target_id, now, now),
        commit=True,
    )
    return ok({"status": "pending", "auto_accepted": False, "request_id": req_id}, 201)


@friends_bp.get("/requests/incoming")
@require_auth
def list_incoming_requests():
    me = int(g.current_user["id"])
    db = get_db()
    rows = db.fetchall(
        """
        SELECT fr.id AS request_id, fr.created_at AS requested_at, u.*
        FROM friend_requests fr
        JOIN users u ON u.id = fr.sender_id
        WHERE fr.receiver_id = ? AND fr.status = 'pending'
        ORDER BY fr.id DESC
        """,
        (me,),
    )
    requests_list = [
        {
            "request_id": int(r["request_id"]),
            "created_at": r["requested_at"],
            "sender": user_dict(r),
        }
        for r in rows
    ]
    return ok({"requests": requests_list}, 200)


@friends_bp.get("/requests/sent")
@require_auth
def list_sent_requests():
    me = int(g.current_user["id"])
    db = get_db()
    rows = db.fetchall(
        """
        SELECT fr.id AS request_id, fr.created_at AS requested_at, u.*
        FROM friend_requests fr
        JOIN users u ON u.id = fr.receiver_id
        WHERE fr.sender_id = ? AND fr.status = 'pending'
        ORDER BY fr.id DESC
        """,
        (me,),
    )
    requests_list = [
        {
            "request_id": int(r["request_id"]),
            "created_at": r["requested_at"],
            "receiver": user_dict(r),
        }
        for r in rows
    ]
    return ok({"requests": requests_list}, 200)


@friends_bp.post("/respond")
@require_auth
def respond_friend_request():
    body = request.get_json(silent=True) or {}
    request_id = body.get("request_id")
    action = (body.get("action") or "").strip().lower()

    if not isinstance(request_id, int) or isinstance(request_id, bool):
        return fail("Valid integer request_id is required", 400)
    if action not in {"accept", "reject"}:
        return fail("Action must be 'accept' or 'reject'", 400)

    me = int(g.current_user["id"])
    db = get_db()
    fr = db.fetchone("SELECT * FROM friend_requests WHERE id = ?", (request_id,))
    if fr is None or int(fr["receiver_id"]) != me:
        return fail("Friend request not found", 404)
    if fr["status"] != "pending":
        return fail("Friend request is no longer pending", 409)

    sender_id = int(fr["sender_id"])
    if is_blocked(db, me, sender_id):
        return fail("Action not allowed due to block status", 403)

    now = now_iso()
    new_status = "accepted" if action == "accept" else "rejected"
    db.execute(
        "UPDATE friend_requests SET status = ?, updated_at = ? WHERE id = ?",
        (new_status, now, request_id),
    )
    if action == "accept":
        u1, u2 = canonical_pair(me, sender_id)
        db.execute(db.insert_ignore_sql("friendships", ["user1", "user2", "created_at"]), (u1, u2, now))
    db.commit()
    return ok({"request_id": request_id, "status": new_status}, 200)


@friends_bp.post("/cancel")
@require_auth
def cancel_friend_request():
    body = request.get_json(silent=True) or {}
    request_id = body.get("request_id")
    if not isinstance(request_id, int) or isinstance(request_id, bool):
        return fail("Valid integer request_id is required", 400)

    me = int(g.current_user["id"])
    db = get_db()
    fr = db.fetchone("SELECT * FROM friend_requests WHERE id = ?", (request_id,))
    if fr is None or int(fr["sender_id"]) != me:
        return fail("Friend request not found", 404)
    if fr["status"] != "pending":
        return fail("Friend request is no longer pending", 409)

    db.execute(
        "UPDATE friend_requests SET status = 'cancelled', updated_at = ? WHERE id = ?",
        (now_iso(), request_id),
        commit=True,
    )
    return ok({"request_id": request_id, "status": "cancelled"}, 200)


@friends_bp.get("")
@require_auth
def list_friends():
    me = int(g.current_user["id"])
    db = get_db()
    rows = db.fetchall(
        """
        SELECT u.*
        FROM friendships f
        JOIN users u ON u.id = CASE WHEN f.user1 = ? THEN f.user2 ELSE f.user1 END
        WHERE f.user1 = ? OR f.user2 = ?
        ORDER BY u.username ASC
        """,
        (me, me, me),
    )
    return ok({"friends": [user_dict(r) for r in rows]}, 200)


@friends_bp.delete("/<int:peer_id>")
@require_auth
def remove_friend(peer_id: int):
    me = int(g.current_user["id"])
    if peer_id == me:
        return fail("Invalid peer id", 400)

    u1, u2 = canonical_pair(me, peer_id)
    db = get_db()
    existing = db.fetchone("SELECT 1 FROM friendships WHERE user1 = ? AND user2 = ?", (u1, u2))
    if existing is None:
        return fail("Friendship not found", 404)

    db.execute("DELETE FROM friendships WHERE user1 = ? AND user2 = ?", (u1, u2))
    db.execute(
        """
        DELETE FROM friend_requests
        WHERE (sender_id = ? AND receiver_id = ?)
           OR (sender_id = ? AND receiver_id = ?)
        """,
        (me, peer_id, peer_id, me),
    )
    db.commit()
    return ok({"removed": True, "peer_id": peer_id}, 200)
