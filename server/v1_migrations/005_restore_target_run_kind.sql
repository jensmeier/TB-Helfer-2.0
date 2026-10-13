-- TB-Server 1.0: distinguish controlled restore-to-target runs.
-- Immutable after application.

ALTER TABLE tb.backup_runs
    DROP CONSTRAINT IF EXISTS backup_runs_kind_check;

ALTER TABLE tb.backup_runs
    ADD CONSTRAINT backup_runs_kind_check
    CHECK (kind IN ('DATABASE','FILES','FULL','RESTORE_TEST','RESTORE_TARGET'));
