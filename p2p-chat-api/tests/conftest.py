import pytest
from app import create_app


@pytest.fixture
def app(tmp_path):
    db_file = tmp_path / "test_p2p.db"
    upload_dir = tmp_path / "uploads"
    application = create_app(
        {
            "ENV": "testing",
            "TESTING": True,
            "SECRET_KEY": "pytest-fixed-secret-key",
            "P2P_DB_PATH": str(db_file),
            "P2P_UPLOAD_DIR": str(upload_dir),
            "DATABASE_URL": "",
            "RATELIMIT_ENABLED": False,
        }
    )
    return application


@pytest.fixture
def rate_limited_app(tmp_path):
    db_file = tmp_path / "test_rl_p2p.db"
    upload_dir = tmp_path / "uploads_rl"
    application = create_app(
        {
            "ENV": "testing",
            "TESTING": True,
            "SECRET_KEY": "pytest-fixed-secret-key",
            "P2P_DB_PATH": str(db_file),
            "P2P_UPLOAD_DIR": str(upload_dir),
            "DATABASE_URL": "",
            "RATELIMIT_ENABLED": True,
            "RATELIMIT_STORAGE_URI": "memory://",
        }
    )
    return application


@pytest.fixture
def register_user(client):
    def _register(username: str, password: str = "StrongPass123!", display_name: str | None = None):
        payload = {"username": username, "password": password}
        if display_name is not None:
            payload["display_name"] = display_name
        res = client.post("/api/register", json=payload)
        assert res.status_code == 201, res.get_json()
        body = res.get_json()
        return {
            "id": body["user"]["id"],
            "username": body["user"]["username"],
            "token": body["token"],
            "headers": {"Authorization": f"Bearer {body['token']}"},
            "user": body["user"],
        }

    return _register


@pytest.fixture
def auth_user(register_user):
    return register_user("alice_01", display_name="Alice")


@pytest.fixture
def peer_user(register_user):
    return register_user("bob_02", display_name="Bob")


@pytest.fixture
def make_friends(client):
    def _make_friends(user_a: dict, user_b: dict):
        r1 = client.post(
            "/api/friends/request",
            json={"user_id": user_b["id"]},
            headers=user_a["headers"],
        )
        assert r1.status_code in (200, 201), r1.get_json()
        req_id = r1.get_json()["request_id"]
        if r1.get_json().get("status") != "accepted":
            r2 = client.post(
                "/api/friends/respond",
                json={"request_id": req_id, "action": "accept"},
                headers=user_b["headers"],
            )
            assert r2.status_code == 200, r2.get_json()

    return _make_friends
