# P2P Chat REST API

Production-grade, modular Python/Flask REST API for **P2P Chat**, featuring `scrypt` password hashing, `itsdangerous` signed bearer tokens with database-backed `token_version` revocation, Redis-backed multi-worker rate limiting, Pillow MIME-verified profile photo uploads stored outside webroot, mutual friend & block enforcement, cursor-paginated messaging with read receipts, and TTL-scoped atomic one-shot WebRTC signaling.

---

## Architecture Overview

- **App Factory (`app/__init__.py`)**: Environment-aware Flask application factory (`create_app()`) registering extensions, security headers, global JSON error handlers, and modular blueprints.
- **Database Abstraction (`app/db.py`)**: Thin parameterized SQL helper without an ORM. Supports **SQLite WAL** (`PRAGMA foreign_keys = ON`, `journal_mode = WAL`, `synchronous = NORMAL`) by default and **PostgreSQL** via `DATABASE_URL` (`psycopg` v3), translating `?` placeholders to `%s` and `INSERT OR IGNORE` to `ON CONFLICT DO NOTHING`.
- **Security Layer (`app/security.py`)**:
  - `scrypt` password hashing via Werkzeug.
  - Timing-safe login (executes dummy `scrypt` verification when username does not exist).
  - Signed tokens via `URLSafeTimedSerializer` (`uid`, `tv`, `iat`, 30-day max age), revalidated against `users.token_version` on every protected request.
- **Rate Limiting (`app/extensions.py`)**: `Flask-Limiter` keyed per IP with Redis storage (`REDIS_URL`) in production and in-memory fallback for local development/testing.
- **Blueprints (`app/blueprints/`)**: `auth`, `users`, `friends`, `blocks`, `messages`, and `signals`.

---

## Security Notes

- **No CSRF Required (Bearer Token Auth)**: Every authenticated endpoint uses stateless `Authorization: Bearer <token>` headers rather than browser cookies. Because browsers never automatically attach `Authorization` headers on cross-origin form/fetch requests, CSRF attacks do not apply.
- **Production Secret Enforcement**: Booting with `FLASK_ENV=production` without `P2P_SECRET_KEY` raises `RuntimeError` rather than silently generating an ephemeral key.
- **Security Headers**: Every response sets `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, plus `Strict-Transport-Security` in production.
- **Upload Safety**: Global `MAX_CONTENT_LENGTH = 10 MB`. Uploads are verified via `PIL.Image.open(...).verify()` (never `imghdr` or trusting filename extensions), saved as `<uid>_<random_hex>.<ext>` outside the static webroot, and served with `X-Content-Type-Options: nosniff` and `Content-Disposition: inline`.

---

## Endpoint Reference

| Method | Path | Auth | Rate Limit | Body / Query | Response |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/health` | No | Default | — | `{"success": true, "status": "ok", "db": "ok", "version": "2.0.0"}` |
| `POST` | `/api/register` | No | `3 / 5 min` | `{"username", "password", "display_name"?}` | `201 {"success": true, "token", "user"}` |
| `POST` | `/api/login` | No | `5 / min` | `{"username", "password"}` | `200 {"success": true, "token", "user"}` |
| `POST` | `/api/logout` | Bearer | Default | — | `200 {"success": true, "message"}` |
| `POST` | `/api/password` | Bearer | Default | `{"old_password", "new_password"}` | `200 {"success": true, "message"}` |
| `GET` | `/api/me` | Bearer | Default | — | `200 {"success": true, "user"}` |
| `PATCH` | `/api/profile` | Bearer | Default | `{"display_name"?, "bio"?}` | `200 {"success": true, "user"}` |
| `POST` | `/api/profile/photo` | Bearer | `10 / hr` | `multipart/form-data` (`photo`) | `200 {"success": true, "photo_url", "user"}` |
| `GET` | `/api/users/<id>/photo` | No | Default | — | Raw image bytes (`nosniff`, `inline`) |
| `GET` | `/api/users?q=...` | Bearer | `60 / min` | `?q=term` | `200 {"success": true, "users": [...]}` |
| `POST` | `/api/friends/request` | Bearer | `20 / hr` | `{"user_id"}` | `201/200 {"success": true, "status", "auto_accepted", "request_id"}` |
| `GET` | `/api/friends/requests/incoming` | Bearer | Default | — | `200 {"success": true, "requests": [...]}` |
| `GET` | `/api/friends/requests/sent` | Bearer | Default | — | `200 {"success": true, "requests": [...]}` |
| `POST` | `/api/friends/respond` | Bearer | Default | `{"request_id", "action": "accept"\|"reject"}` | `200 {"success": true, "request_id", "status"}` |
| `POST` | `/api/friends/cancel` | Bearer | Default | `{"request_id"}` | `200 {"success": true, "request_id", "status": "cancelled"}` |
| `GET` | `/api/friends` | Bearer | Default | — | `200 {"success": true, "friends": [...]}` |
| `DELETE` | `/api/friends/<peer_id>` | Bearer | Default | — | `200 {"success": true, "removed": true, "peer_id"}` |
| `POST` | `/api/blocks` | Bearer | Default | `{"user_id"}` | `200 {"success": true, "blocked": true, "user_id"}` |
| `DELETE` | `/api/blocks/<user_id>` | Bearer | Default | — | `200 {"success": true, "blocked": false, "user_id"}` |
| `GET` | `/api/blocks` | Bearer | Default | — | `200 {"success": true, "blocked": [...]}` |
| `POST` | `/api/messages` | Bearer | `120 / min` | `{"receiver_id", "type", "content", "media_size"?}` | `201 {"success": true, "message"}` |
| `GET` | `/api/messages/<peer_id>` | Bearer | Default | `?after_id=0&limit=50` | `200 {"success": true, "messages": [...], "next_after_id"}` |
| `POST` | `/api/signals` | Bearer | Default | `{"to_user_id", "type": "offer"\|"answer"\|"ice", "payload"}` | `201 {"success": true, "signal_id", "expires_at"}` |
| `GET` | `/api/signals` | Bearer | Default | — | `200 {"success": true, "signals": [...]}` |

---

## `curl` Examples

```bash
BASE="http://127.0.0.1:5000"

# Health
curl -s "$BASE/api/health"

# Register & Login
curl -s -X POST "$BASE/api/register" -H "Content-Type: application/json" \
  -d '{"username":"alice_01","password":"StrongPassword123!","display_name":"Alice"}'

TOKEN=$(curl -s -X POST "$BASE/api/login" -H "Content-Type: application/json" \
  -d '{"username":"alice_01","password":"StrongPassword123!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Current user & Profile update
curl -s "$BASE/api/me" -H "Authorization: Bearer $TOKEN"
curl -s -X PATCH "$BASE/api/profile" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"display_name":"Alice Nova","bio":"Hello P2P"}'

# Upload & fetch profile photo
curl -s -X POST "$BASE/api/profile/photo" -H "Authorization: Bearer $TOKEN" -F "photo=@avatar.png"
curl -I "$BASE/api/users/1/photo"

# Search users
curl -s "$BASE/api/users?q=bob" -H "Authorization: Bearer $TOKEN"

# Friend flow
curl -s -X POST "$BASE/api/friends/request" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"user_id":2}'
curl -s "$BASE/api/friends/requests/incoming" -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/api/friends/requests/sent" -H "Authorization: Bearer $TOKEN"
curl -s -X POST "$BASE/api/friends/respond" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"request_id":1,"action":"accept"}'
curl -s -X POST "$BASE/api/friends/cancel" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"request_id":2}'
curl -s "$BASE/api/friends" -H "Authorization: Bearer $TOKEN"
curl -s -X DELETE "$BASE/api/friends/2" -H "Authorization: Bearer $TOKEN"

# Block flow
curl -s -X POST "$BASE/api/blocks" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"user_id":2}'
curl -s "$BASE/api/blocks" -H "Authorization: Bearer $TOKEN"
curl -s -X DELETE "$BASE/api/blocks/2" -H "Authorization: Bearer $TOKEN"

# Messages & Read Receipts
curl -s -X POST "$BASE/api/messages" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"receiver_id":2,"type":"text","content":"Hey there!"}'
curl -s "$BASE/api/messages/2?after_id=0&limit=50" -H "Authorization: Bearer $TOKEN"

# WebRTC Signals
curl -s -X POST "$BASE/api/signals" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"to_user_id":2,"type":"offer","payload":{"type":"offer","sdp":"v=0..."}}'
curl -s "$BASE/api/signals" -H "Authorization: Bearer $TOKEN"

# Password change & Logout (revokes all tokens)
curl -s -X POST "$BASE/api/password" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"old_password":"StrongPassword123!","new_password":"EvenStrongerPassword99!"}'
curl -s -X POST "$BASE/api/logout" -H "Authorization: Bearer $TOKEN"
```

---

## Local Development Setup

```bash
cd p2p-chat-api
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env
python run.py
```

Run tests and coverage:

```bash
pytest --cov=app --cov-report=term-missing
```

---

## Production Deployment (Docker Compose)

```bash
cd p2p-chat-api
export P2P_SECRET_KEY="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
docker compose up --build -d
```

### Environment Variables Reference

| Variable | Required | Default | Purpose |
| :--- | :--- | :--- | :--- |
| `FLASK_ENV` | No | `development` | `development`, `production`, or `testing` |
| `P2P_SECRET_KEY` | Yes in prod | dev-only fallback | Signs bearer tokens (`URLSafeTimedSerializer`) |
| `DATABASE_URL` | No | `""` | PostgreSQL DSN (uses SQLite WAL when empty) |
| `P2P_DB_PATH` | No | `./data/p2p_chat.db` | SQLite database file path |
| `P2P_UPLOAD_DIR` | No | `./uploads` | External directory for uploaded profile photos |
| `REDIS_URL` | Recommended in prod | `""` | Redis URI for `Flask-Limiter` multi-worker state |
| `LOG_LEVEL` | No | `INFO` | Structured JSON log level |

---

## SQLite → PostgreSQL Migration Path

1. Set `DATABASE_URL=postgresql://user:pass@host:5432/p2p_chat`.
2. Run `alembic upgrade head` (automatically executed by `docker/entrypoint.sh`) to apply `migrations/001_initial_postgres.sql`.
3. `app/db.py` handles parameterized `%s` placeholders, `RETURNING id`, and `ON CONFLICT DO NOTHING` transparently.

---

## Known Limitations & Future Work

- Polling-based message and signal delivery could be complemented with WebSockets/SSE for lower latency at high scale.
- Uploaded profile photos are stored on a single shared filesystem volume (`P2P_UPLOAD_DIR`); multi-node clusters should front uploads with S3-compatible object storage.
- Text messages are authenticated at transport (HTTPS) and stored server-side; true end-to-end encryption would require client-managed identity keys.
