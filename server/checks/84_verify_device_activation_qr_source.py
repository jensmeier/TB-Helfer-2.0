from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    links = read("server/tbserver/device_activation_links.py")
    devices = read("server/tbserver/devices.py")
    pdf = read("server/tbserver/device_activation_pdf.py")
    users = read("server/templates/v1_admin_users.html")
    activation_card = read("server/templates/v1_device_activation_card.html")
    activation = read("app/src/main/java/de/tbhelfer/ui/activation/AktivierungActivity.kt")
    server_config = read("app/src/main/java/de/tbhelfer/server/ServerConfig.kt")
    requirements = read("server/requirements.txt")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ('ACTIVATION_SCHEME = "tbhelfer"', links),
        ('ACTIVATION_HOST = "activate"', links),
        ('device_activation_app_uri', links),
        ('?server=', links),
        ('PUBLIC_BASE_URL_MUST_USE_HTTPS', links),
        ('device_activation_qr_svg_data_url', links),
        ('SvgPathImage', links),
        ('qrcode==8.2', requirements),
        ('"server_base_url": public_base_url', devices),
        ('device_activation_qr_svg_data_url(raw_key, public_base_url)', devices),
        ('"activation_pdf_data_url": device_activation_pdf_data_url(', devices),
        ('response.headers["Cache-Control"] = "no-store"', devices),
        ('FOR UPDATE OF u', devices),
        ("device_activation_pdf_bytes", pdf),
        ("QrCodeWidget", pdf),
        ("data:application/pdf;base64", pdf),
        ('/activation-card', users),
        ('id="activationQr"', activation_card),
        ('id="downloadPdf"', activation_card),
        ('id="sharePdf"', activation_card),
        ("navigator.share", activation_card),
        ("Drucken / als PDF speichern", activation_card),
        ("Der Key wird nur für die erste Gerätebindung benötigt.", activation_card),
        ('android:host="activate"', read("app/src/main/AndroidManifest.xml")),
        ('scanQrCode()', activation),
        ('extractActivationCredential(raw)', activation),
        ('ServerConfig.rememberActivatedServer(', activation),
        ('ServerConfig.activationServer(rawServer)', activation),
        ('activated_server_base_url', server_config),
        ('BuildConfig.SERVER_BASE_URL', server_config),
        ('84_verify_device_activation_qr_source.py', workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if 'teamApi.' in activation:
        raise SystemExit("Aktivierungs-Activity enthält veraltete teamApi-Referenz")
    if missing:
        raise SystemExit(
            "Fehlende Device-Aktivierungs-QR-Marker: " + ", ".join(missing)
        )

    print("OK: device activation QR/PDF flow is wired server -> portal -> Android")


if __name__ == "__main__":
    main()
