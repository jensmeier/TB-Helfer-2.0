from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    source = (ROOT / rel).read_text(encoding="utf-8")
    ast.parse(source, filename=rel)
    return source


def require_markers(name: str, source: str, markers: set[str]) -> None:
    missing = sorted(marker for marker in markers if marker not in source)
    if missing:
        raise SystemExit(f"{name}: fehlende Replay-/Sync-Marker: {', '.join(missing)}")


def main() -> None:
    btb = read("server/tbserver/btb.py")
    worksites = read("server/tbserver/worksites.py")
    files = read("server/tbserver/files.py")
    caps = read("server/tbserver/capabilities.py")

    require_markers(
        "btb",
        btb,
        {
            "BTB_CREATE",
            "BTB_HEADER_UPDATE",
            "BTB_ENTRY_CREATE",
            "BTB_ENTRY_UPDATE",
            "BTB_ENTRY_DELETE",
            "BTB_FINALIZE",
            "BTB_REOPEN",
            "BTB_DELETE",
            "begin_mutation",
            "finish_mutation",
            "MutationContext = Depends(mutation_context)",
        },
    )
    require_markers(
        "worksites",
        worksites,
        {
            "WORKSITE_CREATE",
            "WORKSITE_NODE_CREATE",
            "WORKSITE_NODE_UPDATE",
            "WORKSITE_NODE_DELETE",
            "WORKSITE_MEMBER_ADD",
            "WORKSITE_MEMBER_PERMISSIONS_UPDATE",
            'entity_type="WORKSITE_NODE"',
            'entity_type="WORKSITE_MEMBERSHIP"',
            "emit_change",
            "audit",
        },
    )
    require_markers(
        "files",
        files,
        {
            "UPLOAD_CREATE",
            "UPLOAD_COMPLETE",
            "UPLOAD_ABORT",
            "begin_mutation",
            "finish_mutation",
        },
    )
    require_markers("capabilities", caps, {'"idempotent_mutations_v1": True'})
    print("OK: BTB/worksite/file mutations are replay-safe")


if __name__ == "__main__":
    main()
