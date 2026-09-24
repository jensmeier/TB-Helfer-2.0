from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
portal = (ROOT / "server/templates/v1_btb.html").read_text(encoding="utf-8")
btb = (ROOT / "server/tbserver/btb.py").read_text(encoding="utf-8")

for token in (
    "Schichtstatus",
    "Schicht Ende",
    "Schicht fortsetzen",
    "BTB endgültig abschließen",
    "/shift-finish",
    "/shift-resume",
    "/participants/",
    "NOT_PARTICIPATING",
    "allow_unfinished",
    "isTeamLead()",
    "ownShiftFinished()",
    "canEditCurrentBtb()",
    "Abschluss-PDF öffnen",
):
    assert token in portal, token

assert "status==='FINALIZED'&&finalPdf&&isTeamLead()" in portal
assert "document.getElementById('headerForm').style.display=canEditCurrentBtb()" in portal
assert "document.getElementById('entryForm').style.display=canEditCurrentBtb()" in portal
assert "document.getElementById('photoUploadBox').style.display=(can('files.add')&&canEditCurrentBtb())" in portal
assert "participants=result.data.participants||[];currentUser=result.data.current_user||{};" in portal

for token in (
    '"participants": participants',
    '"current_user": {',
    '"is_team_lead": is_lead',
    '"participant_status": current_participant["status"] if current_participant else None',
    '"can_view_btb_pdf": is_lead',
):
    assert token in btb, token

print("OK: portal BTB uses shift-end state, helper read-only and lead-only final/PDF actions")
