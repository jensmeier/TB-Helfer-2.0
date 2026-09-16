BEGIN;

-- Gemeinsamer fachlicher Baustellenbaum unterhalb der bereits vorhandenen Server-Baustelle.
-- Lokale Android-Room-IDs werden absichtlich NICHT gespeichert. client_node_key ist die stabile
-- geraeteuebergreifende Identitaet; Parent-Zuordnung liegt im Payload als parent_client_node_key.
CREATE TABLE IF NOT EXISTS tb.collab_worksite_nodes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    client_node_key text NOT NULL,
    area_scope jsonb NOT NULL DEFAULT '{"all":true}'::jsonb CHECK (jsonb_typeof(area_scope)='object'),
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status='ACTIVE'),
    payload jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(payload)='object'),
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    updated_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    retain_permanent boolean NOT NULL DEFAULT true,
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    UNIQUE(worksite_id,client_node_key)
);
CREATE INDEX IF NOT EXISTS collab_worksite_nodes_worksite_idx
    ON tb.collab_worksite_nodes(worksite_id,deleted_at,updated_at);
CREATE INDEX IF NOT EXISTS collab_worksite_nodes_parent_key_idx
    ON tb.collab_worksite_nodes(worksite_id,((payload->>'parent_client_node_key')))
    WHERE deleted_at IS NULL;

DROP TRIGGER IF EXISTS trg_collab_worksite_nodes_updated_at ON tb.collab_worksite_nodes;
CREATE TRIGGER trg_collab_worksite_nodes_updated_at
BEFORE UPDATE ON tb.collab_worksite_nodes
FOR EACH ROW EXECUTE FUNCTION tb.set_updated_at();

-- -------------------------------------------------------------------------
-- Mobile/bearbeitete Streckendaten
-- -------------------------------------------------------------------------
-- Originale Vermessungsdateien (TRA/GRA/KF/KOR usw.) bleiben unveraendert auf dem NAS.
-- Vom Handy wird ausschliesslich die fachliche Korrektur-/Berechnungsebene synchronisiert.
-- Ein Datensatz entspricht einer Quellachse (quelle_datei + achse_name) innerhalb einer Firma.
CREATE TABLE IF NOT EXISTS tb.mobile_track_data (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id bigint NOT NULL REFERENCES tb.companies(id) ON DELETE CASCADE,
    dataset_key text NOT NULL,
    quelle_datei text NOT NULL,
    achse_name text NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(payload)='object'),
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    updated_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    UNIQUE(company_id,dataset_key)
);
CREATE INDEX IF NOT EXISTS mobile_track_data_company_idx
    ON tb.mobile_track_data(company_id,deleted_at,updated_at);
CREATE INDEX IF NOT EXISTS mobile_track_data_source_idx
    ON tb.mobile_track_data(company_id,quelle_datei,achse_name);

DROP TRIGGER IF EXISTS trg_mobile_track_data_updated_at ON tb.mobile_track_data;
CREATE TRIGGER trg_mobile_track_data_updated_at
BEFORE UPDATE ON tb.mobile_track_data
FOR EACH ROW EXECUTE FUNCTION tb.set_updated_at();

-- -------------------------------------------------------------------------
-- Baustelle global loeschen
-- -------------------------------------------------------------------------
-- Die Worksite-Zeile bleibt als Soft-Delete/Audit-Anker erhalten. Der Tombstone ist absichtlich
-- firmenbezogen abrufbar, damit andere Geraete die lokale Baustelle entfernen koennen, nachdem
-- ihre Mitgliedschaft serverseitig bereits geloescht wurde.
ALTER TABLE tb.worksites
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

CREATE TABLE IF NOT EXISTS tb.worksite_delete_tombstones (
    worksite_id uuid PRIMARY KEY REFERENCES tb.worksites(id) ON DELETE CASCADE,
    company_id bigint NOT NULL REFERENCES tb.companies(id) ON DELETE CASCADE,
    worksite_name text,
    deleted_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    deleted_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS worksite_delete_tombstones_company_idx
    ON tb.worksite_delete_tombstones(company_id,deleted_at);

-- Der vorhandene Collaboration-Transport darf den neuen Knotentyp mitfuehren. Attachments bleiben
-- weiterhin ausschliesslich an BTB/Mangel/Abnahme erlaubt; Baustellenknoten selbst haben keine
-- Dateianhaenge. Mobile Streckendaten laufen bewusst ueber einen firmenweiten Snapshot-Endpunkt.
ALTER TABLE tb.collab_change_log
    DROP CONSTRAINT IF EXISTS collab_change_log_entity_type_check;
ALTER TABLE tb.collab_change_log
    ADD CONSTRAINT collab_change_log_entity_type_check
    CHECK (entity_type IN ('BTB','DEFECT','ACCEPTANCE','WORKSITE_NODE','ATTACHMENT'));

ALTER TABLE tb.collab_mutation_receipts
    DROP CONSTRAINT IF EXISTS collab_mutation_receipts_entity_type_check;
ALTER TABLE tb.collab_mutation_receipts
    ADD CONSTRAINT collab_mutation_receipts_entity_type_check
    CHECK (entity_type IN ('BTB','DEFECT','ACCEPTANCE','WORKSITE_NODE'));

INSERT INTO tb.schema_versions(version,name)
VALUES (13,'013_worksite_structure_track_sync')
ON CONFLICT (version) DO UPDATE SET name=EXCLUDED.name;

COMMIT;
