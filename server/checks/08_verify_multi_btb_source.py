#!/usr/bin/env python3
"""Static source preflight for stable multi-BTB identity across server + Android."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

MIGRATION = ROOT / "server" / "migrations" / "009_worksite_collaboration.sql"
SERVER = ROOT / "server" / "app_parts" / "15_part.py"
BRIDGE = ROOT / "app" / "src" / "main" / "java" / "de" / "tbhelfer" / "datenbank" / "CollabLocalBridge.kt"
MEDIA = ROOT / "app" / "src" / "main" / "java" / "de" / "tbhelfer" / "datenbank" / "BtbTeamMediaSync.kt"
MODEL = ROOT / "app" / "src" / "main" / "java" / "de" / "tbhelfer" / "ui" / "bautagebuch" / "BautagebuchModels.kt"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"


def require(text: str, needle: str, code: str) -> None:
    if needle not in text:
        raise SystemExit(code)


def forbid(text: str, needle: str, code: str) -> None:
    if needle in text:
        raise SystemExit(code)


def main() -> None:
    migration = MIGRATION.read_text(encoding="utf-8")
    server = SERVER.read_text(encoding="utf-8")
    bridge = BRIDGE.read_text(encoding="utf-8")
    media = MEDIA.read_text(encoding="utf-8")
    model = MODEL.read_text(encoding="utf-8")
    manifest = MANIFEST.read_text(encoding="utf-8")

    # Syntax of the Python wrapper must be valid before rollout.
    compile(server, str(SERVER), "exec")

    # Existing DB schema already identifies a BTB by group + arbitrary client key.
    require(migration, "UNIQUE (group_id, client_entry_id)", "BTB_DB_UNIQUE_CLIENT_KEY_MISSING")
    forbid(migration, "UNIQUE (group_id, entry_date, shift)", "BTB_DB_DATE_SHIFT_UNIQUE_FORBIDDEN")

    # Branch server must enforce one stable technical BTB ID and keep it in the payload.
    require(server, "BTB_ID_REQUIRED", "BTB_SERVER_ID_REQUIRED_MISSING")
    require(server, "BTB_ID_MISMATCH", "BTB_SERVER_ID_MISMATCH_GUARD_MISSING")
    require(server, 'req.payload["btbId"]', "BTB_SERVER_PAYLOAD_ID_MIRROR_MISSING")
    require(server, "req.client_entity_key", "BTB_SERVER_CLIENT_KEY_MISSING")

    # Android text sync and media sync must use the same stable key.
    require(bridge, "verlauf.btbId", "BTB_ANDROID_TEXT_ID_MISSING")
    require(bridge, '"legacy:${verlauf.datum}:${verlauf.schicht.name}"', "BTB_ANDROID_TEXT_LEGACY_KEY_MISSING")
    require(media, "v.btbId", "BTB_ANDROID_MEDIA_ID_MISSING")
    require(media, '"legacy:${v.datum}:${v.schicht.name}"', "BTB_ANDROID_MEDIA_LEGACY_KEY_MISSING")
    forbid(media, 'val key = "${v.datum}:${v.schicht.name}"', "BTB_ANDROID_MEDIA_DATE_SHIFT_KEY_FORBIDDEN")

    # New local reports have an explicit stable ID field; TAG/NACHT remains display/legacy only.
    require(model, "val btbId: String? = null", "BTB_ANDROID_MODEL_ID_MISSING")
    require(model, "Nur Anzeige-/Legacy-Klassifikation, nie technische Identität.", "BTB_ANDROID_MODEL_ID_RULE_MISSING")

    # Network transport inspection in SyncWorker requires this normal manifest permission.
    require(manifest, "android.permission.ACCESS_NETWORK_STATE", "SYNC_NETWORK_STATE_PERMISSION_MISSING")

    print("MULTI_BTB_SOURCE_OK")


if __name__ == "__main__":
    main()
