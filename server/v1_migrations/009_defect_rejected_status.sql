ALTER TABLE tb.defects
    DROP CONSTRAINT IF EXISTS defects_status_check;

ALTER TABLE tb.defects
    ADD CONSTRAINT defects_status_check
    CHECK (status IN ('OPEN','IN_PROGRESS','REJECTED','CLOSED','DELETED'));
