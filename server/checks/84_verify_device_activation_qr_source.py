from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    links = read("server/tbserver/device_activation_links.py")
    devices = read("server/tbserver/devices.py")
    users = read("server/templates/v1_admin_users.html")
    activation = read("app/src/main/java/de/tbhelfer/ui/activation/AktivierungActivity.kt")
    requirements = read("server/requirements.txt")
    workflow = read(".github/workflows/tb-server-v1.yml")

    assertions = [
        ('ACTIVATION_SCHEME = "tbhelfer"', links),
        ('ACTIVATION_HOST = "activate"', links),
        ('device_activation_app_uri', links),
        ('device_activation_qr_svg_data_url', links),
        ('SvgPathImage', links),
        ('qrcode==8.2', requirements),
        ('"activation_qr_svg_data_url": device_activation_qr_svg_data_url(raw_key)', devices),
        ('id="deviceActivationQr"', users),
        ('showDeviceActivation(data)', users),
        ('QR-Code jetzt mit dem vorgesehenen Handy scannen', users),
        ('android:host="activate"', read("app/src/main/AndroidManifest.xml")),
        ('scanQrCode()', activation),
        ('extractActivationKey(raw)', activation),
        ('84_verify_device_activation_qr_source.py', workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Device-Aktivierungs-QR-Marker: " + ", ".join(missing)
        )

    print("OK: device activation QR flow is wired server -> portal -> Android")


if __name__ == "__main__":
    main()
