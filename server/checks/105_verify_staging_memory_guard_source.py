from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
compose = (ROOT / "server/docker-compose.synology-staging.v1.yml").read_text(encoding="utf-8")
pdf = (ROOT / "server/tbserver/btb_pdf.py").read_text(encoding="utf-8")
renditions = (ROOT / "server/tbserver/image_renditions.py").read_text(encoding="utf-8")

assert "mem_limit: ${TB_V1_API_MEMORY_LIMIT:-512m}" in compose
assert "PDF_PHOTO_MAX_SIZE_PX = (1600, 1200)" in pdf
assert '"preview": (1280, 1280, 82)' in renditions

print("OK: staging API has cgroup memory guard plus bounded PDF/image rendering")
