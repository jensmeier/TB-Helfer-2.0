#!/usr/bin/env python3
"""Same-browser admin/portal session E2E for TB-Server 1.0 staging.

Credentials are read from environment/arguments and are never printed.
The script uses one CookieJar on purpose to reproduce a single browser profile.
"""
from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import urllib.error
import urllib.request


def json_request(
    opener: urllib.request.OpenerDirector,
    base_url: str,
    path: str,
    *,
    method: str = "GET",
    body: dict | None = None,
    csrf_cookie: str | None = None,
) -> tuple[int, object]:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if csrf_cookie:
        headers["X-CSRF-Token"] = csrf_cookie
        headers["X-Client-Change-Id"] = "session-split-e2e"

    request = urllib.request.Request(
        base_url.rstrip("/") + path,
        data=data,
        headers=headers,
        method=method,
    )
    try:
        with opener.open(request, timeout=15) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", "replace")
        try:
            payload = json.loads(raw)
        except Exception:
            payload = raw[:300]
        return exc.code, payload


def cookie_value(jar: http.cookiejar.CookieJar, name: str) -> str | None:
    for cookie in jar:
        if cookie.name == name:
            return cookie.value
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=os.environ.get("TB_PORTAL_BASE_URL", ""))
    parser.add_argument("--portal-login", default=os.environ.get("TB_PORTAL_LOGIN", ""))
    parser.add_argument("--portal-password", default=os.environ.get("TB_PORTAL_PASSWORD", ""))
    parser.add_argument("--admin-login", default=os.environ.get("TB_ADMIN_LOGIN", ""))
    parser.add_argument("--admin-password", default=os.environ.get("TB_ADMIN_PASSWORD", ""))
    args = parser.parse_args()

    missing = [
        name
        for name, value in (
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

    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    status, _ = json_request(
        opener,
        args.base_url,
        "/api/v1/portal/login",
        method="POST",
        body={"login": args.portal_login, "password": args.portal_password},
    )
    if status != 200:
        print(f"FAIL portal login HTTP {status}")
        return 1

    status, _ = json_request(
        opener,
        args.base_url,
        "/api/v1/admin/session/login",
        method="POST",
        body={"login": args.admin_login, "password": args.admin_password},
    )
    if status != 200:
        print(f"FAIL admin login HTTP {status}")
        return 1

    expected_cookies = {
        "tb_portal_session",
        "tb_portal_csrf",
        "tb_admin_session",
        "tb_admin_csrf",
    }
    present = {cookie.name for cookie in jar}
    missing_cookies = sorted(expected_cookies - present)
    if missing_cookies:
        print("FAIL missing independent cookies: " + ", ".join(missing_cookies))
        return 1

    portal_status, portal_me = json_request(opener, args.base_url, "/api/v1/portal/me")
    admin_status, admin_me = json_request(opener, args.base_url, "/api/v1/admin/session/me")
    if portal_status != 200 or admin_status != 200:
        print(f"FAIL simultaneous sessions portal={portal_status} admin={admin_status}")
        return 1
    if not isinstance(portal_me, dict) or not isinstance(admin_me, dict):
        print("FAIL invalid session identity response")
        return 1
    if portal_me.get("id") == admin_me.get("id"):
        print("FAIL test requires distinct portal/admin users")
        return 1

    portal_csrf = cookie_value(jar, "tb_portal_csrf")
    status, _ = json_request(
        opener,
        args.base_url,
        "/api/v1/portal/logout",
        method="POST",
        csrf_cookie=portal_csrf,
    )
    if status != 204:
        print(f"FAIL portal logout HTTP {status}")
        return 1

    admin_status, _ = json_request(opener, args.base_url, "/api/v1/admin/session/me")
    if admin_status != 200:
        print(f"FAIL admin session was lost after portal logout: HTTP {admin_status}")
        return 1

    admin_csrf = cookie_value(jar, "tb_admin_csrf")
    status, _ = json_request(
        opener,
        args.base_url,
        "/api/v1/admin/session/logout",
        method="POST",
        csrf_cookie=admin_csrf,
    )
    if status != 204:
        print(f"FAIL admin logout HTTP {status}")
        return 1

    print("OK: portal and admin sessions coexist in one browser cookie jar and logout independently")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
