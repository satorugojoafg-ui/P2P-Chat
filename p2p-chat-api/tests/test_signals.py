from datetime import datetime, timedelta, timezone
from app.db import get_db


def test_signal_validation_one_shot_and_ttl(app, client, auth_user, peer_user, make_friends):
    # Non-friend denied
    denied = client.post(
        "/api/signals",
        json={"to_user_id": peer_user["id"], "type": "offer", "payload": {"sdp": "v=0"}},
        headers=auth_user["headers"],
    )
    assert denied.status_code == 403

    make_friends(auth_user, peer_user)

    # Oversized payload > 20 KB rejected
    huge = client.post(
        "/api/signals",
        json={"to_user_id": peer_user["id"], "type": "offer", "payload": {"sdp": "A" * 21000}},
        headers=auth_user["headers"],
    )
    assert huge.status_code == 400

    # Valid structured JSON signal delivered once and deleted atomically
    created = client.post(
        "/api/signals",
        json={"to_user_id": peer_user["id"], "type": "offer", "payload": {"sdp": "v=0", "type": "offer"}},
        headers=auth_user["headers"],
    )
    assert created.status_code == 201

    poll1 = client.get("/api/signals", headers=peer_user["headers"])
    assert poll1.status_code == 200
    sigs = poll1.get_json()["signals"]
    assert len(sigs) == 1
    assert sigs[0]["payload"]["type"] == "offer"

    poll2 = client.get("/api/signals", headers=peer_user["headers"])
    assert poll2.status_code == 200
    assert poll2.get_json()["signals"] == []

    # Expired signal beyond 5-min TTL is purged and never returned
    sig2 = client.post(
        "/api/signals",
        json={"to_user_id": peer_user["id"], "type": "ice", "payload": {"candidate": "c1"}},
        headers=auth_user["headers"],
    )
    assert sig2.status_code == 201
    sig_id = sig2.get_json()["signal_id"]

    past = (datetime.now(timezone.utc) - timedelta(minutes=6)).isoformat()
    with app.app_context():
        get_db().execute("UPDATE signals SET expires_at = ? WHERE id = ?", (past, sig_id), commit=True)

    poll_expired = client.get("/api/signals", headers=peer_user["headers"])
    assert poll_expired.status_code == 200
    assert poll_expired.get_json()["signals"] == []
