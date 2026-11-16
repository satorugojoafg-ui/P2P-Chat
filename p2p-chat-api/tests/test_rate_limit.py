def test_login_rate_limit_triggers_429(rate_limited_app):
    client = rate_limited_app.test_client()
    client.post(
        "/api/register",
        json={"username": "rl_user", "password": "StrongPassword123!"},
    )

    statuses = []
    for _ in range(6):
        res = client.post(
            "/api/login",
            json={"username": "rl_user", "password": "WrongPassword123!"},
        )
        statuses.append(res.status_code)

    assert statuses[:5] == [401, 401, 401, 401, 401]
    assert statuses[5] == 429
    assert res.get_json()["success"] is False


def test_register_rate_limit_triggers_429(rate_limited_app):
    client = rate_limited_app.test_client()
    for i in range(3):
        res = client.post(
            "/api/register",
            json={"username": f"rl_reg_{i}", "password": "StrongPassword123!"},
        )
        assert res.status_code == 201

    limited = client.post(
        "/api/register",
        json={"username": "rl_reg_4", "password": "StrongPassword123!"},
    )
    assert limited.status_code == 429
