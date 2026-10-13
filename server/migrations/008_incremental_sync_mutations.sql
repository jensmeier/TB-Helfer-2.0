BEGIN;

-- TB-Helfer 0.7.0 / Schema 8
-- Inkrementeller Katalog-Sync, Versionsnummern, ACK-Cursor und idempotente Android-Mutationen.

ALTER TABLE tb.routes ADD COLUMN IF NOT EXISTS current_version integer NOT NULL DEFAULT 1;
ALTER TABLE tb.stations ADD COLUMN IF NOT EXISTS current_version integer NOT NULL DEFAULT 1;

-- Das produktive Schema 7 besitzt bereits `device_sync_state`, dessen vollständiger historischer
-- Tabellenaufbau nicht Teil des gesicherten 0.6.0-Quellarchivs ist. Diese bestehende Tabelle wird
-- absichtlich NICHT verändert. Der neue inkrementelle Cursor erhält eine eigene, eindeutig
-- definierte Tabelle und kann dadurch ohne Annahmen über alte NOT-NULL-/PK-Spalten migriert werden.
CREATE TABLE IF NOT EXISTS tb.sync_device_state (
  device_id uuid PRIMARY KEY REFERENCES tb.devices(id) ON DELETE CASCADE,
  last_sequence_no bigint NOT NULL DEFAULT 0 CHECK (last_sequence_no >= 0),
  last_sync_at timestamptz
);

-- Gerätebezug für Änderungen/Versionen nachrüsten, ohne vorhandene Admin-Historie zu verändern.
ALTER TABLE tb.change_log ADD COLUMN IF NOT EXISTS changed_by_device uuid REFERENCES tb.devices(id) ON DELETE SET NULL;
ALTER TABLE tb.object_versions ADD COLUMN IF NOT EXISTS changed_by_device uuid REFERENCES tb.devices(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS tb.route_section_versions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  route_section_id uuid NOT NULL REFERENCES tb.route_sections(id) ON DELETE CASCADE,
  version_no integer NOT NULL CHECK (version_no >= 1),
  changed_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
  changed_by_device uuid REFERENCES tb.devices(id) ON DELETE SET NULL,
  changed_at timestamptz NOT NULL DEFAULT now(),
  change_reason text,
  snapshot jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(snapshot)='object'),
  geom geometry(LineString),
  UNIQUE(route_section_id, version_no)
);
CREATE INDEX IF NOT EXISTS ix_tb_route_section_versions_section
  ON tb.route_section_versions(route_section_id, version_no DESC);

CREATE TABLE IF NOT EXISTS tb.sync_mutation_receipts (
  device_id uuid NOT NULL REFERENCES tb.devices(id) ON DELETE CASCADE,
  client_change_id text NOT NULL CHECK (btrim(client_change_id)<>''),
  http_status integer NOT NULL CHECK (http_status BETWEEN 100 AND 599),
  response jsonb NOT NULL CHECK (jsonb_typeof(response)='object'),
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
