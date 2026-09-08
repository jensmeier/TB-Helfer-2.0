BEGIN;

-- TB-Helfer 0.6.1
-- Versionsnummern fuer Katalogobjekte, Abschnittshistorie und idempotente Android-Mutationen.

ALTER TABLE tb.routes ADD COLUMN IF NOT EXISTS current_version integer NOT NULL DEFAULT 1;
ALTER TABLE tb.stations ADD COLUMN IF NOT EXISTS current_version integer NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS tb.route_section_versions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  route_section_id uuid NOT NULL REFERENCES tb.route_sections(id) ON DELETE CASCADE,
  version_no integer NOT NULL,
  changed_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
  changed_by_device uuid REFERENCES tb.devices(id) ON DELETE SET NULL,
  changed_at timestamptz NOT NULL DEFAULT now(),
  change_reason text,
  snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
  geom geometry(LineString),
  UNIQUE(route_section_id, version_no)
);
CREATE INDEX IF NOT EXISTS ix_tb_route_section_versions_section
  ON tb.route_section_versions(route_section_id, version_no DESC);

CREATE TABLE IF NOT EXISTS tb.sync_mutation_receipts (
  device_id uuid NOT NULL REFERENCES tb.devices(id) ON DELETE CASCADE,
  client_change_id text NOT NULL,
  http_status integer NOT NULL,
  response jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(device_id, client_change_id)
);
CREATE INDEX IF NOT EXISTS ix_tb_sync_mutation_receipts_created
  ON tb.sync_mutation_receipts(created_at);

CREATE INDEX IF NOT EXISTS ix_tb_change_log_entity
  ON tb.change_log(entity_type, entity_id, sequence_no DESC);
CREATE INDEX IF NOT EXISTS ix_tb_change_log_changed_at
  ON tb.change_log(changed_at);

INSERT INTO tb.schema_versions(version,name)
VALUES (8,'008_incremental_sync_mutations')
ON CONFLICT (version) DO NOTHING;

COMMIT;
