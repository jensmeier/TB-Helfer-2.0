-- TB-Server 1.0: kanonischer Bereichsscope fuer Maengel und Abnahmen.
-- Immutable after application.

ALTER TABLE tb.defects
    ADD COLUMN area_scope jsonb NOT NULL DEFAULT '{"all":true}'::jsonb;

ALTER TABLE tb.acceptances
    ADD COLUMN area_scope jsonb NOT NULL DEFAULT '{"all":true}'::jsonb;

-- Die bisherigen Built-in-Profile hatten die Schreibrechte, aber keine expliziten
-- Leserechte fuer Maengel/Abnahmen. Lesen wird ab dieser Migration ebenfalls
-- serverseitig ueber require_permission() erzwungen.
UPDATE tb.permission_profiles
SET permissions = permissions || '{"defect.view":true,"acceptance.view":true}'::jsonb,
    updated_at = now()
WHERE profile_key IN ('RESPONSIBLE_TB','HELPER_STANDARD','HELPER_EXTENDED','READ_ONLY');

-- Automatisch angelegte verantwortliche TBs aus frueheren Testbaselines hatten
-- einen leeren Scope. Fuer diese Rolle bedeutet der bisherige Zustand die ganze
-- Baustelle; Helfer/READ_ONLY werden absichtlich NICHT pauschal erweitert.
UPDATE tb.worksite_memberships
SET area_scope = '{"all":true}'::jsonb,
    updated_at = now()
WHERE worksite_role IN ('RESPONSIBLE_TB','TB')
  AND area_scope = '{}'::jsonb;

CREATE INDEX defects_worksite_area_scope_idx
    ON tb.defects USING gin(area_scope);

CREATE INDEX acceptances_worksite_area_scope_idx
    ON tb.acceptances USING gin(area_scope);
