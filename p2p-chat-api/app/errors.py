import logging
from flask import Flask, jsonify
from werkzeug.exceptions import HTTPException

logger = logging.getLogger(__name__)


def register_error_handlers(app: Flask) -> None:
    @app.errorhandler(400)
    def handle_400(_err):
        return jsonify({"success": False, "error": "Bad request"}), 400

    @app.errorhandler(401)
    def handle_401(_err):
        return jsonify({"success": False, "error": "Unauthorized"}), 401

    @app.errorhandler(403)
    def handle_403(_err):
        return jsonify({"success": False, "error": "Forbidden"}), 403

    @app.errorhandler(404)
    def handle_404(_err):
        return jsonify({"success": False, "error": "Resource not found"}), 404

    @app.errorhandler(405)
    def handle_405(_err):
        return jsonify({"success": False, "error": "Method not allowed"}), 405

    @app.errorhandler(413)
    def handle_413(_err):
        return jsonify({"success": False, "error": "Payload too large (max 10 MB)"}), 413

    @app.errorhandler(429)
    def handle_429(_err):
        return jsonify({"success": False, "error": "Too many requests. Please slow down."}), 429

    @app.errorhandler(HTTPException)
    def handle_http_exception(err: HTTPException):
        code = err.code or 500
        return jsonify({"success": False, "error": err.name}), code

    @app.errorhandler(Exception)
    def handle_unexpected_exception(err: Exception):
        logger.exception("Unhandled server exception: %s", err)
        return jsonify({"success": False, "error": "Internal server error"}), 500
