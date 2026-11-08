from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
main = (ROOT / "server/tbserver/main.py").read_text(encoding="utf-8")
purge = (ROOT / "server/tbserver/admin_purge.py").read_text(encoding="utf-8")
releases = (ROOT / "server/templates/v1_releases.html").read_text(encoding="utf-8")
users = (ROOT / "server/templates/v1_admin_users.html").read_text(encoding="utf-8")
companies = (ROOT / "server/templates/v1_admin_companies.html").read_text(encoding="utf-8")
devices = (ROOT / "server/templates/v1_admin_devices.html").read_text(encoding="utf-8")
worksites = (ROOT / "server/templates/v1_worksites.html").read_text(encoding="utf-8")
rules = (ROOT / "docs/TB-HELFER_PROJEKTREGELN.md").read_text(encoding="utf-8")

checks = [
    ("purge router included", "admin_purge_router", main),
    ("release draft purge endpoint", '@router.delete("/releases/{release_id}"', purge),
    ("device purge endpoint", '@router.delete("/devices/{device_id}"', purge),
    ("worksite purge endpoint", '@router.delete("/worksites/{worksite_id}"', purge),
    ("user purge endpoint", '@router.delete("/users/{user_id}"', purge),
    ("company purge endpoint", '@router.delete("/companies/{company_id}"', purge),
    ("typed permanent confirmation", "ENDGUELTIG LOESCHEN", purge),
    ("production survey-original protection", "COMPANY_PURGE_ORIGINALS_PROTECTED", purge),
    ("release draft delete UI", "Entwurf komplett löschen", releases),
    ("user permanent delete UI", "Komplett löschen", users),
    ("company permanent delete UI", "Komplett löschen", companies),
    ("device permanent delete UI", "Komplett löschen", devices),
    ("worksite permanent delete UI", "Komplett löschen", worksites),
    ("project purge rule", "Endgültig / komplett löschen", rules),
]

missing = [name for name, needle, haystack in checks if needle not in haystack]
if missing:
    raise SystemExit("Admin purge source check failed: " + ", ".join(missing))

print("OK admin permanent purge source")
