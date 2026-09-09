\set ON_ERROR_STOP on

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tb.schema_versions WHERE version=10) THEN
        RAISE EXCEPTION 'SCHEMA_10_NOT_REGISTERED';
    END IF;
    IF to_regclass('tb.worksite_mobile_origins') IS NULL THEN
        RAISE EXCEPTION 'WORKSITE_MOBILE_ORIGINS_MISSING';
    END IF;
    IF to_regclass('tb.worksite_invitations') IS NULL THEN
        RAISE EXCEPTION 'WORKSITE_INVITATIONS_MISSING';
    END IF;
    IF to_regclass('tb.worksite_invitation_redemptions') IS NULL THEN
        RAISE EXCEPTION 'WORKSITE_INVITATION_REDEMPTIONS_MISSING';
    END IF;
    IF to_regclass('tb.company_profiles') IS NULL THEN
        RAISE EXCEPTION 'COMPANY_PROFILES_MISSING';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='tb' AND table_name='worksite_groups' AND column_name='lead_name_ordinal'
    ) THEN
        RAISE EXCEPTION 'LEAD_NAME_ORDINAL_MISSING';
    END IF;
END $$;

SELECT version,name,applied_at
FROM tb.schema_versions
WHERE version=10;

SELECT 'SCHEMA_10_OK' AS result;
