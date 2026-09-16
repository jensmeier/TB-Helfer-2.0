#!/usr/bin/env python3
"""Static contract checks for Android 200050 BTB section images and PDF logo rendering."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GRADLE = ROOT / "app/build.gradle.kts"
BINDER = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbEntryPhotoActionBinder.kt"
PDF_HEADER = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbPdfHeaderRenderer.kt"
BTB_STORE = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BautagebuchSpeicher.kt"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


gradle = read(GRADLE)
binder = read(BINDER)
header = read(PDF_HEADER)
store = read(BTB_STORE)
errors: list[str] = []

# Punkte 4-8 laufen alle ueber denselben Section-Editor mit einem nicht-null Bildtyp.
if "if (fotoType != null)" not in gradle:
    errors.append("BTB-Bilderaktion ist nicht fuer alle Section-Bildtypen 4-8 aktiviert.")
if "bildtyp = fotoType" not in gradle:
    errors.append("Der konkrete Bildtyp wird nicht an den gemeinsamen Binder weitergereicht.")
if "versionCode = 200050" not in gradle:
    errors.append("Android-Buildkandidat 200050 ist nicht gesetzt.")

if "bildtyp: Bildtyp" not in binder:
    errors.append("BtbEntryPhotoActionBinder akzeptiert keinen konkreten Bildtyp.")
if 'putString("filterBildtyp", bildtyp.name)' not in binder:
    errors.append("Bildauswahl filtert nicht nach dem Bildtyp des BTB-Abschnitts.")
if "LinearLayout.LayoutParams(\n                        0,\n                        buttonHeight,\n                        1f" not in binder:
    errors.append("Bilder/Abbrechen/Hinzufuegen haben keine gleich breite Dreieraufteilung.")
if "button.textSize = 10.5f" not in binder:
    errors.append("Kompakte lesbare Button-Schrift fuer die schmale Dreierzeile fehlt.")

# Die vier weiteren BTB-Bloecke muessen weiterhin Bestandteil desselben BTB-JSON sein.
for field in ("abnahmen", "einweisungUnterweisung", "besonderheiten", "maengelSchaeden"):
    if f'put("{field}"' not in store:
        errors.append(f"BTB-Speicher serialisiert Punkt 5-8 Feld {field!r} nicht.")

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

print("OK: Android 200050 BTB-Punkte 4-8 und weisser PDF-Logohintergrund sind statisch abgesichert.")
