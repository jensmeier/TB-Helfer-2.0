"""Non-secret provenance for matching an update backup to its server package."""
from __future__ import annotations

import hashlib
from pathlib import Path

from . import SERVER_VERSION
from .migrations import discover_migrations


def runtime_identity() -> dict:
    root = Path(__file__).resolve().parents[1]
    files = []
    for folder, pattern in (("tbserver", "*.py"), ("templates", "*"), ("v1_migrations", "*.sql")):
        files.extend(p for p in (root / folder).rglob(pattern) if p.is_file())
    files.extend(p for name in ("requirements.txt", "Dockerfile.v1", "app.py")
                 if (p := root / name).is_file())
    digest = hashlib.sha256()
    for path in sorted(files):
        digest.update(path.relative_to(root).as_posix().encode() + b"\0")
        digest.update(hashlib.sha256(path.read_bytes()).digest())
    return {"format": "tb-runtime-v1", "server_version": SERVER_VERSION,
            "source_sha256": digest.hexdigest(),
            "migrations": [{"version": m.version, "name": m.name, "sha256": m.checksum}
                           for m in discover_migrations()]}


def check_update_version(manifest: dict) -> dict:
    saved = manifest.get("runtime")
    if not isinstance(saved, dict):
        raise ValueError("RECOVERY_RUNTIME_METADATA_MISSING")
    if saved != runtime_identity():
        raise ValueError("RECOVERY_RUNTIME_VERSION_MISMATCH")
    return saved
