from flask import Flask
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address
from itsdangerous import URLSafeTimedSerializer

limiter = Limiter(
    key_func=get_remote_address,
    headers_enabled=True,
)


def init_extensions(app: Flask) -> None:
    limiter.init_app(app)
    app.extensions["token_serializer"] = URLSafeTimedSerializer(
        secret_key=app.config["SECRET_KEY"],
        salt=app.config["TOKEN_SALT"],
    )


def get_token_serializer(app: Flask) -> URLSafeTimedSerializer:
    return app.extensions["token_serializer"]
