from __future__ import annotations

import base64
import io
from urllib.parse import quote

import qrcode
from qrcode.image.svg import SvgPathImage


INVITE_SCHEME = "tbhelfer"
INVITE_HOST = "worksite-invite"


def invite_app_uri(token: str) -> str:
    encoded = quote(token, safe="")
    return f"{INVITE_SCHEME}://{INVITE_HOST}?token={encoded}"


def invite_browser_link(redeem_url: str, token: str) -> str:
    base = redeem_url.split("#", 1)[0]
    encoded = quote(token, safe="")
    return f"{base}#token={encoded}"


def invite_qr_svg_data_url(token: str) -> str:
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=6,
        border=4,
    )
    qr.add_data(invite_app_uri(token))
    qr.make(fit=True)
    image = qr.make_image(image_factory=SvgPathImage)
    buffer = io.BytesIO()
    image.save(buffer)
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    return f"data:image/svg+xml;base64,{encoded}"
