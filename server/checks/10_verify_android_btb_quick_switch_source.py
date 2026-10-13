#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
QUICK = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/BtbSchnellwechselInstaller.kt"
ACTIVITY = ROOT / "app/src/main/java/de/tbhelfer/ui/HauptActivity.kt"
SHIFT_BUTTON = ROOT / "app/src/main/java/de/tbhelfer/ui/bautagebuch/ExistingBtbShiftButton.kt"
SHIFT_LAYOUT = ROOT / "app/src/main/res/layout/dialog_btb_open_shift.xml"


def require(path: Path, *needles: str) -> None:
    text = path.read_text(encoding="utf-8")
    missing = [needle for needle in needles if needle not in text]
    if missing:
        print(f"FEHLER {path}: fehlt {missing}", file=sys.stderr)
        raise SystemExit(1)


require(
    QUICK,
    "cardCurrentReport",
    "verlaufFuerBaustelle",
    "aktiveBtbId",
    "aktivierenBtb",
    "putString(\"btbId\"",
    "Neuen BTB über + starten",
    "setPopUpTo(R.id.bautagebuchFragment, true)",
)
require(
    ACTIVITY,
    "BtbSchnellwechselInstaller.install(this)",
)
require(
    SHIFT_BUTTON,
    "R.string.btb_status_new_empty",
    "visibility = if (missing) View.GONE else View.VISIBLE",
)
require(
    SHIFT_LAYOUT,
    "de.tbhelfer.ui.bautagebuch.ExistingBtbShiftButton",
)

quick_text = QUICK.read_text(encoding="utf-8")
if "ReportState.NEW_EMPTY" in quick_text or "days.map" in quick_text:
    print("FEHLER: neuer Schnellwechsel darf keine künstliche Tag/Nacht-Leerliste erzeugen", file=sys.stderr)
    raise SystemExit(1)

print("ANDROID_BTB_QUICK_SWITCH_OK")
