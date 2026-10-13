from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
backup = (ROOT / "server/tbserver/backup.py").read_text(encoding="utf-8")

assert "def _storage_backup_filter" in backup
assert 'if ".renditions" in Path(info.name).parts:' in backup
assert "filter=_storage_backup_filter" in backup
assert "FILES_BACKUP_EMPTY" in backup

print("OK: FILES/FULL backups exclude derived image rendition caches")
