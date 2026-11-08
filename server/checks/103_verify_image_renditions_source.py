from pathlib import Path
from tempfile import TemporaryDirectory

from PIL import Image as PILImage

ROOT = Path(__file__).resolve().parents[2]
files = (ROOT / "server/tbserver/files.py").read_text(encoding="utf-8")
btb = (ROOT / "server/tbserver/btb.py").read_text(encoding="utf-8")
capabilities = (ROOT / "server/tbserver/capabilities.py").read_text(encoding="utf-8")
btb_template = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")
worksite_template = (ROOT / "server/templates/v1_worksite.html").read_text(encoding="utf-8")

for token in (
    '@router.get("/files/{file_id}/rendition/{kind}")',
    '"Cache-Control": "private, max-age=31536000, immutable"',
    '"renditions": image_rendition_urls(',
):
    assert token in files or token in btb, token

assert '"file_image_renditions_v1": True' in capabilities
assert '"renditions": image_rendition_urls(' in btb
assert "p.renditions&&p.renditions.preview" in btb_template
assert "tbScopedUrl(file.renditions.thumb)" in worksite_template

from tbserver.image_renditions import (
    RENDITION_SPECS,
    image_rendition_urls,
    render_image_rendition,
)

assert set(RENDITION_SPECS) == {"thumb", "preview"}
source_text = (ROOT / "server/tbserver/image_renditions.py").read_text(encoding="utf-8")
assert "_MAX_CONCURRENT_RENDITION_RENDERS = 2" in source_text
assert "threading.BoundedSemaphore" in source_text
assert "with _RENDITION_RENDER_SEMAPHORE:" in source_text
urls = image_rendition_urls("00000000-0000-0000-0000-000000000001", "image/jpeg")
assert urls["thumb"].endswith("/rendition/thumb")
assert urls["preview"].endswith("/rendition/preview")
assert image_rendition_urls("00000000-0000-0000-0000-000000000001", "application/pdf") == {}

with TemporaryDirectory() as temp_dir:
    root = Path(temp_dir)
    source = root / "phone.jpg"
    thumb = root / "thumb.jpg"
    preview = root / "preview.jpg"

    with PILImage.new("RGB", (3072, 4080), "white") as image:
        image.save(source, "JPEG", quality=90)

    render_image_rendition(source, thumb, "thumb")
    render_image_rendition(source, preview, "preview")

    with PILImage.open(thumb) as image:
        assert image.width <= 320
        assert image.height <= 320
    with PILImage.open(preview) as image:
        assert image.width <= 1280
        assert image.height <= 1280

print("OK: bounded cached image renditions are exposed for Android/portal sync")
