-- TB-Server 1.0: mark backup artifacts removed by retention without
-- pretending they are failed backups.
-- Immutable after application.

ALTER TABLE tb.backup_runs
    DROP CONSTRAINT IF EXISTS backup_runs_status_check;

ALTER TABLE tb.backup_runs
    ADD CONSTRAINT backup_runs_status_check
    CHECK (status IN ('RUNNING','OK','FAILED','PURGED'));
