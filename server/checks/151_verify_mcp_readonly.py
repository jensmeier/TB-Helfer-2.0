"""Run: python -m unittest discover -s server/checks -p '151_verify_mcp_readonly.py'"""
import asyncio
import json
import os
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mcp_gateway.auth import BearerTokenGate
from mcp_gateway import diagnostics


class DiagnosticTests(unittest.TestCase):
    def test_no_snapshot_not_ok(self):
        with tempfile.TemporaryDirectory() as tmp, patch.dict(os.environ, {"TB_MCP_DIAGNOSTICS_ROOT": tmp}):
            result = diagnostics.monitor_uebersicht()
            self.assertEqual(result["overall"], "UNKNOWN")

    def test_redacted_and_stale(self):
        with tempfile.TemporaryDirectory() as tmp:
            filename = Path(tmp) / "monitor-state.json"
            snapshot = {
                "updated_at": datetime.now(timezone.utc).isoformat(),
                "overall": "OK",
                "checks": {
                    "api": {"status": "OK", "latency_ms": 20, "detail": "secret"},
                    "backup": {"status": "FAILED", "last_error": "private"},
                    "database": {"status": "OK"},
                    "public": {"status": "OK"}, "worker": {"status": "OK"},
                    "storage": {"status": "OK"}, "io": {"status": "OK"}, "memory": {"status": "OK"},
                },
                "recent_events": [{"email": "never expose"}],
            }
            filename.write_text(json.dumps(snapshot), encoding="utf-8")
            with patch.dict(os.environ, {"TB_MCP_DIAGNOSTICS_ROOT": tmp}):
                good = diagnostics.monitor_uebersicht()
                self.assertNotIn("secret", json.dumps(good))
                self.assertNotIn("private", json.dumps(good))
                self.assertNotIn("never expose", json.dumps(good))
                self.assertEqual(good["checks"]["backup"]["status"], "UNKNOWN")
                snapshot["updated_at"] = (datetime.now(timezone.utc) - timedelta(hours=1)).isoformat()
                filename.write_text(json.dumps(snapshot), encoding="utf-8")
                old = diagnostics.monitor_uebersicht()
                self.assertTrue(old["stale"])
                self.assertEqual(old["overall"], "UNKNOWN")

    def test_health_does_not_use_arbitrary_targets(self):
        with patch.dict(os.environ, {"TB_MCP_API_BASE_URL": "https://example.org"}):
            self.assertEqual(diagnostics._health("/health/live")["status"], "UNKNOWN")
        self.assertEqual(diagnostics._health("/anything")["status"], "UNKNOWN")

    def test_token_required_and_denied(self):
        async def inner(scope, receive, send):
            await send({"type": "http.response.start", "status": 200, "headers": []})
            await send({"type": "http.response.body", "body": b"OK"})
        with self.assertRaises(RuntimeError):
            BearerTokenGate(inner, token="short")
        gate = BearerTokenGate(inner, token="x" * 32)
        async def run(headers):
            events = []
            async def sender(event):
                events.append(event)
            await gate({"type": "http", "headers": headers}, None, sender)
            return events[0]["status"]
        self.assertEqual(asyncio.run(run([])), 401)
        self.assertEqual(asyncio.run(run([(b"authorization", b"Bearer bad")])), 401)
        self.assertEqual(asyncio.run(run([(b"authorization", b"Bearer " + b"x" * 32)])), 200)
        self.assertEqual(asyncio.run(run([(b"authorization", b"Bearer " + b"x" * 32),
                                          (b"origin", b"https://untrusted.example")])), 403)


if __name__ == "__main__":
    unittest.main()
