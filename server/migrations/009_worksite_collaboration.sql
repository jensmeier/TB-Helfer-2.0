BEGIN;

-- TB-Helfer 0.7.0
-- Firmengebundene Baustellen-Teams, gruppenbezogener BTB,
-- baustellenweite Mängel/Abnahmen, Bilder, Retention und kollaborativer Sync.

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
    area_scope jsonb NOT NULL DEFAULT '{"all":false}'::jsonb
      CHECK (jsonb_typeof(area_scope)='object'),
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
    user_role text;
    user_status text;
BEGIN
    SELECT worksite_id INTO grp_worksite FROM tb.worksite_groups WHERE id=NEW.group_id;
    IF grp_worksite IS NULL OR grp_worksite <> NEW.worksite_id THEN
        RAISE EXCEPTION 'GROUP_WORKSITE_MISMATCH';
    END IF;
    SELECT company_id INTO ws_company FROM tb.worksites WHERE id=NEW.worksite_id;
    SELECT company_id,role,status INTO user_company,user_role,user_status FROM tb.users WHERE id=NEW.user_id;
    IF ws_company IS NULL THEN RAISE EXCEPTION 'WORKSITE_COMPANY_REQUIRED'; END IF;
    IF user_company IS NULL OR user_company <> ws_company THEN RAISE EXCEPTION 'COMPANY_MISMATCH'; END IF;
    IF user_role <> 'USER' OR user_status <> 'ACTIVE' THEN RAISE EXCEPTION 'USER_NOT_ACTIVE'; END IF;
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
    user_role text;
    user_status text;
BEGIN
    IF NEW.lead_user_id IS NULL THEN RETURN NEW; END IF;
    SELECT company_id INTO ws_company FROM tb.worksites WHERE id=NEW.worksite_id;
    SELECT company_id,full_name,role,status INTO user_company,user_name,user_role,user_status FROM tb.users WHERE id=NEW.lead_user_id;
    IF ws_company IS NULL THEN RAISE EXCEPTION 'WORKSITE_COMPANY_REQUIRED'; END IF;
    IF user_company IS NULL OR user_company <> ws_company THEN RAISE EXCEPTION 'COMPANY_MISMATCH'; END IF;
    IF user_role <> 'USER' OR user_status <> 'ACTIVE' THEN RAISE EXCEPTION 'USER_NOT_ACTIVE'; END IF;
    NEW.lead_name_snapshot := COALESCE(NULLIF(NEW.lead_name_snapshot,''),user_name);
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
    status text NOT NULL DEFAULT 'ENTWURF' CHECK (status IN ('ENTWURF','FERTIG')),
    entry_date date,
    shift text,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(payload)='object'),
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
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

-- BTB darf niemals versehentlich mit einer TB-Gruppe einer anderen Baustelle verknüpft werden.
CREATE OR REPLACE FUNCTION tb.validate_collab_btb_group_worksite()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    grp_worksite uuid;
BEGIN
    SELECT worksite_id INTO grp_worksite FROM tb.worksite_groups WHERE id=NEW.group_id;
    IF grp_worksite IS NULL OR grp_worksite <> NEW.worksite_id THEN
        RAISE EXCEPTION 'BTB_GROUP_WORKSITE_MISMATCH';
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_validate_collab_btb_group_worksite ON tb.collab_btb_entries;
CREATE TRIGGER trg_validate_collab_btb_group_worksite
BEFORE INSERT OR UPDATE OF worksite_id,group_id ON tb.collab_btb_entries
FOR EACH ROW EXECUTE FUNCTION tb.validate_collab_btb_group_worksite();

CREATE TABLE IF NOT EXISTS tb.collab_defects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    client_defect_key text NOT NULL,
    area_scope jsonb NOT NULL DEFAULT '{"all":true}'::jsonb
      CHECK (jsonb_typeof(area_scope)='object'),
    status text NOT NULL DEFAULT 'OFFEN' CHECK (status IN ('OFFEN','IN_ARBEIT','ERLEDIGT','ABGELEHNT')),
    payload jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(payload)='object'),
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
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
    area_scope jsonb NOT NULL DEFAULT '{"all":true}'::jsonb
      CHECK (jsonb_typeof(area_scope)='object'),
    status text NOT NULL DEFAULT 'ENTWURF'
      CHECK (status IN ('ENTWURF','NOCH_ZU_PRUEFEN','ABGENOMMEN_MIT_RESTPUNKTEN','ABGENOMMEN','NICHT_ABGENOMMEN')),
    payload jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(payload)='object'),
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
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

-- Endgültig erledigte Vorgänge dürfen weder durch API-Fehler noch direkte SQL-Pflege
-- wieder in einen offenen Status zurückgesetzt werden.
CREATE OR REPLACE FUNCTION tb.prevent_reopening_collab_final_status()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME='collab_defects' THEN
        IF OLD.status='ERLEDIGT' AND NEW.status IS DISTINCT FROM OLD.status THEN
            RAISE EXCEPTION 'DEFECT_FINAL_STATUS';
        END IF;
    ELSIF TG_TABLE_NAME='collab_acceptances' THEN
        IF OLD.status IN ('ABGENOMMEN','NICHT_ABGENOMMEN')
           AND NEW.status IS DISTINCT FROM OLD.status THEN
            RAISE EXCEPTION 'ACCEPTANCE_FINAL_STATUS';
        END IF;
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_prevent_reopening_collab_defect ON tb.collab_defects;
CREATE TRIGGER trg_prevent_reopening_collab_defect
BEFORE UPDATE OF status ON tb.collab_defects
FOR EACH ROW EXECUTE FUNCTION tb.prevent_reopening_collab_final_status();
DROP TRIGGER IF EXISTS trg_prevent_reopening_collab_acceptance ON tb.collab_acceptances;
CREATE TRIGGER trg_prevent_reopening_collab_acceptance
BEFORE UPDATE OF status ON tb.collab_acceptances
FOR EACH ROW EXECUTE FUNCTION tb.prevent_reopening_collab_final_status();

CREATE TABLE IF NOT EXISTS tb.collab_attachments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    entity_type text NOT NULL CHECK (entity_type IN ('BTB','DEFECT','ACCEPTANCE')),
    entity_id uuid NOT NULL,
    original_filename text,
    mime_type text,
    storage_path text NOT NULL,
    sha256 text,
    size_bytes bigint NOT NULL DEFAULT 0 CHECK (size_bytes >= 0),
    uploaded_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    retain_permanent boolean NOT NULL DEFAULT false,
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE INDEX IF NOT EXISTS collab_attachments_entity_idx ON tb.collab_attachments(entity_type,entity_id,deleted_at);
CREATE INDEX IF NOT EXISTS collab_attachments_expiry_idx ON tb.collab_attachments(expires_at) WHERE deleted_at IS NULL AND expires_at IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS collab_attachments_sha_active_uq
    ON tb.collab_attachments(entity_type,entity_id,sha256) WHERE deleted_at IS NULL AND sha256 IS NOT NULL;

-- Polymorphe Attachment-Zuordnung auf Datenbankebene absichern: Parent muss existieren
-- und zur identischen Baustelle gehören.
CREATE OR REPLACE FUNCTION tb.validate_collab_attachment_parent()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    parent_worksite uuid;
BEGIN
    CASE NEW.entity_type
        WHEN 'BTB' THEN
            SELECT worksite_id INTO parent_worksite FROM tb.collab_btb_entries WHERE id=NEW.entity_id;
        WHEN 'DEFECT' THEN
            SELECT worksite_id INTO parent_worksite FROM tb.collab_defects WHERE id=NEW.entity_id;
        WHEN 'ACCEPTANCE' THEN
            SELECT worksite_id INTO parent_worksite FROM tb.collab_acceptances WHERE id=NEW.entity_id;
        ELSE
            parent_worksite := NULL;
    END CASE;
    IF parent_worksite IS NULL THEN
        RAISE EXCEPTION 'ATTACHMENT_PARENT_NOT_FOUND';
    END IF;
    IF parent_worksite <> NEW.worksite_id THEN
        RAISE EXCEPTION 'ATTACHMENT_WORKSITE_MISMATCH';
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_validate_collab_attachment_parent ON tb.collab_attachments;
CREATE TRIGGER trg_validate_collab_attachment_parent
BEFORE INSERT OR UPDATE OF worksite_id,entity_type,entity_id ON tb.collab_attachments
FOR EACH ROW EXECUTE FUNCTION tb.validate_collab_attachment_parent();

CREATE TABLE IF NOT EXISTS tb.collab_change_log (
    sequence_no bigserial PRIMARY KEY,
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    group_id uuid REFERENCES tb.worksite_groups(id) ON DELETE CASCADE,
    entity_type text NOT NULL CHECK (entity_type IN ('BTB','DEFECT','ACCEPTANCE','ATTACHMENT')),
    entity_id uuid NOT NULL,
    operation text NOT NULL CHECK (operation IN ('CREATE','UPDATE','DELETE')),
    entity_version integer CHECK (entity_version IS NULL OR entity_version >= 1),
    changed_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    changed_by_device uuid REFERENCES tb.devices(id) ON DELETE SET NULL,
    area_scope jsonb CHECK (area_scope IS NULL OR jsonb_typeof(area_scope)='object'),
    payload jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(payload)='object'),
    changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS collab_change_worksite_seq_idx ON tb.collab_change_log(worksite_id,sequence_no);
CREATE INDEX IF NOT EXISTS collab_change_group_seq_idx ON tb.collab_change_log(group_id,sequence_no) WHERE group_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS collab_change_entity_idx ON tb.collab_change_log(entity_type,entity_id,sequence_no DESC);

-- Falls ein Change eine Gruppe trägt (z. B. BTB oder BTB-Attachment), muss diese Gruppe zur
-- gleichen Baustelle gehören. Das verhindert Datenlecks durch falsch zusammengesetzte Logzeilen.
CREATE OR REPLACE FUNCTION tb.validate_collab_change_group_worksite()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    grp_worksite uuid;
BEGIN
    IF NEW.group_id IS NULL THEN RETURN NEW; END IF;
    SELECT worksite_id INTO grp_worksite FROM tb.worksite_groups WHERE id=NEW.group_id;
    IF grp_worksite IS NULL OR grp_worksite <> NEW.worksite_id THEN
        RAISE EXCEPTION 'CHANGE_GROUP_WORKSITE_MISMATCH';
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_validate_collab_change_group_worksite ON tb.collab_change_log;
CREATE TRIGGER trg_validate_collab_change_group_worksite
BEFORE INSERT OR UPDATE OF worksite_id,group_id ON tb.collab_change_log
FOR EACH ROW EXECUTE FUNCTION tb.validate_collab_change_group_worksite();

CREATE TABLE IF NOT EXISTS tb.collab_device_state (
    device_id uuid NOT NULL REFERENCES tb.devices(id) ON DELETE CASCADE,
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    last_sequence_no bigint NOT NULL DEFAULT 0 CHECK (last_sequence_no >= 0),
    last_sync_at timestamptz,
    PRIMARY KEY (device_id,worksite_id)
);

CREATE TABLE IF NOT EXISTS tb.collab_mutation_receipts (
    device_id uuid NOT NULL REFERENCES tb.devices(id) ON DELETE CASCADE,
    client_change_id text NOT NULL,
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    entity_type text NOT NULL CHECK (entity_type IN ('BTB','DEFECT','ACCEPTANCE')),
    entity_id uuid,
    entity_version integer CHECK (entity_version IS NULL OR entity_version >= 1),
    http_status integer NOT NULL DEFAULT 200 CHECK (http_status BETWEEN 100 AND 599),
    response_payload jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(response_payload)='object'),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (device_id,client_change_id)
);
CREATE INDEX IF NOT EXISTS collab_mutation_receipts_created_idx ON tb.collab_mutation_receipts(created_at);

-- Aktualisierungszeit auch bei direkter SQL-Pflege zuverlässig setzen.
DROP TRIGGER IF EXISTS trg_user_storage_preferences_updated_at ON tb.user_storage_preferences;
CREATE TRIGGER trg_user_storage_preferences_updated_at BEFORE UPDATE ON tb.user_storage_preferences
FOR EACH ROW EXECUTE FUNCTION tb.set_updated_at();
DROP TRIGGER IF EXISTS trg_worksite_groups_updated_at ON tb.worksite_groups;
CREATE TRIGGER trg_worksite_groups_updated_at BEFORE UPDATE ON tb.worksite_groups
FOR EACH ROW EXECUTE FUNCTION tb.set_updated_at();
DROP TRIGGER IF EXISTS trg_worksite_group_members_updated_at ON tb.worksite_group_members;
CREATE TRIGGER trg_worksite_group_members_updated_at BEFORE UPDATE ON tb.worksite_group_members
FOR EACH ROW EXECUTE FUNCTION tb.set_updated_at();
DROP TRIGGER IF EXISTS trg_collab_btb_entries_updated_at ON tb.collab_btb_entries;
CREATE TRIGGER trg_collab_btb_entries_updated_at BEFORE UPDATE ON tb.collab_btb_entries
FOR EACH ROW EXECUTE FUNCTION tb.set_updated_at();
DROP TRIGGER IF EXISTS trg_collab_defects_updated_at ON tb.collab_defects;
CREATE TRIGGER trg_collab_defects_updated_at BEFORE UPDATE ON tb.collab_defects
FOR EACH ROW EXECUTE FUNCTION tb.set_updated_at();
DROP TRIGGER IF EXISTS trg_collab_acceptances_updated_at ON tb.collab_acceptances;
CREATE TRIGGER trg_collab_acceptances_updated_at BEFORE UPDATE ON tb.collab_acceptances
FOR EACH ROW EXECUTE FUNCTION tb.set_updated_at();

-- Nicht nur Firmenwechsel: auch Sperrung/Deaktivierung oder Rollenwechsel darf keine aktive
-- Baustellen-Zuordnung zurücklassen. Historische Fachdaten bleiben erhalten; gelöst werden nur
-- aktive Lead-/Mitgliedschaftsbeziehungen.
CREATE OR REPLACE FUNCTION tb.cleanup_memberships_after_user_company_change()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.company_id IS NOT DISTINCT FROM NEW.company_id
       AND OLD.role IS NOT DISTINCT FROM NEW.role
       AND OLD.status IS NOT DISTINCT FROM NEW.status THEN
        RETURN NEW;
    END IF;
    UPDATE tb.worksite_groups g
       SET lead_user_id=NULL,updated_at=now()
      FROM tb.worksites w
     WHERE g.worksite_id=w.id AND g.lead_user_id=NEW.id
       AND (
           NEW.company_id IS NULL
           OR w.company_id IS DISTINCT FROM NEW.company_id
           OR NEW.role IS DISTINCT FROM 'USER'
           OR NEW.status IS DISTINCT FROM 'ACTIVE'
       );
    DELETE FROM tb.worksite_group_members gm
      USING tb.worksites w
     WHERE gm.worksite_id=w.id AND gm.user_id=NEW.id
       AND (
           NEW.company_id IS NULL
           OR w.company_id IS DISTINCT FROM NEW.company_id
           OR NEW.role IS DISTINCT FROM 'USER'
           OR NEW.status IS DISTINCT FROM 'ACTIVE'
       );
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_cleanup_memberships_after_user_company_change ON tb.users;
CREATE TRIGGER trg_cleanup_memberships_after_user_company_change
AFTER UPDATE OF company_id,role,status ON tb.users
FOR EACH ROW EXECUTE FUNCTION tb.cleanup_memberships_after_user_company_change();

CREATE OR REPLACE FUNCTION tb.cleanup_memberships_after_worksite_company_change()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.company_id IS NOT DISTINCT FROM NEW.company_id THEN
        RETURN NEW;
    END IF;
    UPDATE tb.worksite_groups g
       SET lead_user_id=NULL,updated_at=now()
      FROM tb.users u
     WHERE g.worksite_id=NEW.id AND g.lead_user_id=u.id
       AND (
           NEW.company_id IS NULL
           OR u.company_id IS DISTINCT FROM NEW.company_id
           OR u.role IS DISTINCT FROM 'USER'
           OR u.status IS DISTINCT FROM 'ACTIVE'
       );
    DELETE FROM tb.worksite_group_members gm
      USING tb.users u
     WHERE gm.worksite_id=NEW.id AND gm.user_id=u.id
       AND (
           NEW.company_id IS NULL
           OR u.company_id IS DISTINCT FROM NEW.company_id
           OR u.role IS DISTINCT FROM 'USER'
           OR u.status IS DISTINCT FROM 'ACTIVE'
       );
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_cleanup_memberships_after_worksite_company_change ON tb.worksites;
CREATE TRIGGER trg_cleanup_memberships_after_worksite_company_change
AFTER UPDATE OF company_id ON tb.worksites
FOR EACH ROW EXECUTE FUNCTION tb.cleanup_memberships_after_worksite_company_change();

INSERT INTO tb.schema_versions(version,name)
VALUES (9,'009_worksite_collaboration')
ON CONFLICT (version) DO NOTHING;

COMMIT;
