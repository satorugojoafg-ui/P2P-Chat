def test_full_friend_request_accept_list_and_remove(client, auth_user, peer_user):
    send = client.post("/api/friends/request", json={"user_id": peer_user["id"]}, headers=auth_user["headers"])
    assert send.status_code == 201
    req_id = send.get_json()["request_id"]

    sent = client.get("/api/friends/requests/sent", headers=auth_user["headers"])
    assert sent.status_code == 200
    assert len(sent.get_json()["requests"]) == 1

    incoming = client.get("/api/friends/requests/incoming", headers=peer_user["headers"])
    assert incoming.status_code == 200
    assert incoming.get_json()["requests"][0]["sender"]["username"] == "alice_01"

    accept = client.post(
        "/api/friends/respond",
        json={"request_id": req_id, "action": "accept"},
        headers=peer_user["headers"],
    )
    assert accept.status_code == 200
    assert accept.get_json()["status"] == "accepted"

    f1 = client.get("/api/friends", headers=auth_user["headers"]).get_json()["friends"]
    f2 = client.get("/api/friends", headers=peer_user["headers"]).get_json()["friends"]
    assert [u["username"] for u in f1] == ["bob_02"]
    assert [u["username"] for u in f2] == ["alice_01"]

    remove = client.delete(f"/api/friends/{peer_user['id']}", headers=auth_user["headers"])
    assert remove.status_code == 200
    assert client.get("/api/friends", headers=auth_user["headers"]).get_json()["friends"] == []


def test_cross_request_auto_accepts(client, auth_user, peer_user):
    r1 = client.post("/api/friends/request", json={"user_id": peer_user["id"]}, headers=auth_user["headers"])
    assert r1.status_code == 201

    r2 = client.post("/api/friends/request", json={"user_id": auth_user["id"]}, headers=peer_user["headers"])
    assert r2.status_code == 200
    body = r2.get_json()
    assert body["status"] == "accepted"
    assert body["auto_accepted"] is True

    friends = client.get("/api/friends", headers=auth_user["headers"]).get_json()["friends"]
    assert len(friends) == 1
    assert friends[0]["id"] == peer_user["id"]


def test_cancel_and_reject_friend_requests(client, auth_user, peer_user, register_user):
    charlie = register_user("charlie_03")

    r1 = client.post("/api/friends/request", json={"user_id": peer_user["id"]}, headers=auth_user["headers"])
    req1 = r1.get_json()["request_id"]
    cancel = client.post("/api/friends/cancel", json={"request_id": req1}, headers=auth_user["headers"])
    assert cancel.status_code == 200
    assert client.get("/api/friends/requests/incoming", headers=peer_user["headers"]).get_json()["requests"] == []

    r2 = client.post("/api/friends/request", json={"user_id": charlie["id"]}, headers=auth_user["headers"])
    req2 = r2.get_json()["request_id"]
    reject = client.post(
        "/api/friends/respond",
        json={"request_id": req2, "action": "reject"},
        headers=charlie["headers"],
    )
    assert reject.status_code == 200
    assert reject.get_json()["status"] == "rejected"
    assert client.get("/api/friends", headers=auth_user["headers"]).get_json()["friends"] == []
