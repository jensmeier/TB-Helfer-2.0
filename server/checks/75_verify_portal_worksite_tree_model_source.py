from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_worksite.html").read_text(encoding="utf-8")
android = (ROOT / "app/src/main/java/de/tbhelfer/datenbank/BaustellenKnotenTyp.kt").read_text(encoding="utf-8")
api = (ROOT / "app/src/main/java/de/tbhelfer/server/WorksiteNodeApi.kt").read_text(encoding="utf-8")
server = (ROOT / "server/tbserver/worksites.py").read_text(encoding="utf-8")

for kind in ("FREIE_STRECKE", "BAHNHOF", "ALLGEMEIN_OBJEKT"):
    assert kind in android, kind
    assert f'value="{kind}"' in portal, kind

for old in ('value="SECTION"', 'value="TRACK"', 'value="SWITCH"', 'value="STATION"', 'value="OTHER"'):
    assert old not in portal, old

for token in (
    "nodeParent",
    "nodeKmFrom",
    "nodeKmTo",
    "nodeStation",
    "canonicalNodePayload",
    "parent_id:",
    "base_version:Number(n.current_version||n.version||1)",
    "?base_version=",
    "track_or_switch",
    "station_name",
    "km_from",
    "km_to",
):
    assert token in portal, token

assert 'payload.put("node_type", item.optString("node_kind"))' in api
assert 'include_deleted=true' in api
assert "WITH RECURSIVE subtree" in server

print("OK: Portal and Android use the same canonical Server-1.0 worksite-tree model")
