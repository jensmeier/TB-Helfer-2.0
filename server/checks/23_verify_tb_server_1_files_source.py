from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    files_source = read("server/tbserver/files.py")
    main_source = read("server/tbserver/main.py")
    caps = read("server/tbserver/capabilities.py")
    baseline = read("server/v1_migrations/001_server_1_0_base.sql")

    ast.parse(files_source, filename="server/tbserver/files.py")

    assertions = [
        ("CREATE TABLE tb.files", baseline),
        ("CREATE TABLE tb.file_links", baseline),
        ("CREATE TABLE tb.upload_sessions", baseline),
        ("/worksites/{worksite_id}/uploads", files_source),
        ("/uploads/{upload_id}/chunk", files_source),
        ("/uploads/{upload_id}/complete", files_source),
        ("expected_sha256", files_source),
        ("UPLOAD_OFFSET_MISMATCH", files_source),
        ("UPLOAD_SHA256_MISMATCH", files_source),
        ("os.fsync", files_source),
        ("os.replace", files_source),
        ("FILE_LINK_TARGET_NOT_FOUND", files_source),
        ('file_source = "APP" if user.get("auth_kind") == "DEVICE" else "PORTAL"', files_source),
        ('actor_device_id=user.get("device_id")', files_source),
        ('"source": file_source', files_source),
        ("files_router", main_source),
        ('"files_v1": True', caps),
        ('"chunk_upload_v1": True', caps),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Datei-/Chunkupload-Vertragsmarker: " + ", ".join(missing))

    if "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,'PORTAL'" in files_source:
        raise SystemExit("Datei-Source darf beim gemeinsamen Upload nicht fest auf PORTAL verdrahtet sein")

    print("OK: TB-Server 1.0 files/chunk upload source contract incl. APP/PORTAL classification")


if __name__ == "__main__":
    main()
