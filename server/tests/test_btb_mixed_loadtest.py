"""Exercise the runner over local HTTP, using synthetic fixtures instead of a NAS."""
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import sys
import threading
import unittest
from urllib.parse import parse_qs, urlsplit
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from btb_mixed_loadtest import Api, TestRunError, loopback_origin, run, verify_pdf


class TestApi(ThreadingHTTPServer):
    def __init__(self):
        super().__init__(("127.0.0.1", 0), Handler)
        self.small, self.rich, self.upload = (str(uuid4()) for _ in range(3))
        self.site = str(uuid4())
        self.pdf = b"%PDF-test"
        self.events = []
        self.lock = threading.Lock()
        self.started_upload = threading.Event()
        self.upload_id = str(uuid4())
        self.file_id = str(uuid4())
        self.photo = b"\xff\xd8fixture\xff\xd9"

    def event(self, kind, key, operation):
        with self.lock:
            self.events.append({"change_id": len(self.events) + 1, "entity_type": kind,
                                "entity_id": key, "operation": operation})


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, data, status=200):
        body = data if isinstance(data, bytes) else json.dumps(data).encode()
        self.send_response(status)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urlsplit(self.path)
        server = self.server
        if url.path.endswith("/changes/wait"):
            self.assert_header("Authorization", "Bearer reader")
            after = int(parse_qs(url.query)["after"][0])
            with server.lock:
                return self.reply({"head": len(server.events), "next_after": len(server.events),
                                   "caught_up": True, "changes": server.events[after:]})
        if url.path.endswith("/final-pdf"):
            return self.reply(server.pdf)
        if url.path.endswith(server.upload_id):
            return self.reply({"status": "OPEN", "received_bytes": 0})
        btb = url.path.rsplit("/", 1)[-1]
        return self.reply({"btb": {"id": btb, "status": "ACTIVE", "worksite_id": server.site},
                           "current_user": {"can_finalize_btb": True},
                           "photos": [None] * (6 if btb == server.rich else 0)})

    def assert_header(self, name, value):
        if self.headers.get(name) != value:
            raise AssertionError("Unexpected test header")

    def do_POST(self):
        server = self.server
        self.assert_header("Authorization", "Bearer writer")
        if not re.fullmatch(r"mixed-(final|create|complete)-[a-f0-9-]{36}", self.headers.get("X-Client-Change-Id", "")):
            return self.reply({}, 400)
        payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        if self.path.endswith("/finalize"):
            btb = self.path.split("/")[-2]
            if payload != {"allow_unfinished": False}:
                return self.reply({}, 400)
            if btb == server.rich and not server.started_upload.wait(2):
                return self.reply({}, 503)
            server.event("BTB", btb, "FINALIZE")
            return self.reply({"id": btb, "status": "FINALIZED", "final_pdf": {
                "sha256": hashlib.sha256(server.pdf).hexdigest(), "byte_size": len(server.pdf)}})
        digest = hashlib.sha256(server.photo).hexdigest()
        if self.path.endswith("/uploads"):
            if payload["expected_sha256"] != digest:
                return self.reply({}, 400)
            server.started_upload.set()
            return self.reply({"id": server.upload_id}, 201)
        if payload["entity_id"] != server.upload or payload["entity_type"] != "BTB":
            return self.reply({}, 400)
        server.event("FILE", server.file_id, "CREATE")
        return self.reply({"file": {"id": server.file_id, "byte_size": len(server.photo), "sha256": digest},
                           "link": {"entity_type": "BTB", "entity_id": server.upload}})

    def do_PUT(self):
        if (not self.path.endswith("/chunk?offset=0")
                or self.rfile.read(int(self.headers["Content-Length"])) != self.server.photo):
            return self.reply({}, 400)
        return self.reply({"received_bytes": len(self.server.photo)})


class MixedTest(unittest.TestCase):
    def setUp(self):
        self.server = TestApi()
        thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(thread.join, 3)
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        base = f"http://127.0.0.1:{self.server.server_port}"
        self.writer, self.reader = Api(base, "writer", 3), Api(base, "reader", 3)

    def test_mixed_workload_checks_both_events_and_pdfs(self):
        server = self.server
        result = run(self.writer, self.reader, server.small, server.rich, server.upload, server.photo, 1, 5)
        self.assertTrue(result["passed"])
        self.assertEqual(result["mixed"]["linked_photos"], 6)
        self.assertEqual(len(result["mixed"]["each_upload_to_sync_s"]), 1)

    def test_bad_pdf_checksum_fails(self):
        with self.assertRaisesRegex(TestRunError, "FINAL_PDF_INTEGRITY"):
            verify_pdf(self.writer, self.server.rich, {"byte_size": len(self.server.pdf), "sha256": "0" * 64})

    def test_remote_target_is_rejected(self):
        with self.assertRaises(TestRunError):
            loopback_origin("https://192.168.0.20:443")


if __name__ == "__main__":
    unittest.main()
