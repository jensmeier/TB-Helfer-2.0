from __future__ import annotations

from fastapi import APIRouter

from . import API_VERSION, SERVER_VERSION

router = APIRouter(prefix="/api/v1", tags=["capabilities"])

CAPABILITIES: dict[str, bool] = {
    "server_1_0_foundation_v1": True,
    "portal_v1": True,
    "company_user_admin_v1": True,
    "admin_user_edit_v1": True,
    "portal_identity_activation_v1": True,
    "user_profile_v1": True,
    "user_directory_v1": True,
    "global_catalog_v1": True,
    "catalog_history_v1": True,
    "catalog_no_global_delete_v1": True,
    "device_activation_v1": True,
    "device_bearer_auth_v1": True,
    "device_fach_api_v1": True,
    "release_catalog_v1": True,
    "release_binary_v1": True,
    "client_compatibility_v1": True,
    "worksite_tree_v1": True,
    "worksite_provisioning_v1": True,
    "worksite_delete_v1": True,
    "worksite_permissions_v1": True,
    "worksite_area_scope_v1": True,
    "worksite_teams_v1": True,
    "btb_entries_v1": True,
    "btb_live_sync_v1": True,
    "btb_finalize_with_pdf_v1": True,
    "btb_participant_shift_end_v1": True,
    "btb_general_work_entries_v1": True,
    "btb_photo_metadata_v1": True,
    "btb_pdf_team_lead_only_v1": True,
    "files_v1": True,
    "worksite_media_v1": True,
    "file_cursor_pagination_v1": True,
    "file_image_renditions_v1": True,
    "chunk_upload_v1": True,
    "defects_v1": True,
    "acceptances_v1": True,
    "acceptance_result_v1": True,
    "acceptance_delete_v1": True,
    "track_data_v1": True,
    "survey_originals_v1": True,
    "global_survey_reference_v1": True,
    "survey_import_preview_v1": True,
    "survey_parser_catalog_v1": True,
    "survey_section_calibration_v1": True,
    "survey_projection_v1": True,
    "survey_station_merge_v1": True,
    "survey_gra_jump_detection_v1": True,
    "survey_reference_catalog_v1": True,
    "survey_connection_network_v1": True,
    "survey_reference_contributions_v1": True,
    "survey_reference_change_feed_v1": True,
    "idempotent_mutations_v1": True,
    "sync_change_feed_v1": True,
    "sync_change_wait_v1": True,
    "admin_portal_session_split_v1": True,
    "company_profile_v1": True,
    "company_logo_v1": True,
    "company_pdf_logo_layout_v1": True,
    "company_branding_admin_ui_v1": True,
    "portal_worksite_fach_ui_v1": True,
    "worksite_invitations_v1": True,
    "worksite_open_invitations_v1": True,
    "worksite_invitation_qr_v1": True,
    "worksite_team_transfer_v1": True,
    "admin_system_status_v1": True,
    "system_automation_v1": True,
    "system_email_reports_v1": True,
    "background_jobs_v1": True,
    "backup_v1": True,
    "backup_retention_v1": True,
    "restore_test_v1": True,
    "test_data_reset_v1": True,
    "restore_target_v1": True,
    "recovery_epoch_v1": True,
}


@router.get("/capabilities")
def get_capabilities() -> dict:
    from .recovery_guard import recovery_epoch
    return {
        "recovery_id": recovery_epoch(),
        "server_version": SERVER_VERSION,
        "api_version": API_VERSION,
        "capabilities": CAPABILITIES,
    }


