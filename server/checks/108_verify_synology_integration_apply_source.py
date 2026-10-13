from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
script = (ROOT / "server/tools/synology_integration_apply.sh").read_text(encoding="utf-8")

assert 'Vollständigen Integrations-Quellstand prüfen' in script
assert 'TB_V1_API_MEMORY_LIMIT' in script
assert 'TB_V1_SOURCE_TOOLS_DIR' in script
assert 'sync_change_wait_v1' in script
assert 'file_image_renditions_v1' in script
assert 'photo_metadata:photoMetadata' in script
assert 'tb_admin_session' in script
assert '015_file_photo_metadata.sql' in script
assert 'server1_runtime_probe.py' in script
assert 'run_backup("FULL")' in script
assert 'expected latest_known=15' in script
assert '015_file_photo_metadata.sql' in script
assert 'python -m tbserver.migrations up' in script
assert 'MIGRATIONS_OK 15/15' in script
assert 'up -d --no-deps --force-recreate api worker monitor' in script
assert 'python /app/tools/server1_runtime_probe.py' in script
assert 'DEPLOY_OK' in script

for forbidden in (
    "docker compose down",
    "docker-compose down",
    " down -v",
    "volume rm",
    "rm -rf",
    "prune",
):
    assert forbidden not in script, forbidden

print("OK: guarded Synology deploy applies backup -> migration 015 -> service recreate -> runtime probe without DB-volume teardown")
