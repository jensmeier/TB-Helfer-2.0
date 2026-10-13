#!/usr/bin/env python3
"""Static contract checks for group-wide BTB completion/delete lifecycle on Android."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DAO = ROOT / "app/src/main/java/de/tbhelfer/datenbank/SyncDao.kt"
COORD = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDeleteSyncCoordinator.kt"
LOCAL_DELETE = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbDeleteService.kt"
ACTIVE_CONTEXT = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbActiveContext.kt"
POINTER = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbActivePointerControl.kt"
WORKER = ROOT / "app/src/main/java/de/tbhelfer/server/SyncWorker.kt"
VERLAUF = ROOT / "app/src/main/java/de/tbhelfer/ui/baustelle/BtbVerlaufFragment.kt"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


dao = read(DAO)
coord = read(COORD)
local_delete = read(LOCAL_DELETE)
active = read(ACTIVE_CONTEXT)
pointer = read(POINTER)
worker = read(WORKER)
verlauf = read(VERLAUF)
errors: list[str] = []

# Der Servercache muss Tombstones und auch einen gerade UPLOADING befindlichen Auftrag sichtbar machen.
if "suspend fun deletedCollabEntities" not in dao or "isDeleted = 1" not in dao:
    errors.append("SyncDao exposeiert keine geloeschten Collaboration-Entities.")
if "suspend fun collabPendingAnyForClientKey" not in dao or "'UPLOADING'" not in dao:
    errors.append("SyncDao kann laufende BTB-Mutationen beim Loeschen nicht erkennen.")

# Benutzerdelete: persistentes Intent + bestehende OP_DELETE Queue + SofortSync.
for token in (
    "btb_delete_sync_intents_v1",
    "SyncCollabPendingChangeEntity.OP_DELETE",
    "baseVersion = serverEntity.entityVersion",
    "SofortSync.anstossen(appContext)",
    "BtbDeleteService.loeschen(appContext, entry)",
):
    if token not in coord:
        errors.append(f"BTB-Delete-Coordinator-Vertrag fehlt: {token}")

# Konflikt nach paralleler Team-Aenderung muss mit aktueller Serverversion neu eingereiht werden.
if "STATE_CONFLICT" not in coord or "pending?.baseVersion != serverEntity.entityVersion" not in coord:
    errors.append("Expliziter BTB-Delete wird nach Versionskonflikt nicht gegen den aktuellen Serverstand rebased.")

# Remote-Tombstone muss denselben lokalen BTB entfernen; echte Fotos bleiben ueber den Service erhalten.
if "deletedCollabEntities" not in coord or "BtbDeleteService.loeschen(appContext, row.entry)" not in coord:
    errors.append("Server-Tombstones werden nicht lokal auf dieselbe btbId angewendet.")
if "BtbFotoZuordnungSpeicher.btbLoesen" not in local_delete:
    errors.append("BTB-Loeschen loest die Fotozuordnung nicht, ohne echte Fotos zu vernichten.")
if "return Ergebnis(deleted, detached, null)" not in local_delete:
    errors.append("Nach BTB-Loeschen wird weiterhin ein anderer Verlauf-BTB automatisch aktiviert.")

# Abschluss: fertiger BTB wird aus dem aktiven Kontext geloest und sein Status sofort synchronisiert.
if "BtbDokumentVersionierung.status(appContext, entry) == BtbAbschlussStatus.ABGESCHLOSSEN" not in active:
    errors.append("BtbActiveContext erkennt einen lokal/remote abgeschlossenen BTB nicht.")
if "BtbActivePointerControl.nachAbschlussInaktivMerken" not in active:
    errors.append("Abgeschlossener BTB wird nicht aus dem aktiven Pointer geloest.")
if "Serververtrag 25.09.2026" not in active or "shift-finish/shift-resume/finalize" not in active:
    errors.append("Abschluss-Leseweg darf keinen Vollsync anstossen; Lifecycle muss explizit sein.")

# Nur die bewusste Verlauf-Aktion darf einen abgeschlossenen BTB mit derselben btbId reaktivieren.
if "CONSCIOUS_REOPEN_FILE" not in pointer or "fun bewusstReaktivieren" not in pointer:
    errors.append("Bewusstes Reaktivieren eines Abschluss-BTB ist nicht vom stale Remote-Pointer getrennt.")
if "fun istBewusstReaktiviert" not in pointer:
    errors.append("Aktiver Kontext kann bewusste Nachbearbeitung nicht erkennen.")
if "BtbActivePointerControl.bewusstReaktivieren" not in verlauf:
    errors.append("BTB-Verlauf markiert 'BTB bearbeiten' nicht als bewusste Reaktivierung derselben btbId.")

# Tombstones gewinnen im Worker VOR normalem Server-BTB-Import und werden noch im selben Lauf geflusht.
first_reconcile = worker.find("reconcileDeletesAfterPull()?.let { return it }")
first_import = worker.find("localBridge.applyServerStateToLocal()")
if first_reconcile < 0 or first_import < 0 or first_reconcile > first_import:
    errors.append("BTB-Delete-Reconcile laeuft nicht vor normalem Serverimport.")
if "repeat(3)" not in worker or "serverDeletesEingereiht" not in worker:
    errors.append("Neu eingereihte/konfligierende BTB-Deletes werden nicht im selben Worker-Lauf geflusht.")

# Sichtbare UI soll einen real angelegten BTB nicht als technischen 'Entwurf' bezeichnen.
if 'BtbAbschlussStatus.ENTWURF -> "BTB aktiv"' not in verlauf:
    errors.append("BTB-Verlauf zeigt weiterhin den technischen Begriff Entwurf.")
if "BtbDeleteSyncCoordinator.deleteUserRequested" not in verlauf:
    errors.append("Verlauf loescht BTB weiterhin nur lokal statt ueber den Team-Delete-Coordinator.")

if errors:
    for error in errors:
        print(f"ERROR: {error}")
    raise SystemExit(1)

print("OK: BTB aktiv/Abschluss/Reaktivieren/Loeschen und Team-Tombstones sind statisch abgesichert.")
