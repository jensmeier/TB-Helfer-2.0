-- TB-Server 1.0 fresh baseline.
-- IMMUTABLE after first application to a persistent TB-Server 1.0 database.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE SCHEMA IF NOT EXISTS tb;

CREATE OR REPLACE FUNCTION tb.touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END
$$;

CREATE TABLE tb.companies (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    street text,
    postal_code text,
    city text,
    country_code text NOT NULL DEFAULT 'DE',
    phone text,
    email text,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
    settings jsonb NOT NULL DEFAULT '{}'::jsonb,
    pdf_layout jsonb NOT NULL DEFAULT '{}'::jsonb,
    storage_warning_percent integer CHECK (storage_warning_percent IS NULL OR storage_warning_percent BETWEEN 1 AND 99),
    logo_file_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE UNIQUE INDEX companies_name_active_uq ON tb.companies (lower(btrim(name))) WHERE deleted_at IS NULL;

CREATE TABLE tb.users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid REFERENCES tb.companies(id) ON DELETE RESTRICT,
    first_name text,
    last_name text,
    display_name text NOT NULL,
    login_name text NOT NULL,
    personal_email text,
    personal_phone text,
    company_role text NOT NULL DEFAULT 'USER' CHECK (company_role IN ('ADMIN','CHEF','USER')),
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
    access_valid_until timestamptz,
    profile_version integer NOT NULL DEFAULT 1 CHECK (profile_version >= 1),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CHECK (company_role = 'ADMIN' OR company_id IS NOT NULL)
);
CREATE UNIQUE INDEX users_login_name_uq ON tb.users (lower(login_name));
CREATE INDEX users_company_idx ON tb.users(company_id) WHERE deleted_at IS NULL;

CREATE TABLE tb.portal_credentials (
    user_id uuid PRIMARY KEY REFERENCES tb.users(id) ON DELETE CASCADE,
    password_hash text,
    activation_token_hash text,
    activation_expires_at timestamptz,
    must_change_password boolean NOT NULL DEFAULT true,
    failed_login_count integer NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
    locked_until timestamptz,
    activated_at timestamptz,
    last_login_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE tb.portal_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE CASCADE,
    session_token_hash text NOT NULL UNIQUE,
    csrf_secret_hash text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    ip_hint text,
    user_agent text
);
CREATE INDEX portal_sessions_user_idx ON tb.portal_sessions(user_id, expires_at);

CREATE TABLE tb.devices (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE RESTRICT,
    device_uid text NOT NULL,
    platform text NOT NULL CHECK (platform IN ('ANDROID','IOS','WINDOWS','OTHER')),
    device_name text,
    os_version text,
    app_version text,
    app_build integer,
    token_version integer NOT NULL DEFAULT 1 CHECK (token_version >= 1),
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVOKED','BLOCKED')),
    bound_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz,
    last_sync_at timestamptz,
    last_sync_error text,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX devices_uid_active_uq ON tb.devices(device_uid) WHERE revoked_at IS NULL;
CREATE INDEX devices_user_idx ON tb.devices(user_id);

CREATE TABLE tb.device_activation_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    token_hint text,
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz
);
CREATE INDEX device_activation_tokens_user_idx ON tb.device_activation_tokens(user_id, expires_at);

CREATE TABLE tb.permission_profiles (
    profile_key text PRIMARY KEY,
    label text NOT NULL,
    permissions jsonb NOT NULL,
    built_in boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO tb.permission_profiles(profile_key, label, permissions) VALUES
('RESPONSIBLE_TB', 'Verantwortlicher TB', '{"btb.view":true,"btb.edit":true,"btb.create":true,"btb.finalize":true,"btb.delete":true,"files.add":true,"acceptance.create":true,"acceptance.edit":true,"acceptance.finalize":true,"defect.create":true,"defect.edit":true,"defect.close":true,"worksite.structure.edit":true,"track.edit":true,"members.manage":true,"export.create":true}'::jsonb),
('HELPER_STANDARD', 'Helfer Standard', '{"btb.view":true,"btb.edit":true,"btb.create":false,"btb.finalize":false,"btb.delete":false,"files.add":true,"acceptance.create":true,"acceptance.edit":true,"acceptance.finalize":false,"defect.create":true,"defect.edit":true,"defect.close":false,"worksite.structure.edit":false,"track.edit":false,"members.manage":false,"export.create":true}'::jsonb),
('HELPER_EXTENDED', 'Helfer erweitert', '{"btb.view":true,"btb.edit":true,"btb.create":true,"btb.finalize":false,"btb.delete":false,"files.add":true,"acceptance.create":true,"acceptance.edit":true,"acceptance.finalize":true,"defect.create":true,"defect.edit":true,"defect.close":true,"worksite.structure.edit":false,"track.edit":false,"members.manage":false,"export.create":true}'::jsonb),
('READ_ONLY', 'Nur Lesen', '{"btb.view":true,"btb.edit":false,"btb.create":false,"btb.finalize":false,"btb.delete":false,"files.add":false,"acceptance.create":false,"acceptance.edit":false,"acceptance.finalize":false,"defect.create":false,"defect.edit":false,"defect.close":false,"worksite.structure.edit":false,"track.edit":false,"members.manage":false,"export.create":true}'::jsonb);

CREATE TABLE tb.worksites (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES tb.companies(id) ON DELETE RESTRICT,
    name text NOT NULL,
    designation text,
    route_number text,
    station_code text,
    station_name text,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED','TRASHED')),
    version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    archived_at timestamptz,
    deleted_at timestamptz,
    purge_after timestamptz
);
CREATE INDEX worksites_company_idx ON tb.worksites(company_id, status);
CREATE INDEX worksites_updated_idx ON tb.worksites(updated_at);

CREATE TABLE tb.worksite_nodes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    parent_id uuid REFERENCES tb.worksite_nodes(id) ON DELETE CASCADE,
    node_kind text NOT NULL,
    name text NOT NULL,
    route_number text,
    track_key text,
    external_key text,
    sort_order integer NOT NULL DEFAULT 0,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE INDEX worksite_nodes_tree_idx ON tb.worksite_nodes(worksite_id, parent_id, sort_order);
CREATE UNIQUE INDEX worksite_nodes_external_key_uq ON tb.worksite_nodes(worksite_id, external_key) WHERE external_key IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE tb.worksite_memberships (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE RESTRICT,
    worksite_role text NOT NULL CHECK (worksite_role IN ('RESPONSIBLE_TB','TB','HELPER','READ_ONLY')),
    permission_profile text NOT NULL DEFAULT 'HELPER_STANDARD' REFERENCES tb.permission_profiles(profile_key),
    permissions_override jsonb NOT NULL DEFAULT '{}'::jsonb,
    permissions_version integer NOT NULL DEFAULT 1 CHECK (permissions_version >= 1),
    area_scope jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','LEFT','REMOVED')),
    invited_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    joined_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    removed_at timestamptz,
    UNIQUE(worksite_id, user_id)
);
CREATE INDEX worksite_memberships_user_idx ON tb.worksite_memberships(user_id, status);

CREATE TABLE tb.worksite_invitations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    invited_user_id uuid REFERENCES tb.users(id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    worksite_role text NOT NULL CHECK (worksite_role IN ('RESPONSIBLE_TB','TB','HELPER','READ_ONLY')),
    permission_profile text NOT NULL REFERENCES tb.permission_profiles(profile_key),
    permissions_override jsonb NOT NULL DEFAULT '{}'::jsonb,
    area_scope jsonb NOT NULL DEFAULT '{}'::jsonb,
    invited_by uuid NOT NULL REFERENCES tb.users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    accepted_at timestamptz,
    revoked_at timestamptz
);
CREATE INDEX worksite_invitations_worksite_idx ON tb.worksite_invitations(worksite_id, expires_at);

CREATE TABLE tb.teams (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    lead_user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE RESTRICT,
    name text,
    active_btb_id uuid,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(worksite_id, lead_user_id)
);

CREATE TABLE tb.team_members (
    team_id uuid NOT NULL REFERENCES tb.teams(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE RESTRICT,
    role text NOT NULL DEFAULT 'HELPER' CHECK (role IN ('LEAD','TB','HELPER')),
    created_at timestamptz NOT NULL DEFAULT now(),
    removed_at timestamptz,
    PRIMARY KEY(team_id, user_id)
);
CREATE INDEX team_members_user_idx ON tb.team_members(user_id) WHERE removed_at IS NULL;

CREATE TABLE tb.btbs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    team_id uuid NOT NULL REFERENCES tb.teams(id) ON DELETE CASCADE,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','FINALIZED','REWORK','DELETED')),
    work_date date,
    started_at timestamptz,
    ended_at timestamptz,
    header jsonb NOT NULL DEFAULT '{}'::jsonb,
    version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    first_finalized_at timestamptz,
    finalized_at timestamptz,
    deleted_at timestamptz
);
CREATE INDEX btbs_team_idx ON tb.btbs(team_id, status, work_date);
ALTER TABLE tb.teams ADD CONSTRAINT teams_active_btb_fk FOREIGN KEY (active_btb_id) REFERENCES tb.btbs(id) ON DELETE SET NULL;

CREATE TABLE tb.btb_entries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    btb_id uuid NOT NULL REFERENCES tb.btbs(id) ON DELETE CASCADE,
    section text NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    updated_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE INDEX btb_entries_btb_idx ON tb.btb_entries(btb_id, section, sort_order);

CREATE TABLE tb.btb_original_snapshots (
    btb_id uuid PRIMARY KEY REFERENCES tb.btbs(id) ON DELETE CASCADE,
    snapshot jsonb NOT NULL,
    snapshot_sha256 text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL
);

CREATE TABLE tb.files (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid REFERENCES tb.companies(id) ON DELETE RESTRICT,
    worksite_id uuid REFERENCES tb.worksites(id) ON DELETE CASCADE,
    storage_key text NOT NULL UNIQUE,
    original_name text,
    mime_type text NOT NULL,
    byte_size bigint NOT NULL CHECK (byte_size >= 0),
    sha256 text NOT NULL,
    source text NOT NULL DEFAULT 'APP' CHECK (source IN ('APP','PORTAL','SERVER','IMPORT')),
    captured_at timestamptz,
    gps_lat double precision,
    gps_lon double precision,
    gps_accuracy_m double precision,
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CHECK (company_id IS NOT NULL OR source = 'SERVER')
);
CREATE INDEX files_worksite_idx ON tb.files(worksite_id, created_at);
ALTER TABLE tb.companies ADD CONSTRAINT companies_logo_file_fk FOREIGN KEY (logo_file_id) REFERENCES tb.files(id) ON DELETE SET NULL;

CREATE TABLE tb.file_links (
    file_id uuid NOT NULL REFERENCES tb.files(id) ON DELETE CASCADE,
    entity_type text NOT NULL,
    entity_id uuid NOT NULL,
    purpose text NOT NULL DEFAULT 'ATTACHMENT',
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(file_id, entity_type, entity_id, purpose)
);
CREATE INDEX file_links_entity_idx ON tb.file_links(entity_type, entity_id);

CREATE TABLE tb.upload_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid REFERENCES tb.companies(id) ON DELETE CASCADE,
    worksite_id uuid REFERENCES tb.worksites(id) ON DELETE CASCADE,
    user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    target_kind text NOT NULL,
    original_name text,
    mime_type text,
    expected_bytes bigint CHECK (expected_bytes IS NULL OR expected_bytes >= 0),
    expected_sha256 text,
    chunk_size integer NOT NULL DEFAULT 8388608 CHECK (chunk_size BETWEEN 1048576 AND 16777216),
    temp_storage_key text NOT NULL UNIQUE,
    received_bytes bigint NOT NULL DEFAULT 0 CHECK (received_bytes >= 0),
    status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','COMPLETED','ABORTED','EXPIRED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL
);

CREATE TABLE tb.defects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    worksite_node_id uuid REFERENCES tb.worksite_nodes(id) ON DELETE SET NULL,
    status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','CLOSED','DELETED')),
    title text,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    closed_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    closed_at timestamptz,
    deleted_at timestamptz
);
CREATE INDEX defects_worksite_idx ON tb.defects(worksite_id, status);

CREATE TABLE tb.acceptances (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    worksite_node_id uuid REFERENCES tb.worksite_nodes(id) ON DELETE SET NULL,
    status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','FINALIZED','DELETED')),
    title text,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    finalized_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    finalized_at timestamptz,
    deleted_at timestamptz
);
CREATE INDEX acceptances_worksite_idx ON tb.acceptances(worksite_id, status);

CREATE TABLE tb.survey_originals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid REFERENCES tb.companies(id) ON DELETE RESTRICT,
    route_number text,
    track_key text,
    data_kind text NOT NULL,
    storage_key text NOT NULL UNIQUE,
    original_name text NOT NULL,
    sha256 text NOT NULL,
    byte_size bigint NOT NULL CHECK (byte_size >= 0),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    imported_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    imported_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX survey_originals_route_idx ON tb.survey_originals(route_number, track_key, data_kind);

CREATE TABLE tb.track_data (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    worksite_id uuid NOT NULL REFERENCES tb.worksites(id) ON DELETE CASCADE,
    worksite_node_id uuid REFERENCES tb.worksite_nodes(id) ON DELETE SET NULL,
    dataset_key text NOT NULL,
    dataset_kind text NOT NULL,
    route_number text,
    track_key text,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
    source_original_id uuid REFERENCES tb.survey_originals(id) ON DELETE SET NULL,
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    UNIQUE(worksite_id, dataset_key)
);
CREATE INDEX track_data_worksite_idx ON tb.track_data(worksite_id, dataset_kind);

CREATE TABLE tb.mutation_receipts (
    id bigserial PRIMARY KEY,
    device_id uuid REFERENCES tb.devices(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE RESTRICT,
    client_change_id text NOT NULL,
    entity_type text NOT NULL,
    entity_id uuid,
    result_version integer,
    response_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(user_id, client_change_id)
);

CREATE TABLE tb.change_feed (
    change_id bigserial PRIMARY KEY,
    company_id uuid NOT NULL REFERENCES tb.companies(id) ON DELETE CASCADE,
    worksite_id uuid REFERENCES tb.worksites(id) ON DELETE CASCADE,
    entity_type text NOT NULL,
    entity_id uuid NOT NULL,
    operation text NOT NULL,
    entity_version integer,
    actor_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX change_feed_company_cursor_idx ON tb.change_feed(company_id, change_id);
CREATE INDEX change_feed_worksite_cursor_idx ON tb.change_feed(worksite_id, change_id);

CREATE TABLE tb.sync_cursors (
    device_id uuid NOT NULL REFERENCES tb.devices(id) ON DELETE CASCADE,
    company_id uuid NOT NULL REFERENCES tb.companies(id) ON DELETE CASCADE,
    scope_key text NOT NULL,
    worksite_id uuid REFERENCES tb.worksites(id) ON DELETE CASCADE,
    last_change_id bigint NOT NULL DEFAULT 0 CHECK (last_change_id >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(device_id, company_id, scope_key)
);

CREATE TABLE tb.deletion_tombstones (
    id bigserial PRIMARY KEY,
    company_id uuid NOT NULL REFERENCES tb.companies(id) ON DELETE CASCADE,
    worksite_id uuid REFERENCES tb.worksites(id) ON DELETE SET NULL,
    entity_type text NOT NULL,
    entity_id uuid NOT NULL,
    deleted_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    deleted_at timestamptz NOT NULL DEFAULT now(),
    purge_after timestamptz,
    UNIQUE(entity_type, entity_id)
);

CREATE TABLE tb.audit_log (
    id bigserial PRIMARY KEY,
    company_id uuid REFERENCES tb.companies(id) ON DELETE SET NULL,
    worksite_id uuid REFERENCES tb.worksites(id) ON DELETE SET NULL,
    actor_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    actor_device_id uuid REFERENCES tb.devices(id) ON DELETE SET NULL,
    action text NOT NULL,
    entity_type text,
    entity_id uuid,
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_log_company_time_idx ON tb.audit_log(company_id, created_at DESC);

CREATE TABLE tb.releases (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    platform text NOT NULL CHECK (platform IN ('ANDROID','IOS','WINDOWS')),
    version_name text NOT NULL,
    build_number integer,
    minimum_supported_build integer,
    mandatory boolean NOT NULL DEFAULT false,
    storage_key text,
    byte_size bigint CHECK (byte_size IS NULL OR byte_size >= 0),
    sha256 text,
    download_url text,
    release_notes text,
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','RETIRED')),
    published_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(platform, version_name)
);

CREATE TABLE tb.background_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type text NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','DONE','FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    run_after timestamptz NOT NULL DEFAULT now(),
    last_error text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz
);
CREATE INDEX background_jobs_queue_idx ON tb.background_jobs(status, run_after);

CREATE TABLE tb.backup_runs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    kind text NOT NULL CHECK (kind IN ('DATABASE','FILES','FULL','RESTORE_TEST')),
    status text NOT NULL CHECK (status IN ('RUNNING','OK','FAILED')),
    storage_key text,
    byte_size bigint,
    sha256 text,
    started_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    detail text
);

CREATE TABLE tb.system_metrics_hourly (
    bucket_start timestamptz PRIMARY KEY,
    request_count bigint NOT NULL DEFAULT 0,
    error_count bigint NOT NULL DEFAULT 0,
    bytes_in bigint NOT NULL DEFAULT 0,
    bytes_out bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE tb.server_settings (
    setting_key text PRIMARY KEY,
    value jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO tb.server_settings(setting_key, value) VALUES
    ('schema_baseline', '{"name":"TB-Server 1.0","version":1}'::jsonb),
    ('trash_retention_days', '30'::jsonb),
    ('capabilities_revision', '1'::jsonb)
ON CONFLICT (setting_key) DO NOTHING;

CREATE TRIGGER companies_touch BEFORE UPDATE ON tb.companies FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER users_touch BEFORE UPDATE ON tb.users FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER devices_touch BEFORE UPDATE ON tb.devices FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER worksites_touch BEFORE UPDATE ON tb.worksites FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER worksite_nodes_touch BEFORE UPDATE ON tb.worksite_nodes FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER memberships_touch BEFORE UPDATE ON tb.worksite_memberships FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER teams_touch BEFORE UPDATE ON tb.teams FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER btbs_touch BEFORE UPDATE ON tb.btbs FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER btb_entries_touch BEFORE UPDATE ON tb.btb_entries FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER uploads_touch BEFORE UPDATE ON tb.upload_sessions FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER defects_touch BEFORE UPDATE ON tb.defects FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER acceptances_touch BEFORE UPDATE ON tb.acceptances FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER track_data_touch BEFORE UPDATE ON tb.track_data FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
CREATE TRIGGER jobs_touch BEFORE UPDATE ON tb.background_jobs FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
