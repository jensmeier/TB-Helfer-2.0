-- TB-Helfer Server 0.7 – READ-ONLY Preflight vor Migration 008/009
-- Kein Schreibzugriff. Sobald irgendein Pflichtobjekt/Spaltencheck false ist: NICHT migrieren.
BEGIN READ ONLY;

SELECT 'schema_version_7' AS check_name,
       EXISTS (SELECT 1 FROM tb.schema_versions WHERE version=7) AS ok;

SELECT required_object,to_regclass(required_object) IS NOT NULL AS ok
FROM (VALUES
 ('tb.routes'),('tb.stations'),('tb.route_sections'),('tb.infrastructure_objects'),
 ('tb.object_versions'),('tb.users'),('tb.devices'),('tb.device_sync_state'),
 ('tb.change_log'),('tb.worksites'),('tb.companies'),('tb.schema_versions')
) AS required(required_object)
ORDER BY required_object;

SELECT 'tb.set_updated_at()' AS required_function,to_regprocedure('tb.set_updated_at()') IS NOT NULL AS ok
UNION ALL SELECT 'gen_random_uuid()',to_regprocedure('gen_random_uuid()') IS NOT NULL;

SELECT table_name,column_name,
       EXISTS (SELECT 1 FROM information_schema.columns c
               WHERE c.table_schema='tb' AND c.table_name=required.table_name AND c.column_name=required.column_name) AS ok
FROM (VALUES
 ('users','id'),('users','company_id'),('users','role'),('users','status'),('users','full_name'),
 ('devices','id'),('device_sync_state','device_id'),('device_sync_state','last_sync_at'),
 ('worksites','id'),('worksites','company_id'),('worksites','route_id'),('worksites','station_id'),
 ('routes','id'),('stations','id'),
 ('route_sections','id'),('route_sections','route_id'),('route_sections','current_version'),('route_sections','geom'),
 ('infrastructure_objects','id'),('infrastructure_objects','current_version'),('infrastructure_objects','geom'),
 ('object_versions','object_id'),('object_versions','version_no'),('object_versions','geom'),
 ('change_log','entity_type'),('change_log','entity_id'),('change_log','operation'),('change_log','entity_version'),
 ('change_log','changed_by'),('change_log','payload'),('change_log','sequence_no'),('change_log','changed_at'),
 ('schema_versions','version'),('schema_versions','name')
) AS required(table_name,column_name)
ORDER BY table_name,column_name;

SELECT 'postgis_geometry_type' AS check_name,
       EXISTS (SELECT 1 FROM pg_type WHERE typname='geometry') AS ok;

COMMIT;
