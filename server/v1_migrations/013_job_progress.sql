ALTER TABLE tb.background_jobs
    ADD COLUMN progress_percent smallint NOT NULL DEFAULT 0
        CHECK (progress_percent BETWEEN 0 AND 100),
    ADD COLUMN progress_text text;

UPDATE tb.background_jobs
SET progress_percent = CASE WHEN status = 'DONE' THEN 100 ELSE progress_percent END,
    progress_text = CASE
        WHEN status = 'DONE' THEN 'Fertig'
        WHEN status = 'FAILED' THEN 'Fehlgeschlagen'
        WHEN status = 'RUNNING' THEN 'Läuft'
        ELSE 'Wartet'
    END;
