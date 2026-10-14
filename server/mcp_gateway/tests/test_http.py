"""Real HTTP MCP JSON-RPC auth, initialize, tools/list and tool calls."""
import json
import os
import tempfile
import unittest
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
from unittest.mock import patch
from starlette.testclient import TestClient

TOKEN = "CI-test-token-000001-strong-enough-placeholder"
os.environ["TB_MCP_BEARER_TOKEN"] = TOKEN
from mcp_gateway.service import app

class FakeHealth(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health/live":
            obj = {"status": "ok", "server_version": "1.0-dev"}
        elif self.path == "/health/ready":
            obj = {"status": "ok", "database": "up", "migrations": "ok"}
        else:
            self.send_error(404)
            return
        payload = json.dumps(obj).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)
    def log_message(self, *_args):
        pass

class HttpMcpTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeHealth)
        cls.thread = Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=3)

    def test_bearer_origin_handshake_list_and_tools(self):
        with tempfile.TemporaryDirectory() as td:
            snap = {"updated_at": datetime.now(timezone.utc).isoformat(),
                    "overall": "OK",
                    "checks": {name: {"status": "OK", "detail": "SENSITIVE_DO_NOT_EMIT"}
                               for name in ("database","api","public","worker",
                                            "backup","storage","io","memory")}}
            (Path(td) / "monitor-state.json").write_text(json.dumps(snap))
            with patch.dict(os.environ, {"TB_MCP_DIAGNOSTICS_ROOT":td,
                                         "TB_MCP_API_BASE_URL":f"http://127.0.0.1:{self.server.server_port}"}):
                with TestClient(app) as client:
                    headers = {"Authorization":"Bearer "+TOKEN,
                               "Accept":"application/json, text/event-stream",
                               "Content-Type":"application/json"}
                    init={"jsonrpc":"2.0","id":1,"method":"initialize",
                          "params":{"protocolVersion":"2025-03-26","capabilities":{},
                                    "clientInfo":{"name":"CI","version":"1.0"}}}
                    self.assertEqual(client.post("/mcp",json=init).status_code,401)
                    self.assertEqual(client.post("/mcp",json=init,headers={**headers,"Authorization":"Bearer wrong"}).status_code,401)
                    self.assertEqual(client.post("/mcp",json=init,headers={**headers,"Origin":"https://evil.invalid"}).status_code,403)
                    r=client.post("/mcp",json=init,headers=headers)
                    self.assertEqual(r.status_code,200,r.text[:500])
                    self.assertIn("result",r.json())
                    r=client.post("/mcp",json={"jsonrpc":"2.0","id":2,"method":"tools/list"},headers=headers)
                    self.assertEqual(r.status_code,200,r.text[:500])
                    names={v["name"] for v in r.json()["result"]["tools"]}
                    self.assertEqual(names,{"server_gesundheit","monitor_uebersicht","server_probleme"})
                    for name in names:
                        r=client.post("/mcp",json={"jsonrpc":"2.0","id":3,"method":"tools/call",
                                     "params":{"name":name,"arguments":{}}},headers=headers)
                        self.assertEqual(r.status_code,200,r.text[:500])
                        self.assertFalse(r.json().get("result",{}).get("isError",False),r.text[:500])
                        self.assertNotIn("SENSITIVE_DO_NOT_EMIT",r.text)
                        self.assertNotIn(TOKEN,r.text)
                    print("MCP_HTTP_PASS: authorization, initialize, tool listing/calls, no leaks")

if __name__ == "__main__":
    unittest.main()
