from __future__ import annotations

from typing import Any

PLATFORMS = {"ANDROID", "IOS", "WINDOWS"}


def normalize_platform(value: Any) -> str:
    platform = str(value or "").strip().upper()
    if platform not in PLATFORMS:
        raise ValueError("INVALID_PLATFORM")
    return platform


def compatibility_status(
    *,
    current_build: int | None,
    latest_build: int | None,
    minimum_supported_build: int | None,
    latest_mandatory: bool,
) -> str:
    """Return the stable client compatibility state used by API and tests."""
    if current_build is None:
        return "UNKNOWN_BUILD"
    if minimum_supported_build is not None and current_build < minimum_supported_build:
        return "UNSUPPORTED_BUILD"
    if latest_build is None or current_build >= latest_build:
        return "SUPPORTED_CURRENT"
    if latest_mandatory:
        return "UPDATE_REQUIRED"
    return "SUPPORTED_UPDATE_AVAILABLE"
