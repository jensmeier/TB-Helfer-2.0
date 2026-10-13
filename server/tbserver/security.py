from __future__ import annotations

import base64
import hashlib
import hmac
import secrets


PASSWORD_MIN_LENGTH = 12
ACTIVATION_KEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
SCRYPT_N = 2**14
SCRYPT_R = 8
SCRYPT_P = 1


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def _unb64(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def hash_token(raw: str) -> str:
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def random_token(nbytes: int = 32) -> str:
    return secrets.token_urlsafe(nbytes)


def random_activation_key() -> str:
    raw = "".join(secrets.choice(ACTIVATION_KEY_ALPHABET) for _ in range(32))
    groups = [raw[:8]] + [raw[i:i + 4] for i in range(8, 32, 4)]
    return "TBH-" + "-".join(groups)


def validate_password(password: str) -> None:
    if len(password) < PASSWORD_MIN_LENGTH:
        raise ValueError(f"Passwort muss mindestens {PASSWORD_MIN_LENGTH} Zeichen lang sein.")
    if password.lower() == password or password.upper() == password:
        raise ValueError("Passwort muss Groß- und Kleinbuchstaben enthalten.")
    if not any(ch.isdigit() for ch in password):
        raise ValueError("Passwort muss mindestens eine Ziffer enthalten.")


def hash_password(password: str) -> str:
    validate_password(password)
    salt = secrets.token_bytes(16)
    digest = hashlib.scrypt(
        password.encode("utf-8"),
        salt=salt,
        n=SCRYPT_N,
        r=SCRYPT_R,
        p=SCRYPT_P,
        dklen=32,
    )
    return f"scrypt${SCRYPT_N}${SCRYPT_R}${SCRYPT_P}${_b64(salt)}${_b64(digest)}"


def verify_password(password: str, encoded: str | None) -> bool:
    if not encoded:
        return False
    try:
        kind, n, r, p, salt_raw, digest_raw = encoded.split("$", 5)
        if kind != "scrypt":
            return False
        salt = _unb64(salt_raw)
        expected = _unb64(digest_raw)
        actual = hashlib.scrypt(
            password.encode("utf-8"),
            salt=salt,
            n=int(n),
            r=int(r),
            p=int(p),
            dklen=len(expected),
        )
        return hmac.compare_digest(actual, expected)
    except (ValueError, TypeError):
        return False
