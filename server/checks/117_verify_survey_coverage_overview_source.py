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
    'def _segment_physical_length_m(',
    'def _coverage_summary_data()',
    '@router.get("/coverage/summary")',
    '@router.get("/coverage")',
    'include_overview: bool = Query(default=False)',
    'include_quality: bool = Query(default=False)',
    '@router.get("/coverage/{axis_id}/geometry")',
    '"manual_mobile_length_m"',
    '"parallel_tracks_count_separately": True',
    '"EPSG:4326"',
    "unsupported_axis_count",
    '"overview_segments": overview_segments',
    '"overview_included": include_overview',
):
    assert token in catalog, token

for token in (
    '@router.get("/admin/v1/survey-overview")',
    '"v1_admin_survey_overview.html"',
):
    assert token in admin_web, token

assert 'href="/admin/v1/survey-overview">Streckennetz</a>' in base
assert 'href="/admin/v1/survey-overview"' in dashboard

for token in (
    "Trassendatenübersicht",
    'id="coverageSearch"',
    'id="coverageMap"',
    'id="kpiTotalKm"',
    'id="kpiMobileKm"',
    "/api/v1/survey-catalog/coverage/summary",
    "/api/v1/survey-catalog/coverage",
    "Auswahl einpassen",
    "Gesamter Bestand",
    "Mausrad = Zoom",
    "geometryQuality(data)",
    "coverage/overview-lines?include_overview=true&",
):
    assert token in overview, token

for token in (
    'id="surveyStockKpis"',
    "loadSurveySummary()",
    "/api/v1/survey-catalog/coverage/summary",
    "manuell vom Handy",
):
    assert token in dashboard, token

print("OK: PostGIS survey summary + lazy coverage overview source")

