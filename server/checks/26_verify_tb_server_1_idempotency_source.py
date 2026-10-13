from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    idem = read("server/tbserver/idempotency.py")
    track = read("server/tbserver/track_data.py")
    defects = read("server/tbserver/defects.py")
    acceptances = read("server/tbserver/acceptances.py")
    baseline = read("server/v1_migrations/001_server_1_0_base.sql")

    for rel, source in (
        ("server/tbserver/idempotency.py", idem),
        ("server/tbserver/track_data.py", track),
        ("server/tbserver/defects.py", defects),
        ("server/tbserver/acceptances.py", acceptances),
    ):
        ast.parse(source, filename=rel)

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
        ("DEFECT_CREATE", defects),
        ("DEFECT_UPDATE", defects),
        ("DEFECT_CLOSE", defects),
        ("ACCEPTANCE_CREATE", acceptances),
        ("ACCEPTANCE_UPDATE", acceptances),
        ("ACCEPTANCE_FINALIZE", acceptances),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Idempotenz-Vertragsmarker: " + ", ".join(missing))

    for label, source in (("track", track), ("defects", defects), ("acceptances", acceptances)):
        if "begin_mutation" not in source or "finish_mutation" not in source or "replay_payload" not in source:
            raise SystemExit(f"Idempotenz-Helper fehlen in {label}")

    print("OK: TB-Server 1.0 mutation receipts / track-defect-acceptance idempotency")


if __name__ == "__main__":
    main()
