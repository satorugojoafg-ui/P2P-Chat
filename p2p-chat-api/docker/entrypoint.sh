#!/bin/sh
set -eu

mkdir -p "${P2P_UPLOAD_DIR:-/app/uploads}"

if [ -n "${DATABASE_URL:-}" ]; then
  echo "Running Alembic migrations against DATABASE_URL..."
  alembic upgrade head
else
  mkdir -p "$(dirname "${P2P_DB_PATH:-/app/data/p2p_chat.db}")"
  python -c "from app import create_app; create_app()"
fi

exec "$@"
