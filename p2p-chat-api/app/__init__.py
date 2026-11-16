from flask import Flask

from app.blueprints import ALL_BLUEPRINTS
from app.config import resolve_config
from app.db import init_db, register_db_hooks
from app.errors import register_error_handlers
from app.extensions import init_extensions
from app.logging_conf import configure_logging


def create_app(config_override: dict | None = None) -> Flask:
    cfg_cls = resolve_config(config_override)
    app = Flask(__name__)
    app.config.from_object(cfg_cls)
    if config_override:
        app.config.update(config_override)

    if app.config.get("IS_PRODUCTION") and not app.config.get("SECRET_KEY"):
        raise RuntimeError("P2P_SECRET_KEY environment variable is mandatory in production")

    configure_logging(app.config.get("LOG_LEVEL", "INFO"))
    init_extensions(app)
    init_db(app)
    register_db_hooks(app)
    register_error_handlers(app)

    for bp in ALL_BLUEPRINTS:
        app.register_blueprint(bp)

    @app.after_request
    def apply_security_headers(response):
        response.headers.setdefault("X-Content-Type-Options", "nosniff")
        response.headers.setdefault("X-Frame-Options", "DENY")
        response.headers.setdefault("Referrer-Policy", "no-referrer")
        response.headers.setdefault(
            "Content-Security-Policy",
            "default-src 'none'; frame-ancestors 'none'",
        )
        if app.config.get("IS_PRODUCTION"):
            response.headers.setdefault(
                "Strict-Transport-Security",
                "max-age=31536000; includeSubDomains",
            )
        return response

    return app
