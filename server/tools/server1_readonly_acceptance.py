#!/usr/bin/env python3
"""Read-only browser/API acceptance for TB-Server 1.0 staging.

The tool intentionally performs no fachliche CREATE/UPDATE/DELETE operation.
Credentials are read from environment/arguments and are never printed.
One CookieJar is used deliberately so portal/admin coexistence is covered.
"""
from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import urllib.error
import urllib.request


def _request(
    opener: urllib.request.OpenerDirector,
    base_url: str,
    path: str,
    *,
    method: str = "GET",
    body: dict | None = None,
    csrf: str | None = None,
) -> tuple[int, str]:
    data = None
    headers = {"Accept": "application/json,text/html;q=0.9,*/*;q=0.8"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if csrf:
        headers["X-CSRF-Token"] = csrf
        headers["X-Client-Change-Id"] = "server1-readonly-acceptance"
    request = urllib.request.Request(
        base_url.rstrip("/") + path,
        data=data,
        headers=headers,
        method=method,
    )
    try:
        with opener.open(request, timeout=20) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace")


def _json(
    opener: urllib.request.OpenerDirector,
    base_url: str,
    path: str,
    *,
    method: str = "GET",
    body: dict | None = None,
    csrf: str | None = None,
) -> tuple[int, object]:
    status, raw = _request(
        opener,
        base_url,
        path,
        method=method,
        body=body,
        csrf=csrf,
    )
    try:
        parsed: object = json.loads(raw) if raw else None
    except Exception:
        parsed = raw[:300]
    return status, parsed


def _cookie(jar: http.cookiejar.CookieJar, name: str) -> str | None:
    for item in jar:
        if item.name == name:
            return item.value
    return None


def _expect(status: int, expected: int, label: str, failures: list[str]) -> None:
    print(f"{label}: HTTP {status}")
    if status != expected:
        failures.append(f"{label}: HTTP {status}, expected {expected}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=os.environ.get("TB_PORTAL_BASE_URL", ""))
    parser.add_argument("--portal-login", default=os.environ.get("TB_PORTAL_LOGIN", ""))
    parser.add_argument("--portal-password", default=os.environ.get("TB_PORTAL_PASSWORD", ""))
    parser.add_argument("--admin-login", default=os.environ.get("TB_ADMIN_LOGIN", ""))
    parser.add_argument("--admin-password", default=os.environ.get("TB_ADMIN_PASSWORD", ""))
    args = parser.parse_args()

    missing = [
        label
        for label, value in (
            ("base URL", args.base_url),
            ("portal login", args.portal_login),
            ("portal password", args.portal_password),
            ("admin login", args.admin_login),
            ("admin password", args.admin_password),
        )
        if not value
    ]
    if missing:
        parser.error("missing: " + ", ".join(missing))

    failures: list[str] = []
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    status, _ = _json(
        opener,
        args.base_url,
        "/api/v1/portal/login",
        method="POST",
        body={"login": args.portal_login, "password": args.portal_password},
    )
    _expect(status, 200, "portal login", failures)
    if failures:
        return 1

    for path, label in (
        ("/api/v1/portal/me", "portal me"),
        ("/api/v1/user/profile", "user profile"),
        ("/api/v1/user/directory", "company directory"),
        ("/api/v1/catalog", "global catalog"),
        ("/api/v1/catalog/history?limit=1", "catalog history"),
        ("/portal/v1", "portal page"),
        ("/portal/v1/worksites", "portal worksites page"),
    ):
        status, _ = _request(opener, args.base_url, path)
        _expect(status, 200, label, failures)

    status, _ = _json(
        opener,
        args.base_url,
        "/api/v1/admin/session/login",
        method="POST",
        body={"login": args.admin_login, "password": args.admin_password},
    )
    _expect(status, 200, "admin login", failures)

    expected = {
        "tb_portal_session",
        "tb_portal_csrf",
        "tb_admin_session",
        "tb_admin_csrf",
    }
    present = {item.name for item in jar}
    absent = sorted(expected - present)
    print("independent session cookies: " + ("OK" if not absent else "FEHLER"))
    if absent:
        failures.append("missing cookies: " + ",".join(absent))

    for path, label in (
        ("/api/v1/portal/me", "portal survives admin login"),
        ("/api/v1/admin/session/me", "admin me"),
        ("/api/v1/admin/system/status", "admin system status"),
        ("/api/v1/admin/companies", "admin companies"),
        ("/api/v1/admin/users", "admin users"),
        ("/admin/v1", "admin page"),
        ("/admin/v1/routes", "admin routes page"),
        ("/admin/v1/stations", "admin stations page"),
        ("/admin/v1/system", "admin system page"),
    ):
        status, _ = _request(opener, args.base_url, path)
        _expect(status, 200, label, failures)

    portal_csrf = _cookie(jar, "tb_portal_csrf")
    if portal_csrf:
        status, _ = _json(
            opener,
            args.base_url,
            "/api/v1/portal/logout",
            method="POST",
            csrf=portal_csrf,
        )
        _expect(status, 204, "portal logout", failures)
        status, _ = _request(opener, args.base_url, "/api/v1/admin/session/me")
        _expect(status, 200, "admin survives portal logout", failures)
    else:
        failures.append("portal CSRF cookie missing")

    admin_csrf = _cookie(jar, "tb_admin_csrf")
    if admin_csrf:
        status, _ = _json(
            opener,
            args.base_url,
            "/api/v1/admin/session/logout",
            method="POST",
            csrf=admin_csrf,
        )
        _expect(status, 204, "admin logout", failures)
    else:
        failures.append("admin CSRF cookie missing")

    if failures:
        print("FAILED")
        for failure in failures:
            print("- " + failure)
        return 1

    print("OK: read-only portal/admin/profile/catalog/system acceptance passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
