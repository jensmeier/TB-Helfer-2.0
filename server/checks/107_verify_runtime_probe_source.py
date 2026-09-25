from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
compose = (ROOT / "server/docker-compose.synology-staging.v1.yml").read_text(encoding="utf-8")
probe = (ROOT / "server/tools/server1_runtime_probe.py").read_text(encoding="utf-8")

assert 'TB_V1_SOURCE_TOOLS_DIR:-./tools' in compose
assert ':/app/tools:ro' in compose
assert "migrations.get(\"latest_known\") != 15" in probe
assert "photo_metadata" in probe
assert "file_image_renditions_v1" in probe
assert "sync_change_wait_v1" in probe
assert "API cgroup memory limit is not active" in probe
assert 'render_image_rendition(source, preview, "preview")' in probe

print("OK: staging runtime probe covers DB, migration, contracts, rendition and memory guard")
