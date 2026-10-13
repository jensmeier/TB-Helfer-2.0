"""Committed file references shared by full backup and restore verification.

No database connection or global state: caller supplies the snapshot cursor.
Open uploads and derived caches are deliberately not durable references.
"""
from __future__ import annotations

import hashlib
import os
from pathlib import Path, PurePosixPath
import re
import stat
import tarfile
import tempfile


class InventoryError(RuntimeError):
    pass


def read_inventory(cur) -> list[dict]:
    cur.execute("""
        SELECT storage_key, byte_size, sha256 FROM tb.files WHERE deleted_at IS NULL
        UNION ALL
        SELECT storage_key, byte_size, sha256 FROM tb.survey_originals
        UNION ALL
        SELECT storage_key, byte_size, sha256 FROM tb.releases WHERE storage_key IS NOT NULL
        ORDER BY storage_key
    """)
    by_key = {}
    for row in cur.fetchall():
        key = row["storage_key"]
        path = PurePosixPath(key) if isinstance(key, str) else None
        if (not path or not path.parts or not key or path.is_absolute() or ".." in path.parts
                or str(path) != key or "\\" in key or "\x00" in key
                or ".renditions" in path.parts or ".uploads" in path.parts):
            raise InventoryError("BACKUP_REFERENCE_PATH_INVALID")
        size, digest = row["byte_size"], row["sha256"]
        if (not isinstance(size, int) or isinstance(size, bool) or size < 0
                or not isinstance(digest, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", digest)):
            raise InventoryError("BACKUP_REFERENCE_METADATA_INVALID")
        entry = {"storage_key": key, "byte_size": size, "sha256": digest.lower()}
        if key in by_key and by_key[key] != entry:
            raise InventoryError("BACKUP_REFERENCE_CONFLICT")
        by_key[key] = entry
    return list(by_key.values())


def _open_reference(root: Path, key: str):
    # Traverse with directory descriptors and O_NOFOLLOW, including the root.
    # A concurrent rename cannot redirect an already opened directory to a link.
    parts = PurePosixPath(key).parts
    if not parts or PurePosixPath(key).is_absolute() or ".." in parts:
        raise InventoryError("BACKUP_REFERENCE_PATH_INVALID")
    directory = os.open(root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        for part in parts[:-1]:
            child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=directory)
            os.close(directory)
            directory = child
        fd = os.open(parts[-1], os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
        try:
            if not stat.S_ISREG(os.fstat(fd).st_mode):
                raise InventoryError("BACKUP_REFERENCE_NOT_REGULAR")
            return os.fdopen(fd, "rb")
        except BaseException:
            os.close(fd)
            raise
    finally:
        os.close(directory)


def _copy_verified(root: Path, entry: dict, out=None) -> None:
    digest = hashlib.sha256()
    size = 0
    try:
        with _open_reference(root, entry["storage_key"]) as source:
            for block in iter(lambda: source.read(1024 * 1024), b""):
                size += len(block)
                if size > entry["byte_size"]:
                    raise InventoryError("BACKUP_REFERENCE_SIZE_MISMATCH")
                digest.update(block)
                if out is not None:
                    out.write(block)
    except OSError as exc:
        raise InventoryError("BACKUP_REFERENCE_UNAVAILABLE") from exc
    if size != entry["byte_size"] or digest.hexdigest() != entry["sha256"]:
        raise InventoryError("BACKUP_REFERENCE_CONTENT_MISMATCH")


def archive_inventory(root: Path, target: Path, entries: list[dict]) -> None:
    if root.is_symlink() or not root.is_dir():
        raise InventoryError("BACKUP_STORAGE_ROOT_UNAVAILABLE")
    target.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(target, "w:gz") as archive:
        base = tarfile.TarInfo("storage")
        base.type, base.mode = tarfile.DIRTYPE, 0o700
        archive.addfile(base)
        for entry in entries:
            # Stage one file, verify the exact bytes, then archive that copy.
            # Hashing before archive.add(path) would leave a replacement race.
            with tempfile.TemporaryFile(dir=target.parent) as staged:
                _copy_verified(root, entry, staged)
                staged.seek(0)
                info = tarfile.TarInfo("storage/" + entry["storage_key"])
                info.size, info.mode = entry["byte_size"], 0o600
                archive.addfile(info, staged)


def verify_inventory(root: Path, entries: list[dict]) -> dict:
    for entry in entries:
        _copy_verified(root, entry)
    return {"verified_files": len(entries), "verified_bytes": sum(e["byte_size"] for e in entries)}
