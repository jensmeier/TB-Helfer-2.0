from __future__ import annotations

import http.cookiejar
import json
import os
import secrets
import urllib.error
import urllib.request


BASE = os.getenv("TB_TEST_BASE_URL", "http://127.0.0.1:8080")
ADMIN_LOGIN = os.environ["TB_BOOTSTRAP_ADMIN_LOGIN"]
ADMIN_PASSWORD = os.environ["TB_BOOTSTRAP_ADMIN_PASSWORD"]


def opener():
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar)), jar


def json_call(client, path: str, method: str = "GET", payload=None, headers=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req_headers = {"Accept": "application/json"}
    if payload is not None:
        req_headers["Content-Type"] = "application/json"
    if headers:
        req_headers.update(headers)
    request = urllib.request.Request(BASE + path, data=data, headers=req_headers, method=method)
    with client.open(request, timeout=5) as response:
        body = response.read()
        return response.status, json.loads(body.decode("utf-8")) if body else None


def csrf_from(jar) -> str:
    for cookie in jar:
        if cookie.name == "tb_portal_csrf":
            return cookie.value
    raise AssertionError("CSRF cookie fehlt")


def main() -> None:
    admin, admin_jar = opener()
    status, login = json_call(
        admin,
        "/api/v1/portal/login",
        "POST",
        {"login": ADMIN_LOGIN, "password": ADMIN_PASSWORD},
    )
    assert status == 200 and login["user"]["company_role"] == "ADMIN", login
    csrf = csrf_from(admin_jar)

    status, company = json_call(
        admin,
        "/api/v1/admin/companies",
        "POST",
        {"name": "CI Testfirma", "city": "Teststadt"},
        {"X-CSRF-Token": csrf},
    )
    assert status == 201, company

    status, user = json_call(
        admin,
        "/api/v1/admin/users",
        "POST",
        {
            "company_id": company["id"],
            "display_name": "CI Chef",
            "login_name": "ci-chef",
            "company_role": "CHEF",
        },
        {"X-CSRF-Token": csrf},
    )
    assert status == 201 and user["company_role"] == "CHEF", user

    status, activation = json_call(
        admin,
        f"/api/v1/admin/users/{user['id']}/portal-activation",
        "POST",
        {},
        {"X-CSRF-Token": csrf},
    )
    assert status == 200 and activation["activation_token"], activation

    chef_password = secrets.token_urlsafe(20) + "Aa1"
    anonymous, _ = opener()
    request = urllib.request.Request(
        BASE + "/api/v1/portal/activate",
        data=json.dumps({"token": activation["activation_token"], "password": chef_password}).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with anonymous.open(request, timeout=5) as response:
        assert response.status == 204, response.status

    chef, _ = opener()
    status, chef_login = json_call(
        chef,
        "/api/v1/portal/login",
        "POST",
        {"login": "ci-chef", "password": chef_password},
    )
    assert status == 200 and chef_login["user"]["company_role"] == "CHEF", chef_login
    status, me = json_call(chef, "/api/v1/portal/me")
    assert status == 200 and me["company_role"] == "CHEF", me
    assert me["company_id"] == company["id"], me

    print("OK: portal login, admin company/user creation, CHEF activation")


if __name__ == "__main__":
    main()
