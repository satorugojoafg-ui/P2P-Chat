def test_block_removes_friendship_and_blocks_communications(client, auth_user, peer_user, make_friends):
    make_friends(auth_user, peer_user)

    block = client.post("/api/blocks", json={"user_id": peer_user["id"]}, headers=auth_user["headers"])
    assert block.status_code == 200

    assert client.get("/api/friends", headers=auth_user["headers"]).get_json()["friends"] == []
    assert client.get("/api/friends", headers=peer_user["headers"]).get_json()["friends"] == []

    blocked_list = client.get("/api/blocks", headers=auth_user["headers"]).get_json()["blocked"]
    assert [item["user"]["id"] for item in blocked_list] == [peer_user["id"]]

    # Search hides blocked relationships in both directions
    search_a = client.get("/api/users?q=bob", headers=auth_user["headers"]).get_json()["users"]
    search_b = client.get("/api/users?q=alice", headers=peer_user["headers"]).get_json()["users"]
    assert search_a == []
    assert search_b == []

    # Friend requests, messages, and signals all fail with 403
    req = client.post("/api/friends/request", json={"user_id": auth_user["id"]}, headers=peer_user["headers"])
    assert req.status_code == 403

    msg = client.post(
        "/api/messages",
        json={"receiver_id": auth_user["id"], "type": "text", "content": "hello"},
        headers=peer_user["headers"],
    )
    assert msg.status_code == 403

    sig = client.post(
        "/api/signals",
        json={"to_user_id": auth_user["id"], "type": "offer", "payload": {"sdp": "v=0"}},
        headers=peer_user["headers"],
    )
    assert sig.status_code == 403

    unblock = client.delete(f"/api/blocks/{peer_user['id']}", headers=auth_user["headers"])
    assert unblock.status_code == 200
    assert client.get("/api/blocks", headers=auth_user["headers"]).get_json()["blocked"] == []
