"""Initial P2P Chat schema for PostgreSQL and SQLite

Revision ID: 0001_initial_schema
Revises:
Create Date: 2026-11-16
"""

import os
from alembic import op

revision = "0001_initial_schema"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    dialect = op.get_bind().dialect.name
    migrations_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    sql_file = "001_initial_postgres.sql" if dialect == "postgresql" else "001_initial_sqlite.sql"
    with open(os.path.join(migrations_dir, sql_file), "r", encoding="utf-8") as f:
        raw_sql = f.read()

    for statement in [s.strip() for s in raw_sql.split(";") if s.strip()]:
        if statement.upper().startswith("PRAGMA") and dialect == "postgresql":
            continue
        op.execute(statement)


def downgrade() -> None:
    for table in ["signals", "messages", "blocks", "friendships", "friend_requests", "users"]:
        op.execute(f"DROP TABLE IF EXISTS {table}")
