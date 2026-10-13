#!/usr/bin/env python3
"""Static contract checks for Android 200050 BTB section images, media sync and PDF logo rendering."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GRADLE = ROOT / "app/build.gradle.kts"
BINDER = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEntryPhotoActionBinder.kt"
PDF_HEADER = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfHeaderRenderer.kt"
BTB_STORE = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt"
MEDIA_SYNC = ROOT / "app/src/main/java/de/tbhelfer/datenbank/BtbTeamMediaSync.kt"
FOTO_REPO = ROOT / "app/src/main/java/de/tbhelfer/datenbank/FotoRepository.kt"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


gradle = read(GRADLE)
binder = read(BINDER)
header = read(PDF_HEADER)
store = read(BTB_STORE)
media = read(MEDIA_SYNC)
foto_repo = read(FOTO_REPO)
errors: list[str] = []

# Punkte 4-8 laufen alle ueber denselben Section-Editor mit einem nicht-null Bildtyp.
if "if (fotoType != null)" not in gradle:
    errors.append("BTB-Bilderaktion ist nicht fuer alle Section-Bildtypen 4-8 aktiviert.")
if "bildtyp = fotoType" not in gradle:
    errors.append("Der konkrete Bildtyp wird nicht an den gemeinsamen Binder weitergereicht.")
import re
version_match = re.search(r"versionCode\s*=\s*(\d+)", gradle)
if not version_match or int(version_match.group(1)) < 200050:
    errors.append("Android-Version darf nicht unter dem ursprünglichen BTB-Fix-Build 200050 liegen.")

if "bildtyp: Bildtyp" not in binder:
    errors.append("BtbEntryPhotoActionBinder akzeptiert keinen konkreten Bildtyp.")
if 'putString("filterBildtyp", bildtyp.name)' not in binder:
    errors.append("Bildauswahl filtert nicht nach dem Bildtyp des BTB-Abschnitts.")

# Die AlertDialog-Systemleiste war auf dem Poco trotz gleicher Gewichte abgeschnitten. Die
# sichtbaren Aktionen muessen deshalb als eigene feste Dreierzeile im Dialoginhalt sitzen.
if "fun actionButton(text: String): MaterialButton" not in binder:
    errors.append("Eigene stabile MaterialButton-Aktionszeile fehlt.")
if "LinearLayout.LayoutParams(0, buttonHeight, 1f)" not in binder:
    errors.append("Bilder/Abbrechen/Hinzufuegen haben keine gleich breite 1/3-Aufteilung.")
for label in ('actionButton("Bilder")', 'actionButton("Abbrechen")', 'actionButton("Hinzufügen")'):
    if label not in binder:
        errors.append(f"BTB-Aktionsbutton fehlt: {label}")
if "alert.getButton(DialogInterface.BUTTON_POSITIVE)?.performClick()" not in binder:
    errors.append("Eigener Hinzufuegen-Button delegiert nicht an die bestehende Validierungslogik.")
if "systemRow?.visibility = View.GONE" not in binder:
    errors.append("Alte AlertDialog-Systembuttonzeile wird nicht optisch ausgeblendet.")

# Die vier weiteren BTB-Bloecke muessen weiterhin Bestandteil desselben BTB-JSON sein.
for field in ("abnahmen", "einweisungUnterweisung", "besonderheiten", "maengelSchaeden"):
    if f'put("{field}"' not in store:
        errors.append(f"BTB-Speicher serialisiert Punkt 5-8 Feld {field!r} nicht.")

# Moderne BTB-Fotos duerfen nie wieder ueber ein blosses Zeitfenster zwischen BTBs verrutschen.
if "BtbFotoZuordnungSpeicher.fotoIdsFuerBtb(appContext, stableBtbId)" not in media:
    errors.append("Moderner BTB-Medienupload ist nicht strikt nach btbId gefiltert.")
if "BtbFotoZuordnungSpeicher.btbId(appContext, foto.id) == null" not in media:
    errors.append("Legacy-Zeitfenster schliesst modern zugeordnete Fotos nicht aus.")
if "fotos.einfuegenFuerBtb(entity, parentKey, triggerSync = false)" not in media:
    errors.append("Importierte moderne BTB-Fotos erhalten ihre Server-btbId nicht wieder lokal.")
if "out.writeByte(foto.bildtyp.ordinal" not in media:
    errors.append("BTB-Fotometadaten transportieren den Bildtyp 4-8 nicht.")
if '.put("typ", Bildtyp.values().getOrNull(bildtypIndex)?.name' not in media:
    errors.append("Import stellt den uebertragenen BTB-Bildtyp nicht wieder her.")

# Hintergrund-Sync darf nicht vom aktuell im Fragment gesetzten Foto-Query-Scope abhaengen.
if "suspend fun fotosFuerBaustelleSyncOnce" not in foto_repo:
    errors.append("FotoRepository besitzt keinen ungescopten Hintergrund-Sync-Leseweg.")
if "val localFotos = fotos.fotosFuerBaustelleSyncOnce(local.altBaustelleId)" not in media:
    errors.append("BtbTeamMediaSync verwendet weiterhin den UI-gescopten Foto-Leseweg.")

# Transparente Logos duerfen im PDF nicht von Viewer-/Seitenhintergruenden abhaengen.
if "import android.graphics.Color" not in header:
    errors.append("PDF-Header importiert Color fuer den weissen Logohintergrund nicht.")
rect_pos = header.find("canvas.drawRect(logoRect")
bitmap_pos = header.find("canvas.drawBitmap(")
if rect_pos < 0 or bitmap_pos < 0 or rect_pos > bitmap_pos:
    errors.append("Weisser Logo-Untergrund wird nicht vor dem Bitmap gezeichnet.")
if "color = Color.WHITE" not in header:
    errors.append("Logo-Untergrund ist nicht explizit weiss.")

if errors:
    for error in errors:
        print(f"ERROR: {error}")
    raise SystemExit(1)

print("OK: Android 200050 BTB-Punkte 4-8, stabile Dreierzeile, btbId-Medien-Sync und PDF-Logo sind statisch abgesichert.")
