from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

catalog = (ROOT / "tbserver" / "survey_catalog.py").read_text(encoding="utf-8")
admin_web = (ROOT / "tbserver" / "admin_web.py").read_text(encoding="utf-8")
base = (ROOT / "templates" / "v1_base.html").read_text(encoding="utf-8")
dashboard = (ROOT / "templates" / "v1_admin_dashboard.html").read_text(encoding="utf-8")
overview = (ROOT / "templates" / "v1_admin_survey_overview.html").read_text(encoding="utf-8")
requirements = (ROOT / "requirements.txt").read_text(encoding="utf-8")

assert "pyproj" not in requirements.lower()
assert "from pyproj" not in catalog
for token in (
    "_DBREF_ZONE_SRIDS = {2: 5682, 3: 5683, 4: 5684, 5: 5685}",
    "ST_Transform",
    '@router.get("/coverage")',
    '@router.get("/coverage/{axis_id}/geometry")',
    '"EPSG:4326"',
    "unsupported_axis_count",
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
    "PostGIS · DB_REF → WGS84",
    "Deutschland gesamt",
    "Mausrad = Zoom",
):
    assert token in overview, token

print("OK: PostGIS Germany survey coverage overview source")
