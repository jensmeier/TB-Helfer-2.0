-- Add independent data/system kinds; old archives remain identifiable/readable.
ALTER TABLE tb.backup_runs DROP CONSTRAINT backup_runs_kind_check;
ALTER TABLE tb.backup_runs ADD CONSTRAINT backup_runs_kind_check
    CHECK (kind IN ('DATABASE','FILES','FULL','USER','TRACK','SYSTEM','RESTORE_TEST','RESTORE_TARGET'));
