#!/usr/bin/env python3
"""One bounded BTB comparison on a disposable loopback TB-Helfer instance."""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import threading
from time import monotonic
from urllib.error import HTTPError
from urllib.parse import urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener
from uuid import UUID, uuid4

from sync_mixed_loadtest import validate_payload


class TestRunError(ValueError):
    pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def loopback_origin(value):
    url = urlsplit(value)
    if (url.scheme not in {"http", "https"} or not url.hostname or url.port is None
            or url.username is not None or url.password is not None
            or url.path not in {"", "/"} or url.query or url.fragment
            or not ipaddress.ip_address(url.hostname).is_loopback):
        raise TestRunError("LOOPBACK_TEST_ORIGIN_REQUIRED")
    return value.rstrip("/")


class Api:
    def __init__(self, base, token, timeout):
        self.base = base
        self.token = token
        self.timeout = timeout
        self.opener = build_opener(ProxyHandler({}), NoRedirect())

    def request(self, method, path, *, data=None, change=None, binary=False):
        headers = {"Authorization": "Bearer " + self.token, "Accept-Encoding": "identity"}
        if isinstance(data, dict):
            data = json.dumps(data).encode()
            headers["Content-Type"] = "application/json"
        elif data is not None:
            headers["Content-Type"] = "application/octet-stream"
        if change:
            headers["X-Client-Change-Id"] = change
        req = Request(self.base + "/api/v1" + path, data=data, headers=headers, method=method)
        with self.opener.open(req, timeout=self.timeout) as response:
            limit = (64 if binary else 8) * 1024 * 1024
            body = response.read(limit + 1)
        if len(body) > limit:
            raise TestRunError("RESPONSE_TOO_LARGE")
        return body if binary else json.loads(body)


def require(condition, reason):
    if not condition:
        raise TestRunError(reason)


def inspect_fixture(api, btb_id, *, lead=False):
    doc = api.request("GET", f"/btbs/{btb_id}")
    row = doc["btb"]
    require(row["id"] == btb_id and row["status"] == "ACTIVE", "ACTIVE_TEST_BTB_REQUIRED")
    if lead:
        require(doc["current_user"]["can_finalize_btb"] is True, "TEST_LEAD_REQUIRED")
    return str(UUID(row["worksite_id"])), len(doc["photos"])


def finalize(api, btb_id):
    started = monotonic()
    result = api.request("POST", f"/btbs/{btb_id}/finalize", data={"allow_unfinished": False},
                         change="mixed-final-" + str(uuid4()))
    acknowledged = monotonic()
    pdf = result.get("final_pdf")
    require(result.get("id") == btb_id and result.get("status") == "FINALIZED" and isinstance(pdf, dict),
            "FINALIZE_CONTRACT")
    return started, acknowledged, pdf


def verify_pdf(api, btb_id, metadata):
    started = monotonic()
    pdf = api.request("GET", f"/btbs/{btb_id}/final-pdf", binary=True)
    require(pdf.startswith(b"%PDF-") and len(pdf) == metadata["byte_size"]
            and hashlib.sha256(pdf).hexdigest() == metadata["sha256"], "FINAL_PDF_INTEGRITY")
    return round(monotonic() - started, 3)


def upload_photos(api, worksite, btb_id, data, count):
    started = monotonic()
    digest = hashlib.sha256(data).hexdigest()
    results = {}
    for _ in range(count):
        ident = str(uuid4())
        photo_started = monotonic()
        created = api.request("POST", f"/worksites/{worksite}/uploads", change="mixed-create-" + ident,
                              data={"target_kind": "PHOTO", "mime_type": "image/jpeg",
                                    "original_name": f"mixed-test-{ident}.jpg", "expected_bytes": len(data),
                                    "expected_sha256": digest, "chunk_size": 8 * 1024 * 1024})
        path = "/uploads/" + str(UUID(created["id"]))
        session = api.request("GET", path)
        require(session["status"] == "OPEN" and session["received_bytes"] == 0, "UPLOAD_SESSION_CONTRACT")
        api.request("PUT", path + "/chunk?offset=0", data=data)
        result = api.request("POST", path + "/complete", change="mixed-complete-" + ident,
                             data={"entity_type": "BTB", "entity_id": btb_id, "purpose": "ATTACHMENT"})
        file = result["file"]
        require(file["sha256"] == digest and file["byte_size"] == len(data)
                and result["link"]["entity_type"] == "BTB" and result["link"]["entity_id"] == btb_id,
                "UPLOAD_FILE_INTEGRITY")
        file_id = str(UUID(file["id"]))
        require(file_id not in results, "DUPLICATE_UPLOAD_FILE_ID")
        results[file_id] = (photo_started, monotonic())
    return started, monotonic(), results


def poll(api, cursor, wait):
    path = "/sync/changes/wait?" + urlencode({"after": cursor, "limit": 50,
                                             "entity_types": "BTB,FILE", "wait_seconds": wait})
    doc = api.request("GET", path)
    return validate_payload(doc, cursor), doc


def caught_up_cursor(api):
    cursor = 0
    for _ in range(200):
        cursor, doc = poll(api, cursor, 0)
        if doc["caught_up"]:
            return cursor
    raise TestRunError("TEST_FEED_PREFLIGHT_LIMIT")


def run(lead, observer, small, rich, upload_btb, photo, count, observe_seconds):
    require(len({small, rich, upload_btb}) == 3, "THREE_DISTINCT_TEST_BTBS_REQUIRED")
    _, small_photos = inspect_fixture(lead, small, lead=True)
    rich_site, rich_photos = inspect_fixture(lead, rich, lead=True)
    upload_site, _ = inspect_fixture(lead, upload_btb, lead=True)
    require(small_photos <= 1 and rich_photos >= 6, "SMALL_AND_PHOTO_RICH_FIXTURES_REQUIRED")
    require(inspect_fixture(observer, rich)[0] == rich_site
            and inspect_fixture(observer, upload_btb)[0] == upload_site, "OBSERVER_NEEDS_BOTH_TEST_BTBS")
    cursor = caught_up_cursor(observer)
    start, ack, pdf = finalize(lead, small)
    baseline = {"finalize_s": round(ack - start, 3), "pdf_bytes": pdf["byte_size"],
                "pdf_verify_s": verify_pdf(lead, small, pdf), "linked_photos": small_photos}
    barrier = threading.Barrier(3)
    seen = {}

    def concurrent(fn, *args):
        barrier.wait(timeout=5)
        return fn(*args)

    with ThreadPoolExecutor(max_workers=2) as pool:
        closing = pool.submit(concurrent, finalize, lead, rich)
        uploading = pool.submit(concurrent, upload_photos, Api(lead.base, lead.token, lead.timeout),
                                upload_site, upload_btb, photo, count)
        barrier.wait(timeout=5)
        deadline = monotonic() + observe_seconds
        while monotonic() < deadline:
            if closing.done() and uploading.done():
                finish_start, finish_ack, rich_pdf = closing.result()
                upload_start, upload_ack, files = uploading.result()
                expected = [("BTB", rich, "FINALIZE"), *(("FILE", key, "CREATE") for key in files)]
                if all(key in seen for key in expected):
                    break
            cursor, page = poll(observer, cursor, 2)
            received = monotonic()
            for event in page["changes"]:
                if event["entity_type"] in {"FILE", "BTB"}:
                    seen.setdefault((event["entity_type"], event["entity_id"], event["operation"]), received)
            require(len(seen) <= 2000, "TEST_FEED_EVENT_LIMIT")
        else:
            raise TestRunError("EXPECTED_DELTA_NOT_OBSERVED")

    def elapsed(start, end):
        return round(end - start, 3)

    overlap = max(0, min(finish_ack, upload_ack) - max(finish_start, upload_start))
    require(overlap > 0, "WRITER_WINDOWS_DID_NOT_OVERLAP")
    return {"passed": True, "baseline": baseline,
            "mixed": {"finalize_s": elapsed(finish_start, finish_ack),
                      "pdf_bytes": rich_pdf["byte_size"], "pdf_verify_s": verify_pdf(lead, rich, rich_pdf),
                      "linked_photos": rich_photos, "upload_count": count,
                      "upload_s": elapsed(upload_start, upload_ack), "client_overlap_s": round(overlap, 3),
                      "finalize_to_sync_s": elapsed(finish_start, seen[("BTB", rich, "FINALIZE")]),
                      "each_upload_to_sync_s": [elapsed(begin, seen[("FILE", key, "CREATE")])
                                               for key, (begin, _) in files.items()]}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--confirm-disposable-test", action="store_true", required=True)
    parser.add_argument("--small-btb", required=True, type=UUID)
    parser.add_argument("--rich-btb", required=True, type=UUID)
    parser.add_argument("--upload-btb", required=True, type=UUID)
    parser.add_argument("--jpeg", required=True, type=Path)
    parser.add_argument("--uploads", type=int, default=3, choices=range(1, 11))
    parser.add_argument("--observe-seconds", type=int, default=60, choices=range(10, 181), metavar="10..180")
    args = parser.parse_args()
    try:
        base = loopback_origin(args.base_url)
        writer, reader = os.environ.get("TB_MIXED_WRITER_TOKEN"), os.environ.get("TB_MIXED_READER_TOKEN")
        require(writer and reader and writer != reader, "DISTINCT_TEST_DEVICE_TOKENS_REQUIRED")
        with args.jpeg.open("rb") as source:
            jpeg = source.read(2_000_001)
        require(jpeg.startswith(b"\xff\xd8") and jpeg.endswith(b"\xff\xd9") and len(jpeg) <= 2_000_000,
                "TEST_JPEG_MAX_2MB_REQUIRED")
        result = run(Api(base, writer, 45), Api(base, reader, 5), str(args.small_btb),
                     str(args.rich_btb), str(args.upload_btb), jpeg, args.uploads, args.observe_seconds)
    except HTTPError as exc:
        result = {"passed": False, "http_status": exc.code, "error": "HTTP_ERROR"}
    except TestRunError as exc:
        result = {"passed": False, "error": str(exc)}
    except (KeyError, TypeError, ValueError, OSError, threading.BrokenBarrierError) as exc:
        # Never output server bodies, paths, request URLs or credentials.
        result = {"passed": False, "error": type(exc).__name__}
    print(json.dumps(result))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
