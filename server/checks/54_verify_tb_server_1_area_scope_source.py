from __future__ import annotations

import ast
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "server"))

from tbserver.permissions import area_scope_allows  # noqa: E402


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    permissions = read("server/tbserver/permissions.py")
    defects = read("server/tbserver/defects.py")
    acceptances = read("server/tbserver/acceptances.py")
    worksites = read("server/tbserver/worksites.py")
    migration = read("server/v1_migrations/007_defect_acceptance_area_scope.sql")
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    for name, source in (
        ("permissions.py", permissions),
        ("defects.py", defects),
        ("acceptances.py", acceptances),
        ("worksites.py", worksites),
    ):
        ast.parse(source, filename=name)

    assertions = [
        ("def area_scope_allows", permissions),
        ("def require_area_scope", permissions),
        ('result["effective_area_scope"]', permissions),
        ('require_permission(context, "defect.view")', defects),
        ("require_area_scope(context, defect[\"area_scope\"])", defects),
        ("area_scope_allows(context[\"effective_area_scope\"]", defects),
        ('require_permission(context, "acceptance.view")', acceptances),
        ("require_area_scope(context, acceptance[\"area_scope\"])", acceptances),
        ("area_scope_allows(context[\"effective_area_scope\"]", acceptances),
        ("ADD COLUMN area_scope jsonb", migration),
        ('"defect.view":true', migration),
        ('"acceptance.view":true', migration),
        ('\'{"all":true}\'::jsonb', worksites),
        ('"worksite_area_scope_v1": True', caps),
        ("54_verify_tb_server_1_area_scope_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit("Fehlende Area-Scope-Vertragsmarker: " + ", ".join(missing))

    cases = [
        ({"all": True}, {"all": True}, True),
        ({"labels": ["Gleis 28"]}, {"labels": ["gleis 28"]}, True),
        ({"labels": ["Gleis 28"]}, {"labels": ["W13"]}, False),
        ({"labels": ["Gleis 28"]}, {"all": True}, False),
        ({"km_from_m": 70000, "km_to_m": 71000}, {"km_from_m": 70200, "km_to_m": 70500}, True),
        ({"km_from_m": 70000, "km_to_m": 71000}, {"km_from_m": 69900, "km_to_m": 70500}, False),
        (
            {"labels": ["Gleis 28"], "km_from_m": 70000, "km_to_m": 71000},
            {"labels": ["Gleis 28"], "km_from_m": 70200, "km_to_m": 70500},
            True,
        ),
        (
            {"labels": ["Gleis 28"], "km_from_m": 70000, "km_to_m": 71000},
            {"labels": ["Gleis 28"]},
            False,
        ),
        ({}, {"labels": ["Gleis 28"]}, False),
    ]
    for user_scope, entity_scope, expected in cases:
        actual = area_scope_allows(user_scope, entity_scope)
        if actual is not expected:
            raise SystemExit(
                f"Area-Scope-Matching falsch: user={user_scope} entity={entity_scope} "
                f"expected={expected} actual={actual}"
            )

    print("OK: TB-Server 1.0 defect/acceptance area-scope contract")


if __name__ == "__main__":
    main()
