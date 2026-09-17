-- TB-Server 1.0: rebuildable survey section source links and KM calibration points.
-- Immutable after application.

CREATE TABLE tb.track_data_sources (
    dataset_id uuid NOT NULL REFERENCES tb.track_data(id) ON DELETE CASCADE,
    original_id uuid NOT NULL REFERENCES tb.survey_originals(id) ON DELETE RESTRICT,
    source_role text NOT NULL CHECK (source_role IN ('GEOMETRY','KM','HEIGHT','CONTROL','OTHER')),
    source_order integer NOT NULL DEFAULT 0 CHECK (source_order >= 0),
    reverse_geometry boolean NOT NULL DEFAULT false,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(dataset_id, original_id, source_role)
);
CREATE INDEX track_data_sources_original_idx ON tb.track_data_sources(original_id, source_role);
CREATE INDEX track_data_sources_dataset_order_idx ON tb.track_data_sources(dataset_id, source_role, source_order);

CREATE TABLE tb.track_data_km_points (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id uuid NOT NULL REFERENCES tb.track_data(id) ON DELETE CASCADE,
    point_type text NOT NULL CHECK (point_type IN ('ORIGINAL_ANCHOR','MANUAL_ANCHOR','CONTROL_ANCHOR','KM_JUMP')),
    station_m double precision NOT NULL,
    km_meter double precision,
    km_after_jump_meter double precision,
    source_original_id uuid REFERENCES tb.survey_originals(id) ON DELETE RESTRICT,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (
        (point_type = 'KM_JUMP' AND km_meter IS NOT NULL AND km_after_jump_meter IS NOT NULL)
        OR
        (point_type <> 'KM_JUMP' AND km_meter IS NOT NULL AND km_after_jump_meter IS NULL)
    )
);
CREATE INDEX track_data_km_points_dataset_station_idx ON tb.track_data_km_points(dataset_id, station_m);
CREATE INDEX track_data_km_points_source_idx ON tb.track_data_km_points(source_original_id) WHERE source_original_id IS NOT NULL;
