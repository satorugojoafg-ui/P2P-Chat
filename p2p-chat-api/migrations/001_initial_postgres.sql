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
);

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
);

CREATE TABLE IF NOT EXISTS friendships (
    user1 BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user2 BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    CHECK (user1 < user2),
    PRIMARY KEY (user1, user2)
);

CREATE TABLE IF NOT EXISTS blocks (
    blocker_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    CHECK (blocker_id <> blocked_id),
    PRIMARY KEY (blocker_id, blocked_id)
);

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
);

CREATE TABLE IF NOT EXISTS signals (
    id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    receiver_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    CHECK (type IN ('offer', 'answer', 'ice'))
);

CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages (sender_id, id);
CREATE INDEX IF NOT EXISTS idx_messages_receiver_id ON messages (receiver_id, id);
CREATE INDEX IF NOT EXISTS idx_messages_receiver_read ON messages (receiver_id, read_at);
CREATE INDEX IF NOT EXISTS idx_friend_requests_receiver_status ON friend_requests (receiver_id, status);
CREATE INDEX IF NOT EXISTS idx_friend_requests_sender_status ON friend_requests (sender_id, status);
CREATE INDEX IF NOT EXISTS idx_signals_receiver_id ON signals (receiver_id, id);
