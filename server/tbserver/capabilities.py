from __future__ import annotations

from fastapi import APIRouter

from . import API_VERSION, SERVER_VERSION

router = APIRouter(prefix="/api/v1", tags=["capabilities"])

CAPABILITIES: dict[str, bool] = {
    "server_1_0_foundation_v1": True,
    "portal_v1": True,
    "company_user_admin_v1": True,
    "worksite_tree_v1": True,
    "worksite_permissions_v1": True,
    "worksite_teams_v1": True,
    "btb_entries_v1": True,
    "btb_live_sync_v1": True,
    "btb_finalize_with_pdf_v1": False,
    "track_data_v1": False,
    "company_logo_v1": False,
    "chunk_upload_v1": False,
}


@router.get("/capabilities")
def get_capabilities() -> dict:
    return {
        "server_version": SERVER_VERSION,
        "api_version": API_VERSION,
        "capabilities": CAPABILITIES,
    }
