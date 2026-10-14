"""Fail-closed bearer authentication for the local HTTP MCP gateway."""
from __future__ import annotations

import hmac
import os


class BearerTokenGate:
    def __init__(self, app, token: str | None = None):
        self.app = app
        self.token = token if token is not None else os.getenv("TB_MCP_BEARER_TOKEN", "")
        if len(self.token.encode("utf-8")) < 32:
            raise RuntimeError("TB_MCP_BEARER_TOKEN muss mindestens 32 Bytes lang sein")
        self.allowed_origins = frozenset(
            value.strip() for value in os.getenv("TB_MCP_ALLOWED_ORIGINS", "").split(",") if value.strip()
        )

    async def __call__(self, scope, receive, send):
        if scope["type"] == "lifespan":
            await self.app(scope, receive, send)
            return
        if scope["type"] != "http":
            if scope["type"] == "websocket":
                await send({"type": "websocket.close", "code": 1008})
            return
        headers = {key.lower(): value for key, value in scope.get("headers", [])}
        origin = headers.get(b"origin")
        if origin and origin.decode("utf-8", "replace") not in self.allowed_origins:
            await self._deny(send, 403)
            return
        actual = headers.get(b"authorization", b"")
        expected = b"Bearer " + self.token.encode("utf-8")
        if not hmac.compare_digest(actual, expected):
            await self._deny(send, 401)
            return
        await self.app(scope, receive, send)

    @staticmethod
    async def _deny(send, status):
        body = b'{"error":"access_denied"}'
        await send({"type": "http.response.start", "status": status,
                    "headers": [(b"content-type", b"application/json"),
                                (b"cache-control", b"no-store"),
                                (b"content-length", str(len(body)).encode("ascii"))]})
        await send({"type": "http.response.body", "body": body})
