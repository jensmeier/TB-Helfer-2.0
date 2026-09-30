-- TB-Server 1.0: allow fill-only catalog identity enrichment in history.
-- Required by the parser-v4 / existing-axis object_key upgrade path.

ALTER TABLE tb.survey_catalog_history
    DROP CONSTRAINT IF EXISTS survey_catalog_history_operation_check;

ALTER TABLE tb.survey_catalog_history
    ADD CONSTRAINT survey_catalog_history_operation_check
    CHECK (operation IN ('CREATE','UPDATE','RESOLVE','STATUS','ENRICH'));
