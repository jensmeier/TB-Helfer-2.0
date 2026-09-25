from __future__ import annotations

import base64
import json
import math
import struct
from datetime import datetime, timezone
from typing import Any

PHOTO_TYPES = (
    "ABNAHMEN",
    "EINWEISUNG_UNTERWEISUNG",
    "BESONDERHEITEN",
    "MAENGEL_SCHAEDEN",
    "FOTODOKUMENTATION",
)
PHOTO_INDICATORS = ("GRUEN", "GELB", "ROT")
_PREFIX_V1 = "BTBP1__"
_PREFIX_LEGACY = "BTBPHOTO__"


def _b64url_decode(raw: str) -> bytes:
    padding = "=" * ((4 - len(raw) % 4) % 4)
    return base64.urlsafe_b64decode((raw + padding).encode("ascii"))


def _tiny_utf(data: bytes, offset: int) -> tuple[str, int]:
    if offset >= len(data):
        raise ValueError("BTB photo metadata is truncated")
    size = data[offset]
    offset += 1
    end = offset + size
    if end > len(data):
        raise ValueError("BTB photo metadata text is truncated")
    return data[offset:end].decode("utf-8"), end


def decode_btb_photo_metadata(filename: str | None) -> dict[str, Any] | None:
    raw = str(filename or "")
    if raw.startswith(_PREFIX_V1):
        encoded = raw[len(_PREFIX_V1):].split("__", 1)[0]
        if not encoded:
            return None
        try:
            data = _b64url_decode(encoded)
            fixed_size = struct.calcsize(">qfffffhBBB")
            if len(data) < fixed_size:
                return None
            (
                epoch_ms,
                lat,
                lon,
                alt,
                km,
                track_distance,
                km_suffix,
                flags,
                type_index,
                indicator_index,
            ) = struct.unpack_from(">qfffffhBBB", data, 0)
            offset = fixed_size
            company, offset = _tiny_utf(data, offset)
            short_text, offset = _tiny_utf(data, offset)
            object_name, offset = _tiny_utf(data, offset)
            object_area, offset = _tiny_utf(data, offset)
            return {
                "epoch": int(epoch_ms),
                "lat": float(lat),
                "lon": float(lon),
                "alt": None if math.isnan(alt) else float(alt),
                "km": None if math.isnan(km) else float(km),
                "achsabstand": None if math.isnan(track_distance) else float(track_distance),
                "kmSuffix": int(km_suffix),
                "kmManuell": bool(flags & 1),
                "typ": PHOTO_TYPES[type_index] if type_index < len(PHOTO_TYPES) else "FOTODOKUMENTATION",
                "indikator": PHOTO_INDICATORS[indicator_index] if indicator_index < len(PHOTO_INDICATORS) else "",
                "firma": company,
                "kurztext": short_text or "Foto",
                "objektKennung": object_name,
                "objektBereich": object_area,
            }
        except (ValueError, UnicodeDecodeError, struct.error):
            return None

    if raw.startswith(_PREFIX_LEGACY):
        encoded = raw[len(_PREFIX_LEGACY):].split("__", 1)[0]
        if not encoded:
            return None
        try:
            value = json.loads(_b64url_decode(encoded).decode("utf-8"))
            return value if isinstance(value, dict) else None
        except (ValueError, UnicodeDecodeError, json.JSONDecodeError):
            return None
    return None


def canonical_photo_metadata(
    *,
    filename: str | None,
    captured_at: datetime | None = None,
    gps_lat: float | None = None,
    gps_lon: float | None = None,
    gps_accuracy_m: float | None = None,
) -> dict[str, Any]:
    decoded = decode_btb_photo_metadata(filename) or {}
    resolved_captured_at = captured_at
    if resolved_captured_at is None and decoded.get("epoch") is not None:
        try:
            resolved_captured_at = datetime.fromtimestamp(
                float(decoded["epoch"]) / 1000.0,
                tz=timezone.utc,
            )
        except (OverflowError, OSError, TypeError, ValueError):
            resolved_captured_at = None

    def valid_coordinate(value: Any, minimum: float, maximum: float) -> float | None:
        try:
            number = float(value)
        except (TypeError, ValueError):
            return None
        return number if minimum <= number <= maximum else None

    resolved_lat = gps_lat if gps_lat is not None else valid_coordinate(decoded.get("lat"), -90.0, 90.0)
    resolved_lon = gps_lon if gps_lon is not None else valid_coordinate(decoded.get("lon"), -180.0, 180.0)
    return {
        "captured_at": resolved_captured_at,
        "gps_lat": resolved_lat,
        "gps_lon": resolved_lon,
        "gps_accuracy_m": gps_accuracy_m,
        "photo_metadata": decoded or None,
    }
