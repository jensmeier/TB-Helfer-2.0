#!/usr/bin/env python3
"""One-shot post-deploy acceptance for TB-Server 1.0 staging.

Run inside the API container after synology_integration_apply.sh returned DEPLOY_OK.
Secrets are accepted from environment or prompted interactively with getpass and are
never placed on child-process command lines or printed by this orchestrator.
"""
from __future__ import annotations

import argparse
import getpass
import os
import subprocess
import sys
from pathlib import Path


TOOLS = Path(__file__).resolve().parent


def _secret(env_name: str, label: str) -> str:
    value = os.environ.get(env_name, "")
    if value:
        return value
    if not sys.stdin.isatty():
        raise RuntimeError(
            f"{env_name} is required in non-interactive mode; secret value was not provided"
        )
    value = getpass.getpass(label + ": ")
    if not value:
        raise RuntimeError(f"{env_name} is required")
    return value


def _required(value: str, label: str) -> str:
    value = value.strip()
    if not value:
        raise RuntimeError(f"{label} is required")
    return value


def _run(label: str, script: str, args: list[str], env: dict[str, str]) -> None:
    path = TOOLS / script
    if not path.is_file():
        raise RuntimeError(f"required tool is missing: {path}")
    print(f"\n=== {label} ===", flush=True)
    completed = subprocess.run(
        [sys.executable, str(path), *args],
        env=env,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(f"{label} failed with exit code {completed.returncode}")
    print(f"PASS: {label}", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--portal-base-url", default=os.environ.get("TB_PORTAL_BASE_URL", ""))
    parser.add_argument("--server-base-url", default=os.environ.get("TB_SERVER_BASE_URL", ""))
    parser.add_argument("--portal-login", default=os.environ.get("TB_PORTAL_LOGIN", ""))
    parser.add_argument("--admin-login", default=os.environ.get("TB_ADMIN_LOGIN", ""))
    parser.add_argument("--backup-kind", choices=("DATABASE", "FILES", "FULL"), default="DATABASE")
    parser.add_argument("--clients", type=int, default=32)
    parser.add_argument("--wait-seconds", type=float, default=10.0)
    parser.add_argument("--skip-backup", action="store_true")
    parser.add_argument("--skip-loadtest", action="store_true")
    args = parser.parse_args()

    if args.clients < 1 or args.clients > 255:
        parser.error("--clients must be between 1 and 255")
    if args.wait_seconds < 0 or args.wait_seconds > 30:
        parser.error("--wait-seconds must be between 0 and 30")

    try:
        portal_base_url = _required(args.portal_base_url, "portal base URL")
        server_base_url = _required(args.server_base_url, "server base URL")
        portal_login = _required(args.portal_login, "portal login")
        admin_login = _required(args.admin_login, "admin login")

        child_env = os.environ.copy()
        child_env["TB_PORTAL_BASE_URL"] = portal_base_url
        child_env["TB_SERVER_BASE_URL"] = server_base_url
        child_env["TB_PORTAL_LOGIN"] = portal_login
        child_env["TB_ADMIN_LOGIN"] = admin_login
        child_env["TB_PORTAL_PASSWORD"] = _secret(
            "TB_PORTAL_PASSWORD", "Portal-Passwort (wird nicht angezeigt)"
        )
        child_env["TB_ADMIN_PASSWORD"] = _secret(
            "TB_ADMIN_PASSWORD", "Admin-Passwort (wird nicht angezeigt)"
        )
        if not args.skip_loadtest:
            child_env["TB_DEVICE_TOKEN"] = _secret(
                "TB_DEVICE_TOKEN", "Geräte-Token für Delta-Test (wird nicht angezeigt)"
            )

        _run("Runtime-Probe", "server1_runtime_probe.py", [], child_env)
        _run(
            "Same-Browser Session-Split",
            "session_split_e2e.py",
            [
                "--base-url", portal_base_url,
                "--portal-login", portal_login,
                "--admin-login", admin_login,
            ],
            child_env,
        )
        _run(
            "Read-only Portal/Admin Acceptance",
            "server1_readonly_acceptance.py",
            [
                "--base-url", portal_base_url,
                "--portal-login", portal_login,
                "--admin-login", admin_login,
            ],
            child_env,
        )
        if not args.skip_backup:
            _run(
                "Worker/Backup E2E",
                "server1_backup_job_e2e.py",
                [
                    "--base-url", portal_base_url,
                    "--admin-login", admin_login,
                    "--kind", args.backup_kind,
                ],
                child_env,
            )
        if not args.skip_loadtest:
            _run(
                f"Delta Long-Poll Loadtest ({args.clients} Clients)",
                "sync_wait_loadtest.py",
                [
                    "--base-url", server_base_url,
                    "--clients", str(args.clients),
                    "--wait-seconds", str(args.wait_seconds),
                ],
                child_env,
            )
    except RuntimeError as exc:
        print(f"FAILED: {exc}")
        return 1

    print("\n=== POST_DEPLOY_E2E_OK ===")
    print("Runtime, sessions, read-only acceptance and requested practical probes passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
