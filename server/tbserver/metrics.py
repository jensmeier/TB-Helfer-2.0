from __future__ import annotations

import asyncio
from typing import Any

from .database import transaction


_EXCLUDED_PATH_PREFIXES = (
    "/health/",
)


def _record_http_metric(
    *,
    status_code: int,
    bytes_in: int,
    bytes_out: int,
) -> None:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO tb.system_metrics_hourly(
                bucket_start,
                request_count,
                error_count,
                bytes_in,
                bytes_out
            )
            VALUES (
                date_trunc('hour', now()),
                1,
                %s,
                %s,
                %s
            )
            ON CONFLICT (bucket_start) DO UPDATE
            SET request_count = tb.system_metrics_hourly.request_count + 1,
                error_count = tb.system_metrics_hourly.error_count + EXCLUDED.error_count,
                bytes_in = tb.system_metrics_hourly.bytes_in + EXCLUDED.bytes_in,
                bytes_out = tb.system_metrics_hourly.bytes_out + EXCLUDED.bytes_out,
                updated_at = now()
            """,
            (
                1 if status_code >= 500 else 0,
                max(0, int(bytes_in)),
                max(0, int(bytes_out)),
            ),
        )


class SystemMetricsMiddleware:
    """Record lightweight hourly HTTP traffic metrics without buffering bodies."""

    def __init__(self, app: Any):
        self.app = app

    async def __call__(self, scope: dict[str, Any], receive, send) -> None:
        if scope.get("type") != "http":
            await self.app(scope, receive, send)
            return

        path = str(scope.get("path") or "")
        if any(path.startswith(prefix) for prefix in _EXCLUDED_PATH_PREFIXES):
            await self.app(scope, receive, send)
            return

        bytes_in = 0
        bytes_out = 0
        status_code = 500

        async def counted_receive():
            nonlocal bytes_in
            message = await receive()
            if message.get("type") == "http.request":
                body = message.get("body") or b""
                bytes_in += len(body)
            return message

        async def counted_send(message):
            nonlocal bytes_out, status_code
            message_type = message.get("type")
            if message_type == "http.response.start":
                status_code = int(message.get("status") or 500)
            elif message_type == "http.response.body":
                body = message.get("body") or b""
                bytes_out += len(body)
            await send(message)

        try:
            await self.app(scope, counted_receive, counted_send)
        finally:
            try:
                await asyncio.to_thread(
                    _record_http_metric,
                    status_code=status_code,
                    bytes_in=bytes_in,
                    bytes_out=bytes_out,
                )
            except Exception:
                # Monitoring must never make an otherwise valid API request fail.
                pass
