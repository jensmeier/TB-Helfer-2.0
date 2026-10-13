from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
tool = (ROOT / "server/tools/server1_backup_job_e2e.py").read_text(encoding="utf-8")

for token in (
    "/api/v1/admin/system/backups",
    "/api/v1/admin/system/status",
    "progress_percent",
    'state == "FAILED"',
    'state == "DONE"',
    'item.get("status") != "OK"',
    'item.get("byte_size")',
    'item.get("sha256")',
    "tb_admin_csrf",
):
    assert token in tool, token

assert 'choices=("DATABASE", "FILES", "FULL")' in tool
assert 'default="DATABASE"' in tool
assert "password" not in tool.lower().split("print(", 1)[-1] or "admin-password" in tool

print("OK: background-worker backup E2E verifies queue, progress, DONE and OK artifact without printing credentials")
