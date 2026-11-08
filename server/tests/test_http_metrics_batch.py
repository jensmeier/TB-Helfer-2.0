"""HTTP integration for the production middleware with a database writer double."""
import asyncio
import unittest
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from unittest.mock import patch

from fastapi import FastAPI, Request
from fastapi.responses import Response, StreamingResponse

from tbserver import metrics


class HttpMetricsTests(unittest.IsolatedAsyncioTestCase):
    async def test_http_requests_batch_and_shutdown(self):
        @asynccontextmanager
        async def lifespan(app):
            async with metrics.http_metrics_lifespan() as collector:
                yield {"http_metrics": collector}

        app = FastAPI(lifespan=lifespan)
        app.add_middleware(metrics.SystemMetricsMiddleware)

        @app.post("/sync")
        async def sync(request: Request):
            await request.body()
            return StreamingResponse(iter([b"ab", b"c"]))

        @app.get("/failure")
        def failure():
            return Response(b"bad", status_code=503)

        @app.get("/health/ready")
        def health():
            return Response(b"ready")

        with (
            patch.object(metrics, "_write_http_metrics") as writer,
            patch.object(metrics, "_FLUSH_INTERVAL_SECONDS", 600),
        ):
            async with app.router.lifespan_context(app) as state:
                async def request(method, path, body=b""):
                    messages = []

                    async def receive():
                        return {"type": "http.request", "body": body}

                    async def send(message):
                        messages.append(message)

                    await app({
                        "type": "http",
                        "asgi": {"version": "3.0", "spec_version": "2.4"},
                        "method": method,
                        "path": path,
                        "query_string": b"",
                        "headers": [],
                        "state": state.copy(),
                    }, receive, send)
                    status = messages[0]["status"]
                    response = b"".join(message.get("body", b"") for message in messages[1:])
                    return status, response

                for _ in range(60):
                    self.assertEqual(await request("POST", "/sync", b"abcd"), (200, b"abc"))
                self.assertEqual(await request("GET", "/failure"), (503, b"bad"))
                self.assertEqual(await request("GET", "/health/ready"), (200, b"ready"))
                writer.assert_not_called()
            writer.assert_called_once()
            buckets = writer.call_args.args[0]
            totals = tuple(sum(counts[i] for counts in buckets.values()) for i in range(4))
            self.assertEqual(totals, (61, 1, 240, 183))


class MetricsLifecycleTests(unittest.IsolatedAsyncioTestCase):
    async def test_buckets_use_completion_hour_across_midnight(self):
        collector = metrics.HttpMetrics()
        before = datetime(2026, 11, 8, 23, 59, 59, tzinfo=UTC)
        after = datetime(2026, 11, 9, 0, 0, 1, tzinfo=UTC)
        with patch.object(metrics, "datetime") as clock:
            clock.now.side_effect = [before, after]
            collector.record(200, 1, 2)
            collector.record(500, 3, 4)
        with patch.object(metrics, "_write_http_metrics") as writer:
            await collector.flush()
            await collector.flush()
            writer.assert_called_once_with({
                before.replace(minute=0, second=0): [1, 0, 1, 2],
                after.replace(second=0): [1, 1, 3, 4],
            })

    async def test_failed_batch_not_replayed_and_next_batch_succeeds(self):
        collector = metrics.HttpMetrics()
        with patch.object(metrics, "_write_http_metrics", side_effect=[OSError(), None]) as writer:
            collector.record(200, 1, 2)
            with self.assertLogs(metrics.logger, level="WARNING") as logs:
                await collector.flush()
            self.assertIn("http_metrics_batch_write_failed", logs.output[0])
            collector.record(200, 3, 4)
            await collector.flush()
        self.assertEqual(writer.call_count, 2)
        self.assertEqual(list(writer.call_args.args[0].values()), [[1, 0, 3, 4]])

    async def test_periodic_write_without_a_followup_request(self):
        written = asyncio.Event()
        loop = asyncio.get_running_loop()

        def mark_written(buckets):
            loop.call_soon_threadsafe(written.set)

        with (
            patch.object(metrics, "_FLUSH_INTERVAL_SECONDS", 0.01),
            patch.object(metrics, "_write_http_metrics", side_effect=mark_written) as writer,
        ):
            async with metrics.http_metrics_lifespan() as collector:
                collector.record(200, 0, 1)
                await asyncio.wait_for(written.wait(), timeout=2)
            writer.assert_called_once()


if __name__ == "__main__":
    unittest.main()
