from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = ROOT / "server" / "templates" / "v1_admin_survey_import.html"
ADMIN_WEB = ROOT / "server" / "tbserver" / "admin_web.py"

template = TEMPLATE.read_text(encoding="utf-8")
admin_web = ADMIN_WEB.read_text(encoding="utf-8")

for token in (
    'id="defaultStationPicker"',
    'id="applyDefaultStation"',
    'class="station-picker decision-station-picker"',
    "/api/v1/admin/station-reference?q=",
    "function wireStationPicker(root)",
    "station_code:settings.station_code",
    "station_name:settings.station_name",
    "object_key:settings.object_key",
    "Bahnhof/Betriebsstelle bitte aus der Referenzliste auswählen",
    "data-station-code",
    "data-station-name",
    "<label>Speicherziel</label>",
    "Gemeinsamer Referenzbestand",
    "cell.hidden=global;",
    "card.classList.toggle('global-target',global)",
):
    assert token in template, token

assert "metadata->>'station_code'" in admin_web
assert "metadata->>'station_name'" in admin_web
assert "Bahnhof / Betriebsstelle</th>" in template

print("PASS survey import station assignment source")
