BEGIN;

-- TB-Helfer Schema 10
-- Selbstverwaltete Baustellen, Einladungen, gleichnamige verantwortliche TBs
-- und zentrales Firmenprofil/Logo.

-- ---------------------------------------------------------------------------
-- 1. Gleichnamige verantwortliche TBs
-- ---------------------------------------------------------------------------
ALTER TABLE tb.worksite_groups
    ADD COLUMN IF NOT EXISTS lead_name_ordinal integer;

WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY worksite_id, lower(btrim(COALESCE(lead_name_snapshot,'')))
               ORDER BY created_at,id
           )::integer AS ordinal_no
    FROM tb.worksite_groups
    WHERE lead_user_id IS NOT NULL
)
UPDATE tb.worksite_groups g
SET lead_name_ordinal=ranked.ordinal_no
FROM ranked
WHERE ranked.id=g.id AND g.lead_name_ordinal IS NULL;

UPDATE tb.worksite_groups SET lead_name_ordinal=1 WHERE lead_name_ordinal IS NULL;

ALTER TABLE tb.worksite_groups
    ALTER COLUMN lead_name_ordinal SET DEFAULT 1,
    ALTER COLUMN lead_name_ordinal SET NOT NULL;

ALTER TABLE tb.worksite_groups DROP CONSTRAINT IF EXISTS worksite_groups_lead_name_ordinal_ck;
ALTER TABLE tb.worksite_groups
    ADD CONSTRAINT worksite_groups_lead_name_ordinal_ck CHECK (lead_name_ordinal >= 1);

CREATE UNIQUE INDEX IF NOT EXISTS worksite_groups_lead_name_ordinal_uq
    ON tb.worksite_groups(worksite_id,lower(btrim(lead_name_snapshot)),lead_name_ordinal)
    WHERE lead_user_id IS NOT NULL AND lead_name_snapshot IS NOT NULL AND btrim(lead_name_snapshot)<>'';

CREATE OR REPLACE FUNCTION tb.assign_worksite_group_lead_ordinal()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    user_name text;
    next_ordinal integer;
    same_identity boolean := false;
BEGIN
    IF NEW.lead_user_id IS NULL THEN
        NEW.lead_name_ordinal := 1;
        RETURN NEW;
    END IF;

    SELECT full_name INTO user_name FROM tb.users WHERE id=NEW.lead_user_id;
    NEW.lead_name_snapshot := COALESCE(NULLIF(btrim(NEW.lead_name_snapshot),''),user_name);

    IF TG_OP='UPDATE' THEN
        same_identity := NEW.lead_user_id IS NOT DISTINCT FROM OLD.lead_user_id
            AND NEW.worksite_id IS NOT DISTINCT FROM OLD.worksite_id
            AND lower(btrim(COALESCE(NEW.lead_name_snapshot,'')))
                = lower(btrim(COALESCE(OLD.lead_name_snapshot,'')));
        IF same_identity AND OLD.lead_name_ordinal IS NOT NULL THEN
            NEW.lead_name_ordinal := OLD.lead_name_ordinal;
            RETURN NEW;
        END IF;
    END IF;

    PERFORM pg_advisory_xact_lock(
        hashtextextended(
            'tb-worksite-lead-name:' || NEW.worksite_id::text || ':' || lower(btrim(COALESCE(NEW.lead_name_snapshot,''))),
            0
        )
    );

    SELECT COALESCE(max(g.lead_name_ordinal),0)+1
      INTO next_ordinal
      FROM tb.worksite_groups g
     WHERE g.worksite_id=NEW.worksite_id
       AND g.lead_user_id IS NOT NULL
       AND lower(btrim(COALESCE(g.lead_name_snapshot,'')))=lower(btrim(COALESCE(NEW.lead_name_snapshot,'')))
       AND (TG_OP='INSERT' OR g.id<>NEW.id);

    NEW.lead_name_ordinal := GREATEST(1,COALESCE(next_ordinal,1));
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_assign_worksite_group_lead_ordinal ON tb.worksite_groups;
CREATE TRIGGER trg_assign_worksite_group_lead_ordinal
BEFORE INSERT OR UPDATE OF worksite_id,lead_user_id,lead_name_snapshot ON tb.worksite_groups
FOR EACH ROW EXECUTE FUNCTION tb.assign_worksite_group_lead_ordinal();

-- ---------------------------------------------------------------------------
-- 2. Idempotente Baustellen-Erstellung direkt aus der App
-- ---------------------------------------------------------------------------
-- Eine lokale Hauptbaustelle darf bei Netzabbruch/Retry nicht versehentlich doppelt auf dem
-- Server entstehen. Der Client sendet deshalb einen stabilen, nicht geheimen lokalen Schlüssel.
CREATE TABLE IF NOT EXISTS tb.worksite_mobile_origins (
    user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE CASCADE,
    client_worksite_key text NOT NULL CHECK (btrim(client_worksite_key)<>''),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id,client_worksite_key),
    UNIQUE (worksite_id)
);
CREATE INDEX IF NOT EXISTS worksite_mobile_origins_worksite_idx
    ON tb.worksite_mobile_origins(worksite_id);

-- ---------------------------------------------------------------------------
-- 3. Einladungen fuer weiteren verantwortlichen TB oder Helfer
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb.worksite_invitations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    invited_by uuid NOT NULL REFERENCES tb.users(id) ON DELETE CASCADE,
    invitation_type text NOT NULL CHECK (invitation_type IN ('LEAD','HELPER')),
    target_group_id uuid REFERENCES tb.worksite_groups(id) ON DELETE CASCADE,
    area_scope jsonb NOT NULL DEFAULT '{"all":true}'::jsonb
      CHECK (jsonb_typeof(area_scope)='object'),
    code_id text NOT NULL UNIQUE CHECK (btrim(code_id)<>''),
    code_hash text NOT NULL CHECK (btrim(code_hash)<>''),
    code_hint text,
    expires_at timestamptz NOT NULL,
    max_uses integer NOT NULL DEFAULT 1 CHECK (max_uses BETWEEN 1 AND 20),
    uses_count integer NOT NULL DEFAULT 0 CHECK (uses_count >= 0 AND uses_count <= max_uses),
    last_used_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (
        (invitation_type='LEAD' AND target_group_id IS NULL)
        OR
        (invitation_type='HELPER' AND target_group_id IS NOT NULL)
    )
);
CREATE INDEX IF NOT EXISTS worksite_invitations_worksite_idx
    ON tb.worksite_invitations(worksite_id,created_at DESC);
CREATE INDEX IF NOT EXISTS worksite_invitations_active_idx
    ON tb.worksite_invitations(expires_at)
    WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS worksite_invitations_inviter_idx
    ON tb.worksite_invitations(invited_by,created_at DESC);

CREATE OR REPLACE FUNCTION tb.validate_worksite_invitation_group()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    group_worksite uuid;
    inviter_company bigint;
    worksite_company bigint;
BEGIN
    SELECT company_id INTO worksite_company FROM tb.worksites WHERE id=NEW.worksite_id;
    SELECT company_id INTO inviter_company FROM tb.users WHERE id=NEW.invited_by;
    IF worksite_company IS NULL THEN RAISE EXCEPTION 'WORKSITE_COMPANY_REQUIRED'; END IF;
    IF inviter_company IS NULL OR inviter_company<>worksite_company THEN RAISE EXCEPTION 'COMPANY_MISMATCH'; END IF;

    IF NEW.invitation_type='HELPER' THEN
        SELECT worksite_id INTO group_worksite FROM tb.worksite_groups WHERE id=NEW.target_group_id;
        IF group_worksite IS NULL OR group_worksite<>NEW.worksite_id THEN
            RAISE EXCEPTION 'INVITATION_GROUP_WORKSITE_MISMATCH';
        END IF;
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_validate_worksite_invitation_group ON tb.worksite_invitations;
CREATE TRIGGER trg_validate_worksite_invitation_group
BEFORE INSERT OR UPDATE OF worksite_id,invited_by,invitation_type,target_group_id ON tb.worksite_invitations
FOR EACH ROW EXECUTE FUNCTION tb.validate_worksite_invitation_group();

CREATE TABLE IF NOT EXISTS tb.worksite_invitation_redemptions (
    invitation_id uuid NOT NULL REFERENCES tb.worksite_invitations(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE CASCADE,
    joined_group_id uuid REFERENCES tb.worksite_groups(id) ON DELETE SET NULL,
    redeemed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (invitation_id,user_id)
);
CREATE INDEX IF NOT EXISTS worksite_invitation_redemptions_user_idx
    ON tb.worksite_invitation_redemptions(user_id,redeemed_at DESC);

-- ---------------------------------------------------------------------------
-- 4. Zentrales Firmenprofil
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb.company_profiles (
    company_id bigint PRIMARY KEY REFERENCES tb.companies(id) ON DELETE CASCADE,
    address_line1 text,
    address_line2 text,
    postal_code text,
    city text,
    phone text,
    email text,
    website text,
    logo_storage_path text,
    logo_mime_type text,
    logo_sha256 text,
    logo_version bigint NOT NULL DEFAULT 0 CHECK (logo_version >= 0),
    profile_version bigint NOT NULL DEFAULT 1 CHECK (profile_version >= 1),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS company_profiles_updated_idx ON tb.company_profiles(updated_at);

INSERT INTO tb.schema_versions(version,name)
VALUES (10,'010_worksite_invitations_company_profile')
ON CONFLICT (version) DO NOTHING;

COMMIT;
