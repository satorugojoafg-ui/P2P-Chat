import pytest
from app import create_app
from app.db import get_db


def test_production_requires_secret_key(tmp_path, monkeypatch):
    monkeypatch.delenv("P2P_SECRET_KEY", raising=False)
    with pytest.raises(RuntimeError, match="P2P_SECRET_KEY"):
        create_app(
            {
                "ENV": "production",
                "IS_PRODUCTION": True,
                "SECRET_KEY": "",
                "P2P_DB_PATH": str(tmp_path / "prod.db"),
                "P2P_UPLOAD_DIR": str(tmp_path / "prod_up"),
            }
        )


def test_security_headers_and_health(client):
    res = client.get("/api/health")
    assert res.status_code == 200
    body = res.get_json()
    assert body["success"] is True
    assert body["status"] == "ok"
    assert body["db"] == "ok"
    assert res.headers["X-Content-Type-Options"] == "nosniff"
    assert res.headers["X-Frame-Options"] == "DENY"
    assert res.headers["Referrer-Policy"] == "no-referrer"
    assert "default-src 'none'" in res.headers["Content-Security-Policy"]


def test_register_validation_and_duplicates(client):
    bad_user = client.post("/api/register", json={"username": "Bad-User!", "password": "StrongPassword1"})
    assert bad_user.status_code == 400
    assert bad_user.get_json()["success"] is False

    short_user = client.post("/api/register", json={"username": "ab", "password": "StrongPassword1"})
    assert short_user.status_code == 400

    weak_pw = client.post("/api/register", json={"username": "valid_user", "password": "short"})
    assert weak_pw.status_code == 400

    ok_res = client.post("/api/register", json={"username": "valid_user", "password": "StrongPassword1"})
    assert ok_res.status_code == 201
    assert ok_res.get_json()["token"]

    dup_res = client.post("/api/register", json={"username": "valid_user", "password": "StrongPassword1"})
    assert dup_res.status_code == 409


def test_password_stored_as_scrypt_and_login_timing_safe(app, client, register_user):
    user = register_user("carol_03", password="VeryStrongPassword123!")

    with app.app_context():
        row = get_db().fetchone("SELECT password_hash FROM users WHERE id = ?", (user["id"],))
        assert row["password_hash"].startswith("scrypt:")

    good = client.post("/api/login", json={"username": "carol_03", "password": "VeryStrongPassword123!"})
    assert good.status_code == 200
    assert good.get_json()["token"]

    wrong_pw = client.post("/api/login", json={"username": "carol_03", "password": "WrongPassword999"})
    unknown_user = client.post("/api/login", json={"username": "no_such_user", "password": "WrongPassword999"})
    assert wrong_pw.status_code == 401
    assert unknown_user.status_code == 401
    assert wrong_pw.get_json()["error"] == unknown_user.get_json()["error"]


def test_logout_and_password_change_revoke_tokens(client, auth_user):
    me_res = client.get("/api/me", headers=auth_user["headers"])
    assert me_res.status_code == 200
    assert me_res.get_json()["user"]["username"] == "alice_01"

    logout_res = client.post("/api/logout", headers=auth_user["headers"])
    assert logout_res.status_code == 200

    revoked_me = client.get("/api/me", headers=auth_user["headers"])
    assert revoked_me.status_code == 401

    login2 = client.post("/api/login", json={"username": "alice_01", "password": "StrongPass123!"})
    assert login2.status_code == 200
    headers2 = {"Authorization": f"Bearer {login2.get_json()['token']}"}

    bad_pw = client.post(
        "/api/password",
        json={"old_password": "IncorrectOldPassword", "new_password": "NewStrongPassword456!"},
        headers=headers2,
    )
    assert bad_pw.status_code == 401

    ok_pw = client.post(
        "/api/password",
        json={"old_password": "StrongPass123!", "new_password": "NewStrongPassword456!"},
        headers=headers2,
    )
    assert ok_pw.status_code == 200

    assert client.get("/api/me", headers=headers2).status_code == 401

    login3 = client.post("/api/login", json={"username": "alice_01", "password": "NewStrongPassword456!"})
    assert login3.status_code == 200
