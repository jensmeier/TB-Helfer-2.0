from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    runner = read("server/tools/server1_post_deploy_e2e.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    required = [
        "server1_runtime_probe.py",
        "session_split_e2e.py",
        "server1_readonly_acceptance.py",
        "server1_backup_job_e2e.py",
        "sync_wait_loadtest.py",
        "POST_DEPLOY_E2E_OK",
        "getpass.getpass",
        'child_env["TB_PORTAL_PASSWORD"]',
        'child_env["TB_ADMIN_PASSWORD"]',
        'child_env["TB_DEVICE_TOKEN"]',
        "--skip-backup",
        "--skip-loadtest",
    ]
    missing = [needle for needle in required if needle not in runner]
    if missing:
        raise SystemExit("Post-deploy E2E runner missing markers: " + ", ".join(missing))

    forbidden = [
        '"--portal-password"',
        '"--admin-password"',
        '"--token"',
        "shell=True",
    ]
    present = [needle for needle in forbidden if needle in runner]
    if present:
        raise SystemExit(
            "Post-deploy E2E runner must not place secrets on child command lines: "
            + ", ".join(present)
        )

    if "111_verify_post_deploy_e2e_source.py" not in workflow:
        raise SystemExit("Server workflow does not execute post-deploy E2E source check 111")

    print("OK: post-deploy E2E runner is ordered and keeps secrets off child command lines")


if __name__ == "__main__":
    main()
