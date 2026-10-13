-- Globaler firmenübergreifender Katalog.
-- IMMUTABLE after first application to a persistent TB-Server 1.0 database.

CREATE TABLE tb.catalog_routes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    route_number text NOT NULL,
    name text,
    description text,
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    created_by_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_by_company_id uuid REFERENCES tb.companies(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX catalog_routes_number_uq ON tb.catalog_routes(lower(btrim(route_number)));

CREATE TABLE tb.catalog_stations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    station_code text,
    name text NOT NULL,
    short_name text,
    description text,
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    created_by_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_by_company_id uuid REFERENCES tb.companies(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX catalog_stations_code_uq
ON tb.catalog_stations(lower(btrim(station_code)))
WHERE station_code IS NOT NULL AND btrim(station_code) <> '';
CREATE INDEX catalog_stations_name_idx ON tb.catalog_stations(lower(name));

CREATE TABLE tb.catalog_sections (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id uuid NOT NULL REFERENCES tb.catalog_routes(id) ON DELETE RESTRICT,
    designation text,
    km_from_m double precision,
    km_to_m double precision,
    data_status text,
    geometry jsonb,
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    created_by_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_by_company_id uuid REFERENCES tb.companies(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX catalog_sections_route_idx ON tb.catalog_sections(route_id, km_from_m, km_to_m);

CREATE TABLE tb.catalog_objects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id uuid REFERENCES tb.catalog_routes(id) ON DELETE RESTRICT,
    station_id uuid REFERENCES tb.catalog_stations(id) ON DELETE RESTRICT,
    object_type text NOT NULL,
    designation text,
    km_m double precision,
    data_status text,
    geometry jsonb,
    properties jsonb NOT NULL DEFAULT '{}'::jsonb,
    current_version integer NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    created_by_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    created_by_company_id uuid REFERENCES tb.companies(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (route_id IS NOT NULL OR station_id IS NOT NULL)
);
CREATE INDEX catalog_objects_route_idx ON tb.catalog_objects(route_id, object_type, designation);
CREATE INDEX catalog_objects_station_idx ON tb.catalog_objects(station_id, object_type, designation);

CREATE TABLE tb.catalog_history (
    change_id bigserial PRIMARY KEY,
    entity_type text NOT NULL CHECK (entity_type IN ('ROUTE','STATION','SECTION','OBJECT')),
    entity_id uuid NOT NULL,
    entity_version integer NOT NULL CHECK (entity_version >= 1),
    operation text NOT NULL CHECK (operation IN ('CREATE','UPDATE')),
    before_data jsonb,
    after_data jsonb NOT NULL,
    changed_by_user_id uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    changed_by_company_id uuid REFERENCES tb.companies(id) ON DELETE SET NULL,
    changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX catalog_history_entity_idx ON tb.catalog_history(entity_type, entity_id, change_id DESC);
CREATE INDEX catalog_history_cursor_idx ON tb.catalog_history(change_id);

CREATE TRIGGER catalog_routes_touch
BEFORE UPDATE ON tb.catalog_routes
FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();

CREATE TRIGGER catalog_stations_touch
BEFORE UPDATE ON tb.catalog_stations
FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();

CREATE TRIGGER catalog_sections_touch
BEFORE UPDATE ON tb.catalog_sections
FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();

CREATE TRIGGER catalog_objects_touch
BEFORE UPDATE ON tb.catalog_objects
FOR EACH ROW EXECUTE FUNCTION tb.touch_updated_at();
