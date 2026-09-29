-- TB-Server 1.0: shared/versioned survey reference catalog.
-- Original survey files stay immutable; this catalog stores the composed fachliche Sicht.

CREATE TABLE tb.survey_catalog_assets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    original_id uuid NOT NULL UNIQUE REFERENCES tb.survey_originals(id) ON DELETE RESTRICT,
    scope text NOT NULL CHECK (scope IN ('GLOBAL','COMPANY')),
    company_id uuid REFERENCES tb.companies(id) ON DELETE RESTRICT,
    route_number text,
    station_code text,
    station_name text,
    track_key text,
    data_kind text NOT NULL,
    source_date date,
    parse_status text NOT NULL,
    data_status text NOT NULL CHECK (data_status IN ('ACTIVE','REVIEW','ERROR')),
    bbox_min_x double precision,
    bbox_min_y double precision,
    bbox_max_x double precision,
    bbox_max_y double precision,
    km_from_m double precision,
    km_to_m double precision,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    created_by_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((scope = 'GLOBAL' AND company_id IS NULL) OR (scope = 'COMPANY' AND company_id IS NOT NULL))
);
CREATE INDEX survey_catalog_assets_route_idx
    ON tb.survey_catalog_assets(scope, company_id, route_number, data_kind);
CREATE INDEX survey_catalog_assets_station_idx
    ON tb.survey_catalog_assets(scope, company_id, station_code, data_kind);
CREATE INDEX survey_catalog_assets_bbox_idx
    ON tb.survey_catalog_assets(bbox_min_x, bbox_max_x, bbox_min_y, bbox_max_y);

CREATE TABLE tb.survey_catalog_axes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scope text NOT NULL CHECK (scope IN ('GLOBAL','COMPANY')),
    company_id uuid REFERENCES tb.companies(id) ON DELETE RESTRICT,
    identity_key text NOT NULL,
    route_number text,
    station_code text,
    station_name text,
    track_key text,
    direction text,
    axis_type text NOT NULL,
    display_name text NOT NULL,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVIEW','ARCHIVED')),
    bbox_min_x double precision,
    bbox_min_y double precision,
    bbox_max_x double precision,
    bbox_max_y double precision,
    km_from_m double precision,
    km_to_m double precision,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    created_by_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_by_company_id uuid REFERENCES tb.companies(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((scope = 'GLOBAL' AND company_id IS NULL) OR (scope = 'COMPANY' AND company_id IS NOT NULL))
);
CREATE UNIQUE INDEX survey_catalog_axes_identity_uq
    ON tb.survey_catalog_axes(identity_key);
CREATE INDEX survey_catalog_axes_route_idx
    ON tb.survey_catalog_axes(scope, company_id, route_number, track_key, direction);
CREATE INDEX survey_catalog_axes_station_idx
    ON tb.survey_catalog_axes(scope, company_id, station_code, track_key);
CREATE INDEX survey_catalog_axes_bbox_idx
    ON tb.survey_catalog_axes(bbox_min_x, bbox_max_x, bbox_min_y, bbox_max_y);

CREATE TABLE tb.survey_catalog_segments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    axis_id uuid NOT NULL REFERENCES tb.survey_catalog_axes(id) ON DELETE RESTRICT,
    asset_id uuid REFERENCES tb.survey_catalog_assets(id) ON DELETE RESTRICT,
    source_kind text NOT NULL CHECK (source_kind IN ('ORIGINAL_TRA','MANUAL_TRA_SECTION','MANUAL_GPS')),
    source_original_id uuid REFERENCES tb.survey_originals(id) ON DELETE RESTRICT,
    source_signature text,
    source_date date,
    source_rank integer NOT NULL DEFAULT 0,
    segment_status text NOT NULL DEFAULT 'ACTIVE'
        CHECK (segment_status IN ('ACTIVE','REVIEW','SUPERSEDED','INACTIVE')),
    merge_status text NOT NULL
        CHECK (merge_status IN ('PRIMARY','OVERLAP_CONFIRMED','ENDPOINT_CONFIRMED','INDEPENDENT','MANUAL','REVIEW_REQUIRED')),
    station_from_m double precision,
    station_to_m double precision,
    km_from_m double precision,
    km_to_m double precision,
    bbox_min_x double precision,
    bbox_min_y double precision,
    bbox_max_x double precision,
    bbox_max_y double precision,
    geometry jsonb,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    created_by_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_by_company_id uuid REFERENCES tb.companies(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX survey_catalog_segments_original_tra_uq
    ON tb.survey_catalog_segments(source_original_id)
    WHERE source_kind = 'ORIGINAL_TRA' AND source_original_id IS NOT NULL;
CREATE INDEX survey_catalog_segments_axis_idx
    ON tb.survey_catalog_segments(axis_id, segment_status, source_date DESC NULLS LAST);
CREATE INDEX survey_catalog_segments_bbox_idx
    ON tb.survey_catalog_segments(bbox_min_x, bbox_max_x, bbox_min_y, bbox_max_y);
CREATE INDEX survey_catalog_segments_signature_idx
    ON tb.survey_catalog_segments(source_signature);

CREATE TABLE tb.survey_catalog_conflicts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    axis_id uuid NOT NULL REFERENCES tb.survey_catalog_axes(id) ON DELETE RESTRICT,
    candidate_segment_id uuid NOT NULL REFERENCES tb.survey_catalog_segments(id) ON DELETE RESTRICT,
    existing_segment_id uuid REFERENCES tb.survey_catalog_segments(id) ON DELETE RESTRICT,
    conflict_type text NOT NULL,
    status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED')),
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    resolution text,
    resolution_note text,
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    notified_at timestamptz,
    resolved_by_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    resolved_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX survey_catalog_conflicts_open_uq
    ON tb.survey_catalog_conflicts(
        candidate_segment_id,
        coalesce(existing_segment_id, '00000000-0000-0000-0000-000000000000'::uuid),
        conflict_type
    )
    WHERE status = 'OPEN';
CREATE INDEX survey_catalog_conflicts_status_idx
    ON tb.survey_catalog_conflicts(status, created_at DESC);

CREATE TABLE tb.survey_catalog_history (
    change_id bigserial PRIMARY KEY,
    entity_type text NOT NULL CHECK (entity_type IN ('ASSET','AXIS','SEGMENT','CONFLICT')),
    entity_id uuid NOT NULL,
    entity_version integer NOT NULL CHECK (entity_version >= 1),
    operation text NOT NULL CHECK (operation IN ('CREATE','UPDATE','RESOLVE','STATUS')),
    before_data jsonb,
    after_data jsonb NOT NULL,
    changed_by_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    changed_by_company_id uuid REFERENCES tb.companies(id) ON DELETE SET NULL,
    changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX survey_catalog_history_cursor_idx
    ON tb.survey_catalog_history(change_id);
CREATE INDEX survey_catalog_history_entity_idx
    ON tb.survey_catalog_history(entity_type, entity_id, change_id DESC);

CREATE TRIGGER survey_catalog_assets_touch
BEFORE UPDATE ON tb.survey_catalog_assets
FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();

CREATE TRIGGER survey_catalog_axes_touch
BEFORE UPDATE ON tb.survey_catalog_axes
FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();

CREATE TRIGGER survey_catalog_segments_touch
BEFORE UPDATE ON tb.survey_catalog_segments
FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();

CREATE TRIGGER survey_catalog_conflicts_touch
BEFORE UPDATE ON tb.survey_catalog_conflicts
FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
