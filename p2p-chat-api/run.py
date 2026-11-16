import os
from app import create_app

application = create_app()

if __name__ == "__main__":
    host = os.environ.get("FLASK_RUN_HOST", "127.0.0.1")
    port = int(os.environ.get("FLASK_RUN_PORT", "5000"))
    is_dev = os.environ.get("FLASK_ENV", "development").strip().lower() == "development"
    application.run(host=host, port=port, debug=is_dev)
