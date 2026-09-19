from __future__ import annotations

import base64
import io
from urllib.parse import quote

import qrcode
from qrcode.image.svg import SvgPathImage


ACTIVATION_SCHEME = "tbhelfer"
ACTIVATION_HOST = "activate"


def device_activation_app_uri(key: str) -> str:
    encoded = quote(key, safe="")
    return f"{ACTIVATION_SCHEME}://{ACTIVATION_HOST}?key={encoded}"


def device_activation_qr_svg_data_url(key: str) -> str:
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=7,
        border=4,
    )
    qr.add_data(device_activation_app_uri(key))
    qr.make(fit=True)
    image = qr.make_image(image_factory=SvgPathImage)
    buffer = io.BytesIO()
    image.save(buffer)
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    return f"data:image/svg+xml;base64,{encoded}"
