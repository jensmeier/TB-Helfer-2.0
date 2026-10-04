-- Shared, versioned working topology. Survey originals and KM calibrations stay untouched.
CREATE TABLE tb.survey_network_entities (
 id uuid PRIMARY KEY,
 entity_type text NOT NULL CHECK (entity_type IN ('NODE','OBJECT')),
 version integer NOT NULL CHECK (version > 0),
 active boolean NOT NULL,
 station_code text NOT NULL,
 data jsonb NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX survey_network_station_idx ON tb.survey_network_entities(station_code,entity_type,id);
CREATE TABLE tb.survey_network_changes (
 change_id bigserial PRIMARY KEY,
 station_codes text[] NOT NULL,
 data jsonb NOT NULL,
 actor_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
 actor_company_id uuid REFERENCES tb.companies(id) ON DELETE SET NULL,
 changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX survey_network_changes_station_idx ON tb.survey_network_changes USING gin(station_codes);
CREATE TABLE tb.survey_network_receipts (
 user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE CASCADE,
 client_change_id text NOT NULL,
 fingerprint text NOT NULL,
 response jsonb NOT NULL,
 PRIMARY KEY(user_id,client_change_id)
);
