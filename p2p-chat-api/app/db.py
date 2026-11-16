import os
import sqlite3
from typing import Any
from flask import Flask, current_app, g


SQLITE_SCHEMA_STATEMENTS = [
    """
    CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT UNIQUE NOT NULL,
        display_name TEXT NOT NULL,
        password_hash TEXT NOT NULL,
        bio TEXT NOT NULL DEFAULT '',
        photo_url TEXT,
        token_version INTEGER NOT NULL DEFAULT 1,
        last_seen TEXT,
        created_at TEXT NOT NULL
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS friend_requests (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        sender_id INTEGER NOT NULL,
        receiver_id INTEGER NOT NULL,
        status TEXT NOT NULL DEFAULT 'pending',
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        CHECK (sender_id <> receiver_id),
        CHECK (status IN ('pending', 'accepted', 'rejected', 'cancelled')),
        UNIQUE (sender_id, receiver_id),
        FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
        FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS friendships (
        user1 INTEGER NOT NULL,
        user2 INTEGER NOT NULL,
        created_at TEXT NOT NULL,
        CHECK (user1 < user2),
        PRIMARY KEY (user1, user2),
        FOREIGN KEY (user1) REFERENCES users (id) ON DELETE CASCADE,
        FOREIGN KEY (user2) REFERENCES users (id) ON DELETE CASCADE
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS blocks (
        blocker_id INTEGER NOT NULL,
        blocked_id INTEGER NOT NULL,
        created_at TEXT NOT NULL,
        CHECK (blocker_id <> blocked_id),
        PRIMARY KEY (blocker_id, blocked_id),
        FOREIGN KEY (blocker_id) REFERENCES users (id) ON DELETE CASCADE,
        FOREIGN KEY (blocked_id) REFERENCES users (id) ON DELETE CASCADE
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS messages (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        sender_id INTEGER NOT NULL,
        receiver_id INTEGER NOT NULL,
        type TEXT NOT NULL DEFAULT 'text',
        content TEXT NOT NULL,
        media_size INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL,
        read_at TEXT,
        CHECK (type IN ('text', 'audio', 'image', 'video', 'file')),
        CHECK (media_size >= 0),
        FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
        FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS signals (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        sender_id INTEGER NOT NULL,
        receiver_id INTEGER NOT NULL,
        type TEXT NOT NULL,
        payload TEXT NOT NULL,
        created_at TEXT NOT NULL,
        expires_at TEXT NOT NULL,
        CHECK (type IN ('offer', 'answer', 'ice')),
        FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
        FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages (sender_id, id)",
    "CREATE INDEX IF NOT EXISTS idx_messages_receiver_id ON messages (receiver_id, id)",
    "CREATE INDEX IF NOT EXISTS idx_messages_receiver_read ON messages (receiver_id, read_at)",
    "CREATE INDEX IF NOT EXISTS idx_friend_requests_receiver_status ON friend_requests (receiver_id, status)",
    "CREATE INDEX IF NOT EXISTS idx_friend_requests_sender_status ON friend_requests (sender_id, status)",
    "CREATE INDEX IF NOT EXISTS idx_signals_receiver_id ON signals (receiver_id, id)",
]


POSTGRES_SCHEMA_STATEMENTS = [
    """
    CREATE TABLE IF NOT EXISTS users (
        id BIGSERIAL PRIMARY KEY,
        username TEXT UNIQUE NOT NULL,
        display_name TEXT NOT NULL,
        password_hash TEXT NOT NULL,
        bio TEXT NOT NULL DEFAULT '',
        photo_url TEXT,
        token_version INTEGER NOT NULL DEFAULT 1,
        last_seen TEXT,
        created_at TEXT NOT NULL
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS friend_requests (
        id BIGSERIAL PRIMARY KEY,
        sender_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        receiver_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        status TEXT NOT NULL DEFAULT 'pending',
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        CHECK (sender_id <> receiver_id),
        CHECK (status IN ('pending', 'accepted', 'rejected', 'cancelled')),
        UNIQUE (sender_id, receiver_id)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS friendships (
        user1 BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        user2 BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        created_at TEXT NOT NULL,
        CHECK (user1 < user2),
        PRIMARY KEY (user1, user2)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS blocks (
        blocker_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        blocked_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        created_at TEXT NOT NULL,
        CHECK (blocker_id <> blocked_id),
        PRIMARY KEY (blocker_id, blocked_id)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS messages (
        id BIGSERIAL PRIMARY KEY,
        sender_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        receiver_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        type TEXT NOT NULL DEFAULT 'text',
        content TEXT NOT NULL,
        media_size BIGINT NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL,
        read_at TEXT,
        CHECK (type IN ('text', 'audio', 'image', 'video', 'file')),
        CHECK (media_size >= 0)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS signals (
        id BIGSERIAL PRIMARY KEY,
        sender_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        receiver_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        type TEXT NOT NULL,
        payload TEXT NOT NULL,
        created_at TEXT NOT NULL,
        expires_at TEXT NOT NULL,
        CHECK (type IN ('offer', 'answer', 'ice'))
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages (sender_id, id)",
    "CREATE INDEX IF NOT EXISTS idx_messages_receiver_id ON messages (receiver_id, id)",
    "CREATE INDEX IF NOT EXISTS idx_messages_receiver_read ON messages (receiver_id, read_at)",
    "CREATE INDEX IF NOT EXISTS idx_friend_requests_receiver_status ON friend_requests (receiver_id, status)",
    "CREATE INDEX IF NOT EXISTS idx_friend_requests_sender_status ON friend_requests (sender_id, status)",
    "CREATE INDEX IF NOT EXISTS idx_signals_receiver_id ON signals (receiver_id, id)",
]


class Database:
    def __init__(self, backend: str, raw_conn: Any):
        self.backend = backend
        self.conn = raw_conn

    def _adapt_sql(self, sql: str) -> str:
        if self.backend == "postgres":
            return sql.replace("?", "%s")
        return sql

    def execute(self, sql: str, params: tuple | list = (), *, commit: bool = False) -> Any:
        adapted = self._adapt_sql(sql)
        cur = self.conn.cursor()
        cur.execute(adapted, params)
        if commit:
            self.conn.commit()
        return cur

    def fetchone(self, sql: str, params: tuple | list = ()) -> dict | None:
        cur = self.execute(sql, params)
        row = cur.fetchone()
        if row is None:
            return None
        return dict(row)

    def fetchall(self, sql: str, params: tuple | list = ()) -> list[dict]:
        cur = self.execute(sql, params)
        return [dict(r) for r in cur.fetchall()]

    def insert_returning_id(self, sql: str, params: tuple | list = (), *, commit: bool = False) -> int:
        if self.backend == "postgres":
            adapted = self._adapt_sql(sql)
            if "RETURNING" not in adapted.upper():
                adapted = f"{adapted.rstrip(';')} RETURNING id"
            cur = self.conn.cursor()
            cur.execute(adapted, params)
            row = cur.fetchone()
            if commit:
                self.conn.commit()
            return int(row["id"])
        cur = self.execute(sql, params, commit=commit)
        return int(cur.lastrowid)

    def insert_ignore_sql(self, table: str, columns: list[str]) -> str:
        cols = ", ".join(columns)
        placeholders = ", ".join("?" for _ in columns)
        if self.backend == "postgres":
            return f"INSERT INTO {table} ({cols}) VALUES ({placeholders}) ON CONFLICT DO NOTHING"
        return f"INSERT OR IGNORE INTO {table} ({cols}) VALUES ({placeholders})"

    def commit(self) -> None:
        self.conn.commit()

    def rollback(self) -> None:
        self.conn.rollback()

    def close(self) -> None:
        self.conn.close()


def _open_connection(app: Flask) -> Database:
    database_url = app.config.get("DATABASE_URL", "").strip()
    if database_url.startswith("postgres://") or database_url.startswith("postgresql://"):
        import psycopg
        from psycopg.rows import dict_row

        conn = psycopg.connect(database_url, row_factory=dict_row)
        return Database("postgres", conn)

    db_path = app.config["P2P_DB_PATH"]
    os.makedirs(os.path.dirname(os.path.abspath(db_path)), exist_ok=True)
    conn = sqlite3.connect(db_path, timeout=30.0)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA journal_mode = WAL")
    conn.execute("PRAGMA synchronous = NORMAL")
    return Database("sqlite", conn)


def get_db() -> Database:
    if "db" not in g:
        g.db = _open_connection(current_app)
    return g.db


def close_db(_exception: BaseException | None = None) -> None:
    db: Database | None = g.pop("db", None)
    if db is not None:
        db.close()


def init_db(app: Flask) -> None:
    db = _open_connection(app)
    try:
        statements = POSTGRES_SCHEMA_STATEMENTS if db.backend == "postgres" else SQLITE_SCHEMA_STATEMENTS
        for stmt in statements:
            db.execute(stmt)
        db.commit()
    finally:
        db.close()


def register_db_hooks(app: Flask) -> None:
    app.teardown_appcontext(close_db)
