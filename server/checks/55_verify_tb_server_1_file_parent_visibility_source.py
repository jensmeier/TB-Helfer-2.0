from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    files = read("server/tbserver/files.py")
    workflow = read(".github/workflows/tb-server-v1.yml")
    ast.parse(files, filename="server/tbserver/files.py")

    assertions = [
        ("def _entity_link_visible(", files),
        ('normalized == "BTB"', files),
        ("FROM tb.team_members", files),
        ('has_permission(context, "btb.view")', files),
        ('has_permission(context, "defect.view")', files),
        ('has_permission(context, "acceptance.view")', files),
        ('normalized == "DEFECT"', files),
        ('normalized == "ACCEPTANCE"', files),
        ("area_scope_allows(context[\"effective_area_scope\"]", files),
        ("def _file_visible_for_context(", files),
        ('raise HTTPException(403, "FILE_LINK_TARGET_NOT_VISIBLE")', files),
        ('raise HTTPException(403, "FILE_ACCESS_DENIED")', files),
        ("_file_visible_for_context(cur, user, context, row[\"id\"])", files),
        ("55_verify_tb_server_1_file_parent_visibility_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Parent-Sichtbarkeitsmarker fuer Dateien: " + ", ".join(missing)
        )

    print("OK: Server 1.0 files inherit BTB/team and defect/acceptance area visibility")


if __name__ == "__main__":
    main()
