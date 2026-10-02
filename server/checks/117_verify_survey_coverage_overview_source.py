from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

catalog = (ROOT / "tbserver" / "survey_catalog.py").read_text(encoding="utf-8")
admin_web = (ROOT / "tbserver" / "admin_web.py").read_text(encoding="utf-8")
base = (ROOT / "templates" / "v1_base.html").read_text(encoding="utf-8")
dashboard = (ROOT / "templates" / "v1_admin_dashboard.html").read_text(encoding="utf-8")
overview = (ROOT / "templates" / "v1_admin_survey_overview.html").read_text(encoding="utf-8")
requirements = (ROOT / "requirements.txt").read_text(encoding="utf-8")

for token in (
    "pyproj==3.7.2",
):
    assert token in requirements, token

for token in (
    "from pyproj import Transformer",
    'def _dbref_zone_epsg(x: float)',
    "{2: 5682, 3: 5683, 4: 5684, 5: 5685}",
    '@router.get("/coverage")',
    '"EPSG:4326"',
    '"DB_REF / 3-degree Gauss-Kruger zones 2-5"',
    "unsupported_coordinate_count",
    "_segment_km_quality",
):
    assert token in catalog, token

for token in (
    '@router.get("/admin/v1/survey-overview")',
    '"v1_admin_survey_overview.html"',
):
    assert token in admin_web, token

assert 'href="/admin/v1/survey-overview">Trassendatenübersicht</a>' in base
assert 'href="/admin/v1/survey-overview"' in dashboard

for token in (
    "Trassendatenübersicht Deutschland",
    'id="coverageSearch"',
    'id="germanyMap"',
    "/api/v1/survey-catalog/coverage",
    "Strecke, Bahnhof/Betriebsstelle, Gleis/Achse oder Weiche/WVB",
    "Deutschlandumriss: Natural Earth",
    "AUTOMATISCH",
    "MANUELL",
    "FEHLT",
):
    assert token in overview, token

print("OK: Germany survey coverage overview source")
