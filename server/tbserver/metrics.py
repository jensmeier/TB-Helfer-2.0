from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from typing import Any

from .database import transaction


logger = logging.getLogger(__name__)
_FLUSH_INTERVAL_SECONDS = 10
_EXCLUDED_PATH_PREFIXES = ("/health/",)


def _write_http_metrics(buckets: dict[datetime, list[int]]) -> None:
    with transaction() as conn, conn.cursor() as cur:
        cur.execute("SET LOCAL statement_timeout = '3s'")
        cur.executemany(
            """
            INSERT INTO tb.system_metrics_hourly(
                bucket_start,
                request_count,
                error_count,
                bytes_in,
                bytes_out
            )
            VALUES (%s, %s, %s, %s, %s)
            ON CONFLICT (bucket_start) DO UPDATE
            SET request_count = tb.system_metrics_hourly.request_count + EXCLUDED.request_count,
                error_count = tb.system_metrics_hourly.error_count + EXCLUDED.error_count,
                bytes_in = tb.system_metrics_hourly.bytes_in + EXCLUDED.bytes_in,
                bytes_out = tb.system_metrics_hourly.bytes_out + EXCLUDED.bytes_out,
                updated_at = now()
            """,
            [(hour, *counts) for hour, counts in sorted(buckets.items())],
        )


class HttpMetrics:
    """One event-loop-owned batch per API process."""

    def __init__(self) -> None:
        self._buckets: dict[datetime, list[int]] = {}
        self._write_failed = False

    def record(self, status_code: int, bytes_in: int, bytes_out: int) -> None:
        hour = datetime.now(UTC).replace(minute=0, second=0, microsecond=0)
        counts = self._buckets.setdefault(hour, [0, 0, 0, 0])
        counts[0] += 1
        counts[1] += int(status_code >= 500)
        counts[2] += bytes_in
        counts[3] += bytes_out

    async def flush(self) -> None:
        if not self._buckets:
            return
        # Requests can continue counting while the detached batch is written.
        batch, self._buckets = self._buckets, {}
        try:
            await asyncio.to_thread(_write_http_metrics, batch)
        except Exception as exc:
            # A failed commit can have an unknown outcome. Replaying this
            # additive write could double-count traffic.
            if not self._write_failed:
                logger.warning("http_metrics_batch_write_failed error=%s", type(exc).__name__)
            self._write_failed = True
        else:
            self._write_failed = False

    async def run(self, stopped: asyncio.Event) -> None:
        while True:
            try:
                await asyncio.wait_for(stopped.wait(), timeout=_FLUSH_INTERVAL_SECONDS)
            except TimeoutError:
                await self.flush()
            else:
                break
        await self.flush()


@asynccontextmanager
async def http_metrics_lifespan():
    metrics = HttpMetrics()
    stopped = asyncio.Event()
    task = asyncio.create_task(metrics.run(stopped))
    try:
        yield metrics
    finally:
        stopped.set()
        await task


class SystemMetricsMiddleware:
    """Count HTTP traffic without buffering bodies or writing on the request path."""

    def __init__(self, app: Any) -> None:
        self.app = app

    async def __call__(self, scope: dict[str, Any], receive, send) -> None:
        metrics = scope.get("state", {}).get("http_metrics")
        if scope.get("type") != "http" or metrics is None:
            await self.app(scope, receive, send)
            return
        path = str(scope.get("path") or "")
        if path.startswith(_EXCLUDED_PATH_PREFIXES):
            await self.app(scope, receive, send)
            return

        bytes_in = 0
        bytes_out = 0
        status_code = 500

        async def counted_receive():
            nonlocal bytes_in
            message = await receive()
            if message.get("type") == "http.request":
                bytes_in += len(message.get("body") or b"")
            return message

        async def counted_send(message):
            nonlocal bytes_out, status_code
            message_type = message.get("type")
            if message_type == "http.response.start":
                status_code = int(message.get("status") or 500)
            elif message_type == "http.response.body":
                bytes_out += len(message.get("body") or b"")
            await send(message)

        try:
            await self.app(scope, counted_receive, counted_send)
        finally:
            metrics.record(status_code, bytes_in, bytes_out)
