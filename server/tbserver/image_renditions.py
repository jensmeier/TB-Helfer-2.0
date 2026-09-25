from __future__ import annotations

import os
from pathlib import Path
from uuid import UUID, uuid4

from PIL import Image as PILImage, ImageOps


RENDITION_VERSION = "v1"
RENDITION_SPECS: dict[str, tuple[int, int, int]] = {
    "thumb": (320, 320, 72),
    "preview": (1280, 1280, 82),
}


def image_rendition_urls(file_id: UUID | str, mime_type: str | None) -> dict[str, str]:
    if not str(mime_type or "").lower().startswith("image/"):
        return {}
    base = f"/api/v1/files/{file_id}/rendition"
    return {kind: f"{base}/{kind}" for kind in RENDITION_SPECS}


def _flatten_to_rgb(image: PILImage.Image) -> PILImage.Image:
    if image.mode == "RGB":
        return image
    if image.mode in {"RGBA", "LA"} or "transparency" in image.info:
        rgba = image.convert("RGBA")
        try:
            flattened = PILImage.new("RGB", rgba.size, "white")
            flattened.paste(rgba, mask=rgba.getchannel("A"))
            return flattened
        finally:
            rgba.close()
    return image.convert("RGB")


def render_image_rendition(source_path: Path, target_path: Path, kind: str) -> Path:
    spec = RENDITION_SPECS.get(kind)
    if spec is None:
        raise ValueError("IMAGE_RENDITION_KIND_NOT_SUPPORTED")

    max_width, max_height, quality = spec
    target_path.parent.mkdir(parents=True, exist_ok=True)

    with PILImage.open(source_path) as source:
        try:
            source.draft("RGB", (max_width, max_height))
        except Exception:
            pass

        normalized = ImageOps.exif_transpose(source)
        prepared: PILImage.Image | None = None
        try:
            normalized.thumbnail(
                (max_width, max_height),
                resample=PILImage.Resampling.LANCZOS,
            )
            prepared = _flatten_to_rgb(normalized)

            temp_path = target_path.with_name(
                f".{target_path.name}.{uuid4().hex}.tmp"
            )
            try:
                prepared.save(
                    temp_path,
                    format="JPEG",
                    quality=quality,
                    optimize=True,
                    progressive=True,
                )
                os.replace(temp_path, target_path)
            finally:
                if temp_path.exists():
                    temp_path.unlink()
        finally:
            if prepared is not None and prepared is not normalized:
                prepared.close()
            if normalized is not source:
                normalized.close()

    return target_path


def rendition_cache_path(source_path: Path, kind: str) -> Path:
    if kind not in RENDITION_SPECS:
        raise ValueError("IMAGE_RENDITION_KIND_NOT_SUPPORTED")
    return source_path.parent / ".renditions" / f"{kind}-{RENDITION_VERSION}.jpg"
