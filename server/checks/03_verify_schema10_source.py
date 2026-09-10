#!/usr/bin/env python3
"""Read-only/static contract check for the Schema-10 feature bundle.

Runs without PostgreSQL and without importing FastAPI. It catches syntax errors and accidental
loss of the endpoints/tables that Android 200019 depends on. It deliberately does not replace
02_verify_schema10.sql or the real two-device test.
"""
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
APP_PARTS = SERVER / "app_parts"
MIGRATION = SERVER / "migrations" / "010_worksite_invitations_company_profile.sql"
ANDROID = ROOT / "app" / "src" / "main" / "java" / "de" / "tbhelfer"


def require_text(path: Path, needles: list[str]) -> None:
    if not path.is_file():
        raise AssertionError(f"MISSING_FILE:{path.relative_to(ROOT)}")
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise AssertionError(f"MISSING_CONTRACT:{path.relative_to(ROOT)}:{needle}")


def main() -> int:
    # Every split server part must at least parse as Python before it can be deployed.
    parts = sorted(APP_PARTS.glob("[0-9][0-9]_part.py"))
    if not parts:
        raise AssertionError("APP_PARTS_MISSING")
    for part in parts:
        compile(part.read_bytes(), str(part), "exec")

    require_text(
        MIGRATION,
        [
            "tb.worksite_mobile_origins",
            "tb.worksite_invitations",
            "tb.worksite_invitation_redemptions",
            "tb.company_profiles",
            "lead_name_ordinal",
            "010_worksite_invitations_company_profile",
        ],
    )
    require_text(
        APP_PARTS / "14_part.py",
        [
            "/api/v1/collab/worksites/{worksite_id}/team",
            "/api/v1/collab/worksites/{worksite_id}/invitations",
            "/api/v1/collab/invitations/redeem",
            "/api/v1/company/profile",
        ],
    )
    require_text(
        APP_PARTS / "15_part.py",
        [
            "client_base_version=req.base_version",
            "exact_base=(",
            "force_merge=True",
            "merge_team_btb_payload",
        ],
    )
    require_text(
        APP_PARTS / "16_part.py",
        [
            "/api/v1/collab/worksites",
            "tb.worksite_mobile_origins",
            "COMPANY_REQUIRED",
        ],
    )
    require_text(
        APP_PARTS / "17_part.py",
        [
            "/api/v1/collab/worksites/{worksite_id}/media",
            "_media_row_visible",
            "member_role",
            "LEAD",
            "collab_attachment_download_media_lead",
            "parent_payload",
        ],
    )
    require_text(
        ANDROID / "datenbank" / "BtbTeamMediaSync.kt",
        ["BTBP1__", "MAX_ATTACHMENT_FILENAME_LENGTH = 160", "TYPE_BTB"],
    )
    require_text(
        ANDROID / "ui" / "sync" / "BaustellenSyncFragment.kt",
        ["scanInvitationQr", "showHelperScopeDialog", "showInvitationQr"],
    )
    require_text(
        ANDROID / "ui" / "bautagebuch" / "BtbDokumentIdentitaet.kt",
        [
            "TeamDisplayCache.ownTeam",
            "BTB_${datumIso}_${schichtToken}_${verantwortlicherToken}",
            "verantwortlicherTb",
            "worksiteServerId",
            "groupId",
        ],
    )
    require_text(
        ANDROID / "ui" / "bautagebuch" / "MergeSafeBaustellenExport.kt",
        [
            "BTB_",
            "TB-Helfer",
            "TeamDisplayCache",
            "forcedShift",
            "BTB_(\\\\d{4}-\\\\d{2}-\\\\d{2})\\\\.(pdf|docx)",
            "GesamtMedienArchiv.ergaenzen",
        ],
    )
    require_text(
        ANDROID / "ui" / "bautagebuch" / "GesamtMedienArchiv.kt",
        [
            "Bilderliste_gesamt.xlsx",
            "Mängel/",
            "Maengelliste_Gesamt.csv",
            "Abnahmeliste_Gesamt.csv",
            "SyncMangelSpeicher",
            "WorksiteMediaApi",
            "synchronizeBeforeExport",
        ],
    )
    require_text(
        ANDROID / "ui" / "bautagebuch" / "GesamtBilderlisteXlsx.kt",
        [
            "autoFilter",
            "state=\"frozen\"",
            "hyperlink",
            "Verantwortlicher TB",
            "Bilddatei",
        ],
    )
    require_text(
        ANDROID / "server" / "WorksiteMediaApi.kt",
        [
            "/api/v1/collab/worksites/",
            "/media",
            "responsibleTb",
            "download",
        ],
    )
    require_text(
        ANDROID / "server" / "SofortSync.kt",
        ["OneTimeWorkRequestBuilder<SyncWorker>", "ExistingWorkPolicy.KEEP"],
    )
    require_text(
        ANDROID / "datenbank" / "FotoRepository.kt",
        ["SofortSync.anstossen"],
    )
    require_text(
        ANDROID / "ui" / "baustelle" / "BtbVerlaufFragment.kt",
        [
            "BTB $verantwortlicherTb",
            "BtbDokumentIdentitaet.aufloesen",
            "MergeSafeBaustellenExport.normalisieren",
            "schicht = item.schicht",
        ],
    )

    print(f"SCHEMA10_SOURCE_OK parts={len(parts)}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"SCHEMA10_SOURCE_FAILED:{exc}", file=sys.stderr)
        raise
