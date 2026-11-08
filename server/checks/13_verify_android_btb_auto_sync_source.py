#!/usr/bin/env python3
"""Static contract check for automatic BTB sync after successful local saves."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BTB_STORE = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt"
SOFORT_SYNC = ROOT / "app/src/main/java/de/tbhelfer/server/SofortSync.kt"
LOCAL_BRIDGE = ROOT / "app/src/main/java/de/tbhelfer/datenbank/CollabLocalBridge.kt"

btb = BTB_STORE.read_text(encoding="utf-8")
sofort = SOFORT_SYNC.read_text(encoding="utf-8")
bridge = LOCAL_BRIDGE.read_text(encoding="utf-8")

errors: list[str] = []

if "import de.tbhelfer.server.SofortSync" not in btb:
    errors.append("BautagebuchSpeicher imports SofortSync nicht.")

helper = "private fun syncNachLokalemSpeichernAnstossen("
if helper not in btb:
    errors.append("Zentraler BTB-Sync-Hook fehlt.")

if "runCatching {" not in btb or "SofortSync.btbAnstossen(" not in btb or "SofortSync.anstossen(context.applicationContext)" not in btb:
    errors.append("SofortSync ist nicht fehlertolerant vom lokalen Speichern entkoppelt.")

modern_write = "f.writeText(json.toString(2), Charsets.UTF_8)"
legacy_write = "legacy.writeText(json.toString(2), Charsets.UTF_8)"
sync_call = "syncNachLokalemSpeichernAnstossen(context,"

modern_pos = btb.find(modern_write)
legacy_pos = btb.find(legacy_write)
if modern_pos < 0:
    errors.append("Moderner BTB-Schreibpfad wurde nicht gefunden.")
if legacy_pos < 0:
    errors.append("Legacy-BTB-Schreibpfad wurde nicht gefunden.")

if modern_pos >= 0:
    modern_sync = btb.find(sync_call, modern_pos)
    if modern_sync < 0 or (legacy_pos >= 0 and modern_sync > legacy_pos):
        errors.append("Moderner BTB-Pfad stößt nach lokalem Schreiben keinen SofortSync an.")

if legacy_pos >= 0:
    legacy_sync = btb.find(sync_call, legacy_pos)
    if legacy_sync < 0:
        errors.append("Legacy-BTB-Pfad stößt nach lokalem Schreiben keinen SofortSync an.")

if btb.count(sync_call) < 2:
    errors.append("BTB-Sync-Hook muss modernen und Legacy-Speicherpfad abdecken.")

if "ExistingWorkPolicy.KEEP" not in sofort:
    errors.append("SofortSync entprellt parallele Speichertrigger nicht mehr per unique KEEP.")
if ".setRequiredNetworkType(NetworkType.CONNECTED)" not in sofort:
    errors.append("SofortSync hat keinen Netzwerk-Constraint mehr.")

# Eingehende Server-Snapshots werden absichtlich direkt in die lokalen Zieldateien gespiegelt.
# Würden sie BautagebuchSpeicher.speichern() aufrufen, könnte Serverimport -> SofortSync -> Upload
# einen unnötigen Rückkopplungskreis auslösen.
if "BautagebuchSpeicher.speichern(" in bridge:
    errors.append("CollabLocalBridge darf Serverimporte nicht über BautagebuchSpeicher.speichern() zurückschreiben.")

if errors:
    for error in errors:
        print(f"ERROR: {error}")
    raise SystemExit(1)

print("OK: automatischer BTB-Sync nach lokalem Speichern ist statisch abgesichert.")
