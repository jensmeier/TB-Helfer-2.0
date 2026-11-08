from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    acceptances = read("server/tbserver/acceptances.py")
    migration = read("server/v1_migrations/008_acceptance_result.sql")
    template = read("server/templates/v1_worksite.html")
    caps = read("server/tbserver/capabilities.py")
    workflow = read(".github/workflows/tb-server-v1.yml")

    ast.parse(acceptances, filename="server/tbserver/acceptances.py")

    assertions = [
        ('DraftAcceptanceResult = Literal["ENTWURF", "ABGENOMMEN_MIT_RESTPUNKTEN", "NOCH_ZU_PRUEFEN"]', acceptances),
        ('FinalAcceptanceResult = Literal["ABGENOMMEN", "NICHT_ABGENOMMEN"]', acceptances),
        ("result: DraftAcceptanceResult = \"ENTWURF\"", acceptances),
        ("result: FinalAcceptanceResult", acceptances),
        ("SET status = 'FINALIZED', result = %s", acceptances),
        ('raise HTTPException(409, "ACCEPTANCE_FINAL_RESULT_IMMUTABLE")', acceptances),
        ("ADD COLUMN result text", migration),
        ("acceptances_finalized_result_check", migration),
        ("ABGENOMMEN_MIT_RESTPUNKTEN", migration),
        ("NOCH_ZU_PRUEFEN", migration),
        ("finalizeAcceptance(id,version,result)", template),
        ("'ABGENOMMEN'", template),
        ("NICHT_ABGENOMMEN", template),
        ('"acceptance_result_v1": True', caps),
        ("56_verify_tb_server_1_acceptance_result_source.py", workflow),
    ]
    missing = [needle for needle, source in assertions if needle not in source]
    if missing:
        raise SystemExit(
            "Fehlende Abnahme-Ergebnis-Vertragsmarker: " + ", ".join(missing)
        )

    finalize_pos = acceptances.index("def finalize_acceptance")
    immutable_pos = acceptances.index("ACCEPTANCE_FINAL_RESULT_IMMUTABLE")
    if immutable_pos < finalize_pos:
        raise SystemExit("Finales Abnahmeergebnis muss im Finalize-Pfad unveraenderlich sein")

    print("OK: TB-Server 1.0 acceptance lifecycle/result contract")


if __name__ == "__main__":
    main()
