import io
from PIL import Image


def _make_png_bytes(size=(32, 32)) -> bytes:
    buf = io.BytesIO()
    img = Image.new("RGB", size, color=(120, 40, 220))
    img.save(buf, format="PNG")
    return buf.getvalue()


def test_profile_update_and_photo_upload_validation(client, auth_user):
    bad_name = client.patch("/api/profile", json={"display_name": "A" * 61}, headers=auth_user["headers"])
    assert bad_name.status_code == 400

    bad_bio = client.patch("/api/profile", json={"bio": "B" * 301}, headers=auth_user["headers"])
    assert bad_bio.status_code == 400

    ok_profile = client.patch(
        "/api/profile",
        json={"display_name": "Alice Nova", "bio": "Encrypted P2P enthusiast"},
        headers=auth_user["headers"],
    )
    assert ok_profile.status_code == 200
    assert ok_profile.get_json()["user"]["display_name"] == "Alice Nova"

    # Reject fake image content even with .png extension
    fake = client.post(
        "/api/profile/photo",
        data={"photo": (io.BytesIO(b"not-a-real-image"), "evil.png")},
        content_type="multipart/form-data",
        headers=auth_user["headers"],
    )
    assert fake.status_code == 400

    # Reject payload exceeding 10 MB
    oversized = client.post(
        "/api/profile/photo",
        data={"photo": (io.BytesIO(b"0" * (10 * 1024 * 1024 + 10)), "huge.png")},
        content_type="multipart/form-data",
        headers=auth_user["headers"],
    )
    assert oversized.status_code == 413

    # Accept genuine PNG and serve with security headers
    valid = client.post(
        "/api/profile/photo",
        data={"photo": (io.BytesIO(_make_png_bytes()), "avatar.png")},
        content_type="multipart/form-data",
        headers=auth_user["headers"],
    )
    assert valid.status_code == 200
    photo_url = valid.get_json()["photo_url"]

    served = client.get(photo_url)
    assert served.status_code == 200
    assert served.headers["X-Content-Type-Options"] == "nosniff"
    assert served.headers["Content-Disposition"].startswith("inline")
    served.close()
