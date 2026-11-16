def test_cannot_message_non_friend(client, auth_user, peer_user):
    res = client.post(
        "/api/messages",
        json={"receiver_id": peer_user["id"], "type": "text", "content": "hi"},
        headers=auth_user["headers"],
    )
    assert res.status_code == 403

    fetch = client.get(f"/api/messages/{peer_user['id']}", headers=auth_user["headers"])
    assert fetch.status_code == 403


def test_message_validation_pagination_and_read_receipts(client, auth_user, peer_user, make_friends):
    make_friends(auth_user, peer_user)

    bad_type = client.post(
        "/api/messages",
        json={"receiver_id": peer_user["id"], "type": "exe", "content": "bad"},
        headers=auth_user["headers"],
    )
    assert bad_type.status_code == 400

    too_long = client.post(
        "/api/messages",
        json={"receiver_id": peer_user["id"], "type": "text", "content": "x" * 8001},
        headers=auth_user["headers"],
    )
    assert too_long.status_code == 400

    too_big_media = client.post(
        "/api/messages",
        json={
            "receiver_id": peer_user["id"],
            "type": "video",
            "content": "video.mp4",
            "media_size": 101 * 1024 * 1024,
        },
        headers=auth_user["headers"],
    )
    assert too_big_media.status_code == 400

    sent_ids = []
    for idx in range(5):
        res = client.post(
            "/api/messages",
            json={"receiver_id": peer_user["id"], "type": "text", "content": f"msg-{idx}"},
            headers=auth_user["headers"],
        )
        assert res.status_code == 201
        assert res.get_json()["message"]["read_at"] is None
        sent_ids.append(res.get_json()["message"]["id"])

    page1 = client.get(
        f"/api/messages/{auth_user['id']}?after_id=0&limit=2",
        headers=peer_user["headers"],
    )
    assert page1.status_code == 200
    body1 = page1.get_json()
    assert [m["content"] for m in body1["messages"]] == ["msg-0", "msg-1"]
    assert all(m["read_at"] is not None for m in body1["messages"])

    page2 = client.get(
        f"/api/messages/{auth_user['id']}?after_id={body1['next_after_id']}&limit=10",
        headers=peer_user["headers"],
    )
    assert page2.status_code == 200
    assert [m["content"] for m in page2.get_json()["messages"]] == ["msg-2", "msg-3", "msg-4"]

    # Sender now observes read_at populated on sent messages
    sender_view = client.get(f"/api/messages/{peer_user['id']}?after_id=0&limit=10", headers=auth_user["headers"])
    assert all(m["read_at"] is not None for m in sender_view.get_json()["messages"])
