-- TB-Server 1.0: fachliches Ergebnis einer Abnahme getrennt vom Lebenszyklusstatus.
-- Immutable after application.

ALTER TABLE tb.acceptances
    ADD COLUMN result text;

UPDATE tb.acceptances
SET result = CASE
    WHEN payload->>'ergebnis' IN (
        'ENTWURF',
        'ABGENOMMEN',
        'NICHT_ABGENOMMEN',
        'ABGENOMMEN_MIT_RESTPUNKTEN',
        'NOCH_ZU_PRUEFEN'
    ) THEN payload->>'ergebnis'
    WHEN status = 'OPEN' THEN 'ENTWURF'
    WHEN status = 'IN_PROGRESS' THEN 'NOCH_ZU_PRUEFEN'
    ELSE NULL
END
WHERE result IS NULL;

ALTER TABLE tb.acceptances
    ALTER COLUMN result SET DEFAULT 'ENTWURF';

ALTER TABLE tb.acceptances
    ADD CONSTRAINT acceptances_result_check
    CHECK (
        result IS NULL OR result IN (
            'ENTWURF',
            'ABGENOMMEN',
            'NICHT_ABGENOMMEN',
            'ABGENOMMEN_MIT_RESTPUNKTEN',
            'NOCH_ZU_PRUEFEN'
        )
    );

ALTER TABLE tb.acceptances
    ADD CONSTRAINT acceptances_finalized_result_check
    CHECK (
        status <> 'FINALIZED'
        OR result IS NULL
        OR result IN ('ABGENOMMEN', 'NICHT_ABGENOMMEN')
    );

COMMENT ON COLUMN tb.acceptances.result IS
    'Fachliches Abnahmeergebnis. FINALIZED wird fuer neue Server-1.0-Schreibvorgaenge nur mit ABGENOMMEN oder NICHT_ABGENOMMEN gesetzt. NULL bleibt nur fuer vor Migration 008 bereits finalisierte Test-/Altzeilen moeglich.';
