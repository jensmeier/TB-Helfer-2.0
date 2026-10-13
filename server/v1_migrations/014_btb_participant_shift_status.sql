-- BTB-Schichtstatus pro Nutzer.
-- Ein Helfer beendet nur seinen eigenen Beitrag; der gemeinsame BTB wird
-- weiterhin ausschließlich durch den Teamleiter finalisiert.

CREATE TABLE tb.btb_participant_states (
    btb_id uuid NOT NULL REFERENCES tb.btbs(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE CASCADE,
    status text NOT NULL DEFAULT 'WORKING'
        CHECK (status IN ('WORKING','FINISHED','NOT_PARTICIPATING')),
    version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
    finished_at timestamptz,
    updated_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (btb_id, user_id)
);

CREATE INDEX btb_participant_states_user_idx
    ON tb.btb_participant_states(user_id, status);

CREATE INDEX btb_participant_states_btb_status_idx
    ON tb.btb_participant_states(btb_id, status);
