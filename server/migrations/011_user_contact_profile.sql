BEGIN;

-- TB-Helfer Schema 11
-- Persoenliche Kontaktdaten je Benutzer, getrennt vom zentralen Firmenprofil.
-- Firmenanschrift/Firmentelefon/Firmen-E-Mail bleiben in tb.company_profiles.

ALTER TABLE tb.users
    ADD COLUMN IF NOT EXISTS phone text;

ALTER TABLE tb.users
    ADD COLUMN IF NOT EXISTS profile_version bigint;

UPDATE tb.users
SET profile_version=1
WHERE profile_version IS NULL;

ALTER TABLE tb.users
    ALTER COLUMN profile_version SET DEFAULT 1,
    ALTER COLUMN profile_version SET NOT NULL;

ALTER TABLE tb.users
    DROP CONSTRAINT IF EXISTS users_profile_version_ck;
ALTER TABLE tb.users
    ADD CONSTRAINT users_profile_version_ck CHECK (profile_version >= 1);

INSERT INTO tb.schema_versions(version,name)
VALUES (11,'011_user_contact_profile')
ON CONFLICT (version) DO NOTHING;

COMMIT;
