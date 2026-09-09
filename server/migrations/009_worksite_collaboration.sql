BEGIN;

-- TB-Helfer 0.7.0: Firmengebundene Baustellen-Teams, gruppenbezogener BTB,
-- baustellenweite Mängel/Abnahmen, Speicherregeln und kollaborativer Sync.

CREATE TABLE IF NOT EXISTS tb.user_storage_preferences (
    user_id uuid PRIMARY KEY REFERENCES tb.users(id) ON DELETE CASCADE,
    store_images boolean NOT NULL DEFAULT false,
    store_btb boolean NOT NULL DEFAULT false,
    store_defects boolean NOT NULL DEFAULT false,
    store_acceptances boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS tb.worksite_groups (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    lead_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    lead_name_snapshot text,
    label text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS worksite_groups_lead_uq
    ON tb.worksite_groups(worksite_id, lead_user_id)
    WHERE lead_user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS worksite_groups_worksite_idx ON tb.worksite_groups(worksite_id);

CREATE TABLE IF NOT EXISTS tb.worksite_group_members (
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    group_id uuid NOT NULL REFERENCES tb.worksite_groups(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE CASCADE,
    member_role text NOT NULL DEFAULT 'HELPER' CHECK (member_role IN ('LEAD','HELPER')),
    area_scope jsonb NOT NULL DEFAULT '{"all":false}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id),
    UNIQUE (worksite_id, user_id)
);
CREATE INDEX IF NOT EXISTS worksite_group_members_user_idx ON tb.worksite_group_members(user_id,worksite_id);

CREATE OR REPLACE FUNCTION tb.validate_worksite_group_membership()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    ws_company bigint;
    user_company bigint;
    grp_worksite uuid;
BEGIN
    SELECT worksite_id INTO grp_worksite FROM tb.worksite_groups WHERE id=NEW.group_id;
    IF grp_worksite IS NULL OR grp_worksite <> NEW.worksite_id THEN
        RAISE EXCEPTION 'GROUP_WORKSITE_MISMATCH';
    END IF;
    SELECT company_id INTO ws_company FROM tb.worksites WHERE id=NEW.worksite_id;
    SELECT company_id INTO user_company FROM tb.users WHERE id=NEW.user_id;
    IF ws_company IS NULL THEN
        RAISE EXCEPTION 'WORKSITE_COMPANY_REQUIRED';
    END IF;
    IF user_company IS NULL OR user_company <> ws_company THEN
        RAISE EXCEPTION 'COMPANY_MISMATCH';
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_validate_worksite_group_membership ON tb.worksite_group_members;
CREATE TRIGGER trg_validate_worksite_group_membership
BEFORE INSERT OR UPDATE ON tb.worksite_group_members
FOR EACH ROW EXECUTE FUNCTION tb.validate_worksite_group_membership();

CREATE OR REPLACE FUNCTION tb.validate_worksite_group_lead()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    ws_company bigint;
    user_company bigint;
    user_name text;
BEGIN
    IF NEW.lead_user_id IS NULL THEN RETURN NEW; END IF;
    SELECT company_id INTO ws_company FROM tb.worksites WHERE id=NEW.worksite_id;
    SELECT company_id,full_name INTO user_company,user_name FROM tb.users WHERE id=NEW.lead_user_id;
    IF ws_company IS NULL THEN RAISE EXCEPTION 'WORKSITE_COMPANY_REQUIRED'; END IF;
    IF user_company IS NULL OR user_company <> ws_company THEN RAISE EXCEPTION 'COMPANY_MISMATCH'; END IF;
    NEW.lead_name_snapshot := COALESCE(NEW.lead_name_snapshot,user_name);
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_validate_worksite_group_lead ON tb.worksite_groups;
CREATE TRIGGER trg_validate_worksite_group_lead
BEFORE INSERT OR UPDATE ON tb.worksite_groups
FOR EACH ROW EXECUTE FUNCTION tb.validate_worksite_group_lead();

CREATE TABLE IF NOT EXISTS tb.collab_btb_entries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    group_id uuid NOT NULL REFERENCES tb.worksite_groups(id) ON DELETE CASCADE,
    client_entry_id text NOT NULL,
    entry_date date,
    shift text,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    current_version integer NOT NULL DEFAULT 1,
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    updated_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    retain_permanent boolean NOT NULL DEFAULT false,
    expires_at timestamptz,
    UNIQUE (group_id, client_entry_id)
);
CREATE INDEX IF NOT EXISTS collab_btb_worksite_group_idx ON tb.collab_btb_entries(worksite_id,group_id,updated_at);
CREATE INDEX IF NOT EXISTS collab_btb_expiry_idx ON tb.collab_btb_entries(expires_at) WHERE retain_permanent=false AND expires_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS tb.collab_defects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    client_defect_key text NOT NULL,
    area_scope jsonb NOT NULL DEFAULT '{"all":true}'::jsonb,
    status text NOT NULL DEFAULT 'OFFEN',
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    current_version integer NOT NULL DEFAULT 1,
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    updated_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    retain_permanent boolean NOT NULL DEFAULT false,
    expires_at timestamptz,
    UNIQUE (worksite_id, client_defect_key)
);
CREATE INDEX IF NOT EXISTS collab_defects_worksite_idx ON tb.collab_defects(worksite_id,updated_at);
CREATE INDEX IF NOT EXISTS collab_defects_expiry_idx ON tb.collab_defects(expires_at) WHERE retain_permanent=false AND expires_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS tb.collab_acceptances (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    client_acceptance_id text NOT NULL,
    area_scope jsonb NOT NULL DEFAULT '{"all":true}'::jsonb,
    status text NOT NULL DEFAULT 'ENTWURF',
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    current_version integer NOT NULL DEFAULT 1,
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    updated_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    retain_permanent boolean NOT NULL DEFAULT false,
    expires_at timestamptz,
    UNIQUE (worksite_id, client_acceptance_id)
);
CREATE INDEX IF NOT EXISTS collab_acceptances_worksite_idx ON tb.collab_acceptances(worksite_id,updated_at);
CREATE INDEX IF NOT EXISTS collab_acceptances_expiry_idx ON tb.collab_acceptances(expires_at) WHERE retain_permanent=false AND expires_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS tb.collab_attachments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    entity_type text NOT NULL CHECK (entity_type IN ('BTB','DEFECT','ACCEPTANCE')),
    entity_id uuid NOT NULL,
    original_filename text,
    mime_type text,
    storage_path text NOT NULL,
    sha256 text,
    size_bytes bigint NOT NULL DEFAULT 0,
    uploaded_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    retain_permanent boolean NOT NULL DEFAULT false,
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE INDEX IF NOT EXISTS collab_attachments_entity_idx ON tb.collab_attachments(entity_type,entity_id,deleted_at);
CREATE INDEX IF NOT EXISTS collab_attachments_expiry_idx ON tb.collab_attachments(expires_at) WHERE deleted_at IS NULL AND expires_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS tb.collab_change_log (
    sequence_no bigserial PRIMARY KEY,
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    group_id uuid REFERENCES tb.worksite_groups(id) ON DELETE CASCADE,
    entity_type text NOT NULL CHECK (entity_type IN ('BTB','DEFECT','ACCEPTANCE','ATTACHMENT')),
    entity_id uuid NOT NULL,
    operation text NOT NULL CHECK (operation IN ('CREATE','UPDATE','DELETE')),
    entity_version integer,
    changed_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    changed_by_device uuid REFERENCES tb.devices(id) ON DELETE SET NULL,
    area_scope jsonb,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS collab_change_worksite_seq_idx ON tb.collab_change_log(worksite_id,sequence_no);
CREATE INDEX IF NOT EXISTS collab_change_group_seq_idx ON tb.collab_change_log(group_id,sequence_no) WHERE group_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS tb.collab_device_state (
    device_id uuid NOT NULL REFERENCES tb.devices(id) ON DELETE CASCADE,
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    last_sequence_no bigint NOT NULL DEFAULT 0,
    last_sync_at timestamptz,
    PRIMARY KEY (device_id,worksite_id)
);

CREATE TABLE IF NOT EXISTS tb.collab_mutation_receipts (
    device_id uuid NOT NULL REFERENCES tb.devices(id) ON DELETE CASCADE,
    client_change_id text NOT NULL,
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    entity_type text NOT NULL,
    entity_id uuid,
    entity_version integer,
    response_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (device_id,client_change_id)
);

INSERT INTO tb.schema_versions(version,name)
VALUES (9,'009_worksite_collaboration')
ON CONFLICT (version) DO NOTHING;

COMMIT;
