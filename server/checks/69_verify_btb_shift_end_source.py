from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
migration = (ROOT / "server/v1_migrations/014_btb_participant_shift_status.sql").read_text(encoding="utf-8")
participants = (ROOT / "server/tbserver/btb_participants.py").read_text(encoding="utf-8")
permissions = (ROOT / "server/tbserver/permissions.py").read_text(encoding="utf-8")
btb = (ROOT / "server/tbserver/btb.py").read_text(encoding="utf-8")
files = (ROOT / "server/tbserver/files.py").read_text(encoding="utf-8")
caps = (ROOT / "server/tbserver/capabilities.py").read_text(encoding="utf-8")

for token in (
    "CREATE TABLE tb.btb_participant_states",
    "'WORKING','FINISHED','NOT_PARTICIPATING'",
    "btb_participant_states_btb_status_idx",
):
    assert token in migration, token

for token in (
    "def ensure_participants",
    "def require_participant_writable",
    "def set_own_status",
    "def unfinished_participants",
    "BTB_PARTICIPANT_FINISHED",
):
    assert token in participants, token

for token in (
    "def is_team_lead",
    "def require_team_lead",
    "BTB_TEAM_LEAD_REQUIRED",
):
    assert token in permissions, token

for token in (
    '@router.get("/btbs/{btb_id}/participants")',
    '@router.post("/btbs/{btb_id}/shift-finish")',
    '@router.post("/btbs/{btb_id}/shift-resume")',
    '@router.patch("/btbs/{btb_id}/participants/{participant_user_id}")',
    "BTB_PARTICIPANTS_NOT_FINISHED",
    "allow_unfinished",
    "require_team_lead(cur, user, btb[\"team_id\"])",
    '"can_view_btb_pdf": is_lead',
    "require_participant_writable",
):
    assert token in btb, token

final_pdf_block = btb.split('@router.get("/btbs/{btb_id}/final-pdf")', 1)[1].split(
    '@router.post("/btbs/{btb_id}/reopen")', 1
)[0]
assert "require_team_lead" in final_pdf_block

for token in (
    "FINAL_PDF_PURPOSE",
    "is_team_lead(cur, user, row[\"team_id\"])",
    "FILE_PURPOSE_RESERVED",
    "require_participant_writable",
):
    assert token in files, token

for token in (
    '"btb_participant_shift_end_v1": True',
    '"btb_pdf_team_lead_only_v1": True',
):
    assert token in caps, token

print("OK: BTB shift-end state, team-lead finalize and lead-only PDF access are wired")
