from __future__ import annotations

import argparse
import os

from .database import transaction
from .security import hash_password


def create_initial_admin() -> None:
    login = (os.getenv("TB_BOOTSTRAP_ADMIN_LOGIN") or "").strip()
    password = os.getenv("TB_BOOTSTRAP_ADMIN_PASSWORD") or ""
    display_name = (os.getenv("TB_BOOTSTRAP_ADMIN_NAME") or "TB-Helfer Administrator").strip()
    if not login or not password:
        raise SystemExit("TB_BOOTSTRAP_ADMIN_LOGIN und TB_BOOTSTRAP_ADMIN_PASSWORD müssen gesetzt sein.")
    with transaction() as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT id, login_name FROM tb.users WHERE company_role = 'ADMIN' AND deleted_at IS NULL ORDER BY created_at LIMIT 1"
        )
        existing = cur.fetchone()
        if existing:
            raise SystemExit(f"Admin existiert bereits: {existing['login_name']}")
        cur.execute(
            """
            INSERT INTO tb.users(display_name, login_name, company_role)
            VALUES (%s,%s,'ADMIN')
            RETURNING id
            """,
            (display_name, login),
        )
        user = cur.fetchone()
        cur.execute(
            """
            INSERT INTO tb.portal_credentials(
                user_id, password_hash, must_change_password, activated_at, updated_at
            ) VALUES (%s,%s,false,now(),now())
            """,
            (user["id"], hash_password(password)),
        )
    print(f"Admin angelegt: {login}")


def main() -> None:
    parser = argparse.ArgumentParser(description="TB-Server 1.0 Bootstrap")
    parser.add_argument("command", choices=["create-admin"])
    args = parser.parse_args()
    if args.command == "create-admin":
        create_initial_admin()


if __name__ == "__main__":
    main()
