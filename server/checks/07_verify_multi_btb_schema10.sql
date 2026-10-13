\set ON_ERROR_STOP on

-- Read-only Preflight fuer den Multi-BTB-Servercode auf dem bestehenden Schema 10.
-- Es werden keinerlei Daten oder Schemaobjekte veraendert.
BEGIN;
SET TRANSACTION READ ONLY;

DO $$
DECLARE
    unique_client_key_ok boolean;
    date_shift_unique_exists boolean;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM tb.schema_versions
        WHERE version = 10
    ) THEN
        RAISE EXCEPTION 'SCHEMA_10_NOT_REGISTERED';
    END IF;

    IF to_regclass('tb.collab_btb_entries') IS NULL THEN
        RAISE EXCEPTION 'COLLAB_BTB_ENTRIES_MISSING';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'tb'
          AND table_name = 'collab_btb_entries'
          AND column_name = 'client_entry_id'
          AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'BTB_CLIENT_ENTRY_ID_NOT_REQUIRED';
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'tb'
          AND t.relname = 'collab_btb_entries'
          AND c.contype = 'u'
          AND regexp_replace(pg_get_constraintdef(c.oid), '\\s+', ' ', 'g')
              = 'UNIQUE (group_id, client_entry_id)'
    ) INTO unique_client_key_ok;

    IF NOT unique_client_key_ok THEN
        RAISE EXCEPTION 'BTB_GROUP_CLIENT_KEY_UNIQUE_MISSING';
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'tb'
          AND tablename = 'collab_btb_entries'
          AND indexdef ILIKE 'CREATE UNIQUE%'
          AND (indexdef ILIKE '%entry_date%' OR indexdef ILIKE '%shift%')
    ) INTO date_shift_unique_exists;

    IF date_shift_unique_exists THEN
        RAISE EXCEPTION 'BTB_DATE_SHIFT_MUST_NOT_BE_UNIQUE';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tb.collab_btb_entries
        WHERE client_entry_id IS NULL OR btrim(client_entry_id) = ''
    ) THEN
        RAISE EXCEPTION 'BTB_CLIENT_ENTRY_ID_EMPTY_DATA';
    END IF;
END $$;

SELECT
    count(*) AS btb_rows,
    count(DISTINCT group_id) AS btb_groups,
    count(*) FILTER (WHERE client_entry_id LIKE 'legacy:%' OR client_entry_id LIKE '%:%') AS legacy_like_keys
FROM tb.collab_btb_entries;

SELECT 'MULTI_BTB_SCHEMA10_OK' AS result;
ROLLBACK;
