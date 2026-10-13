-- TB-Server 1.0: rebuildable parser catalog for immutable survey originals.
-- Immutable after application.

CREATE TABLE tb.survey_parse_catalog (
    original_id uuid PRIMARY KEY REFERENCES tb.survey_originals(id) ON DELETE RESTRICT,
    parser_family text NOT NULL,
    parser_version text NOT NULL,
    source_sha256 text NOT NULL,
    status text NOT NULL CHECK (status IN ('PARSED','UNSUPPORTED','ERROR')),
    detected_format text,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    warnings jsonb NOT NULL DEFAULT '[]'::jsonb,
    error_code text,
    error_detail text,
    parsed_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    parsed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX survey_parse_catalog_status_idx ON tb.survey_parse_catalog(status, parser_family);
