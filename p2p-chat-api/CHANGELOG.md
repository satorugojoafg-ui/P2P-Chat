# Changelog

## 2.0.0 — Production Refactor & Hardening

### Added
- **Modular Package Architecture**: Split single-file backend into `app/` (`config.py`, `db.py`, `extensions.py`, `security.py`, `errors.py`, `utils.py`, `logging_conf.py`) and dedicated feature blueprints (`auth`, `users`, `friends`, `blocks`, `messages`, `signals`).
- **Dual-Backend DB Layer & Alembic**: Added thin parameterized `Database` abstraction supporting both WAL SQLite (`P2P_DB_PATH`) and PostgreSQL (`DATABASE_URL`), plus `migrations/` SQL scripts and Alembic setup (`0001_initial_schema`).
- **Comprehensive Pytest Suite**: Added fixtures (`tests/conftest.py`) and coverage across auth, friends, blocks, messages, signals, photos, and rate limiting.
- **Containerization**: Added multi-stage non-root `docker/Dockerfile`, `docker/entrypoint.sh`, and `docker-compose.yml` (API + PostgreSQL 16 + Redis 7).

### Security & Behavioral Hardening
- Enforced mandatory `P2P_SECRET_KEY` on startup in production (`FLASK_ENV=production`).
- Enforced `scrypt` password hashing and timing-safe login with dummy `scrypt` verification for unknown usernames.
- Added `itsdangerous.URLSafeTimedSerializer` bearer tokens embedding `uid`, `tv`, and `iat` with per-request `token_version` revalidation and revocation on `/api/logout` and `/api/password`.
- Replaced `imghdr`/extension checks with real Pillow image verification (`Image.open(...).verify()`), randomized `<uid>_<hex>.<ext>` storage outside webroot, and `nosniff`/`inline` response headers.
- Enforced Redis-backed `Flask-Limiter` per-IP rate limits (`login`, `register`, `send_message`, `upload_profile_photo`, `search_users`, `send_friend_request`).
- Added global JSON error handlers logging exceptions server-side without exposing stack traces, plus strict response security headers (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Content-Security-Policy`, and production `Strict-Transport-Security`).
- Added atomic transaction cleanup for 5-minute WebRTC signal TTL expiry and one-shot deletion on `GET /api/signals`.
