from __future__ import annotations

import base64
import io
from urllib.parse import quote

import qrcode
from qrcode.image.svg import SvgPathImage


ACTIVATION_SCHEME = "tbhelfer"
ACTIVATION_HOST = "activate"


def _normalize_server_base_url(server_base_url: str) -> str:
    value = server_base_url.strip().rstrip("/")
    if not value.startswith("https://"):
        raise ValueError("PUBLIC_BASE_URL_MUST_USE_HTTPS")
    return value


def device_activation_app_uri(key: str, server_base_url: str) -> str:
    encoded_key = quote(key, safe="")
    encoded_server = quote(_normalize_server_base_url(server_base_url), safe="")
    return (
        f"{ACTIVATION_SCHEME}://{ACTIVATION_HOST}"
        f"?server={encoded_server}&key={encoded_key}"
    )


def device_activation_qr_svg_data_url(key: str, server_base_url: str) -> str:
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=7,
        border=4,
    )
    qr.add_data(device_activation_app_uri(key, server_base_url))
    qr.make(fit=True)
    image = qr.make_image(image_factory=SvgPathImage)
    buffer = io.BytesIO()
    image.save(buffer)
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    return f"data:image/svg+xml;base64,{encoded}"
