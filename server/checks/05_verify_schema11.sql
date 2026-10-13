-- TB-Helfer Schema 11 – READ ONLY verification
BEGIN READ ONLY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tb.schema_versions WHERE version=11 AND name='011_user_contact_profile') THEN
        RAISE EXCEPTION 'SCHEMA_11_VERSION_MISSING';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='tb' AND table_name='users' AND column_name='phone'
    ) THEN
        RAISE EXCEPTION 'SCHEMA_11_USERS_PHONE_MISSING';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='tb' AND table_name='users' AND column_name='profile_version'
          AND is_nullable='NO'
    ) THEN
        RAISE EXCEPTION 'SCHEMA_11_USERS_PROFILE_VERSION_MISSING';
    END IF;
    IF EXISTS (SELECT 1 FROM tb.users WHERE profile_version < 1 OR profile_version IS NULL) THEN
        RAISE EXCEPTION 'SCHEMA_11_PROFILE_VERSION_INVALID';
    END IF;
END $$;

SELECT 'SCHEMA_11_OK' AS result;

COMMIT;
