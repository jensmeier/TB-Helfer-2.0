#!/usr/bin/env python3
"""Static preflight: local BTB photo helper state must be isolated by stable btbId."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "app" / "src" / "main" / "java" / "de" / "tbhelfer" / "ui" / "bautagebuch"

FILES = {
    "key": BASE / "BtbPhotoInstanzKey.kt",
    "selection": BASE / "BtbPhotoSelectionStore.kt",
    "text": BASE / "BtbPhotoTextOverrideStore.kt",
    "start": BASE / "BtbPhotoStartOverrideStore.kt",
    "km": BASE / "BtbPhotoKmOverrideStore.kt",
}


def require(text: str, needle: str, code: str) -> None:
    if needle not in text:
        raise SystemExit(code)


def main() -> None:
    src = {name: path.read_text(encoding="utf-8") for name, path in FILES.items()}

    require(src["key"], '".active_btb.json"', "PHOTO_KEY_ACTIVE_POINTER_MISSING")
    require(src["key"], 'optString("btbId")', "PHOTO_KEY_BTB_ID_MISSING")
    require(src["key"], '"baustelle_${baustelleId}/Bautagebuch"', "PHOTO_KEY_CANONICAL_WORKSITE_PATH_MISSING")

    for name in ("selection", "text", "start", "km"):
        text = src[name]
        require(text, "private const val VERSION = 2", f"PHOTO_{name.upper()}_VERSION2_MISSING")
        require(text, "BtbPhotoInstanzKey.activeToken", f"PHOTO_{name.upper()}_ACTIVE_TOKEN_MISSING")
        require(text, "_btb_${token}_", f"PHOTO_{name.upper()}_BTB_FILENAME_SCOPE_MISSING")
        require(text, "BtbPhotoInstanzKey.activeBtbId", f"PHOTO_{name.upper()}_BTB_METADATA_MISSING")

    # deleteAllFor must switch to the active btbId so starting BTB B cannot delete BTB A state.
    for name in ("selection", "text", "start", "km"):
        require(src[name], "fun deleteAllFor", f"PHOTO_{name.upper()}_DELETE_ALL_MISSING")
        require(src[name], "if (token != null)", f"PHOTO_{name.upper()}_DELETE_NOT_BTB_SCOPED")

    print("ANDROID_MULTI_BTB_PHOTO_STATE_OK")


if __name__ == "__main__":
    main()
