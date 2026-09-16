BEGIN;

-- Gemeinsamer fachlicher Baustellenbaum unterhalb der bereits vorhandenen Server-Baustelle.
-- Lokale Android-Room-IDs werden absichtlich NICHT gespeichert. client_node_key ist die stabile
-- geräteübergreifende Identität; Parent-Zuordnung liegt im Payload als parent_client_node_key.
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

-- Der vorhandene Collaboration-Transport darf den neuen Knotentyp mitführen. Attachments bleiben
-- weiterhin ausschließlich an BTB/Mangel/Abnahme erlaubt; Baustellenknoten selbst haben keine
-- Dateianhänge.
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
VALUES (13,'013_worksite_structure_sync')
ON CONFLICT (version) DO NOTHING;

COMMIT;
