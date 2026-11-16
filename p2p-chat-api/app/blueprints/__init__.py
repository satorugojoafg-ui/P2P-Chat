from app.blueprints.auth import auth_bp
from app.blueprints.blocks import blocks_bp
from app.blueprints.friends import friends_bp
from app.blueprints.messages import messages_bp
from app.blueprints.signals import signals_bp
from app.blueprints.users import users_bp

ALL_BLUEPRINTS = [
    auth_bp,
    users_bp,
    friends_bp,
    blocks_bp,
    messages_bp,
    signals_bp,
]
