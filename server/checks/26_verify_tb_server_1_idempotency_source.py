from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    idem = read("server/tbserver/idempotency.py")
    track = read("server/tbserver/track_data.py")
    baseline = read("server/v1_migrations/001_server_1_0_base.sql")

    ast.parse(idem, filename="server/tbserver/idempotency.py")
    ast.parse(track, filename="server/tbserver/track_data.py")

    assertions = [
        ("CREATE TABLE tb.mutation_receipts", baseline),
        ("UNIQUE(user_id, client_change_id)", baseline),
        ("X-Client-Change-Id", idem),
        ("pg_advisory_xact_lock", idem),
        ("hashtextextended", idem),
        ("CLIENT_CHANGE_ID_REUSED", idem),
        ("INSERT INTO tb.mutation_receipts", idem),
        ("TRACK_DATA_CREATE", track),
        ("TRACK_DATA_UPDATE", track),
        ("TRACK_DATA_DELETE", track),
        ("begin_mutation", track),
        ("finish_mutation", track),
        ("replay_payload", track),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Idempotenz-Vertragsmarker: " + ", ".join(missing))
    print("OK: TB-Server 1.0 mutation receipts / track-data idempotency")


if __name__ == "__main__":
    main()
