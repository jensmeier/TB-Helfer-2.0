#!/usr/bin/env python3
"""Small deterministic test for the pure team-BTB merge helpers in app_parts/15_part.py."""
from __future__ import annotations

import ast
import json
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1] / "app_parts" / "15_part.py"
PURE_FUNCTIONS = {
    "_btb_json_key",
    "_btb_unique_list",
    "_btb_merge_lines",
    "_btb_merge_items",
    "_btb_merge_firma",
    "_btb_merge_firmen",
    "merge_team_btb_payload",
}


def load_helpers():
    tree = ast.parse(SOURCE.read_text(encoding="utf-8"), filename=str(SOURCE))
    body = [node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name in PURE_FUNCTIONS]
    module = ast.Module(body=body, type_ignores=[])
    namespace = {"json": json}
    exec(compile(module, str(SOURCE), "exec"), namespace, namespace)
    return namespace


def main() -> None:
    ns = load_helpers()
    merge = ns["merge_team_btb_payload"]
    device_b = {"user_id": "user-b", "full_name": "Helfer B"}

    old = {
        "datumIso": "2026-09-10",
        "schicht": "TAG",
        "notizen": "06:10 Helfer A – Gleis 1",
        "abnahmen": {"items": [{"text": "A"}]},
        "firmen": [{"firmaId": "f1", "name": "Firma", "personal": [{"rolle": "A"}]}],
        "_syncContributors": [{"user_id": "user-a", "name": "Helfer A"}],
    }
    incoming = {
        "datumIso": "2026-09-10",
        "schicht": "TAG",
        "notizen": "06:20 Helfer B – Gleis 2",
        "abnahmen": {"items": [{"text": "B"}]},
        "firmen": [{"firmaId": "f1", "name": "Firma", "personal": [{"rolle": "B"}]}],
    }
    merged = merge(old, incoming, device_b)
    assert "Helfer A" in merged["notizen"] and "Helfer B" in merged["notizen"]
    assert merged["abnahmen"]["items"] == [{"text": "A"}, {"text": "B"}]
    assert merged["firmen"][0]["personal"] == [{"rolle": "A"}, {"rolle": "B"}]
    assert {x["user_id"] for x in merged["_syncContributors"]} == {"user-a", "user-b"}

    # Autoritativer Snapshot: Wrapper ruft merge({}, incoming, device) auf. Alte, bewusst
    # gelöschte Einträge dürfen dann nicht wieder erscheinen.
    authoritative = merge({}, incoming, device_b)
    assert "Helfer A" not in authoritative["notizen"]
    assert authoritative["abnahmen"]["items"] == [{"text": "B"}]

    # Derselbe Eintrag darf durch Retry nicht mehrfach erscheinen.
    repeated = merge(merged, incoming, device_b)
    assert repeated["abnahmen"]["items"] == [{"text": "A"}, {"text": "B"}]
    assert repeated["notizen"].count("Helfer B") == 1

    print("TEAM_BTB_MERGE_OK")


if __name__ == "__main__":
    main()
