import os
from datetime import timedelta


class Config:
    APP_VERSION = "2.0.0"
    ENV = os.environ.get("FLASK_ENV", "development").strip().lower()
    IS_PRODUCTION = ENV == "production"

    SECRET_KEY = os.environ.get("P2P_SECRET_KEY", "").strip()
    TOKEN_SALT = "p2p-chat-auth-token-v1"
    TOKEN_MAX_AGE_SECONDS = int(timedelta(days=30).total_seconds())

    DATABASE_URL = os.environ.get("DATABASE_URL", "").strip()
    P2P_DB_PATH = os.environ.get(
        "P2P_DB_PATH",
        os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "data", "p2p_chat.db")),
    )
    P2P_UPLOAD_DIR = os.environ.get(
        "P2P_UPLOAD_DIR",
        os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "uploads")),
    )

    REDIS_URL = os.environ.get("REDIS_URL", "").strip()
    RATELIMIT_STORAGE_URI = REDIS_URL if REDIS_URL else "memory://"
    RATELIMIT_ENABLED = os.environ.get("RATELIMIT_ENABLED", "true").lower() == "true"
    RATELIMIT_HEADERS_ENABLED = True

    MAX_CONTENT_LENGTH = 10 * 1024 * 1024  # 10 MB global Flask cap
    SIGNAL_TTL_SECONDS = 300  # 5 minutes
    MAX_MESSAGE_LENGTH = 8000
    MAX_MEDIA_SIZE_BYTES = 100 * 1024 * 1024  # 100 MB metadata cap
    MAX_SIGNAL_PAYLOAD_BYTES = 20 * 1024  # 20 KB

    LOG_LEVEL = os.environ.get("LOG_LEVEL", "INFO").upper()


class DevelopmentConfig(Config):
    ENV = "development"
    IS_PRODUCTION = False
    SECRET_KEY = Config.SECRET_KEY or "dev-insecure-secret-key-do-not-use-in-prod"


class ProductionConfig(Config):
    ENV = "production"
    IS_PRODUCTION = True


class TestingConfig(Config):
    ENV = "testing"
    IS_PRODUCTION = False
    TESTING = True
    SECRET_KEY = "test-secret-key-fixed-for-pytest"
    RATELIMIT_STORAGE_URI = "memory://"
    RATELIMIT_ENABLED = False


def resolve_config(config_override: dict | None = None) -> type[Config]:
    env = (config_override or {}).get("ENV") or os.environ.get("FLASK_ENV", "development").strip().lower()
    if env == "production":
        return ProductionConfig
    if env == "testing":
        return TestingConfig
    return DevelopmentConfig
