-- TB-Helfer Server 0.7 – READ-ONLY Preflight vor Migration 008/009
-- Dieses Script verändert KEINE Daten. Es prüft nur, ob der produktive Schema-7-Stand
-- die Voraussetzungen für die vorbereiteten Migrationen besitzt.
--
-- Ausführen erst auf dem NAS, wenn der produktive 0.6.0-Quellstand gesichert/übernommen ist.
-- Ein fehlgeschlagenes Ergebnis bedeutet: Migration NICHT starten.

BEGIN READ ONLY;

SELECT 'schema_version_7' AS check_name,
       EXISTS (
           SELECT 1
           FROM tb.schema_versions
           WHERE version = 7
       ) AS ok;

SELECT required_object,
       to_regclass(required_object) IS NOT NULL AS ok
FROM (VALUES
    ('tb.routes'),
    ('tb.stations'),
    ('tb.route_sections'),
    ('tb.users'),
    ('tb.devices'),
    ('tb.change_log'),
    ('tb.worksites'),
    ('tb.schema_versions')
) AS required(required_object)
ORDER BY required_object;

SELECT 'tb.set_updated_at()' AS required_function,
       to_regprocedure('tb.set_updated_at()') IS NOT NULL AS ok
UNION ALL
SELECT 'gen_random_uuid()',
       to_regprocedure('gen_random_uuid()') IS NOT NULL;

SELECT table_name,
       column_name,
       EXISTS (
           SELECT 1
           FROM information_schema.columns c
           WHERE c.table_schema = 'tb'
             AND c.table_name = required.table_name
             AND c.column_name = required.column_name
       ) AS ok
FROM (VALUES
    ('users','id'),
    ('users','company_id'),
    ('users','role'),
    ('users','status'),
    ('users','full_name'),
    ('devices','id'),
    ('worksites','id'),
    ('worksites','company_id'),
    ('routes','id'),
    ('stations','id'),
    ('route_sections','id'),
    ('change_log','entity_type'),
    ('change_log','entity_id'),
    ('change_log','sequence_no'),
    ('change_log','changed_at'),
    ('schema_versions','version'),
    ('schema_versions','name')
) AS required(table_name,column_name)
ORDER BY table_name,column_name;

SELECT 'postgis_geometry_type' AS check_name,
       EXISTS (
           SELECT 1
           FROM pg_type
           WHERE typname = 'geometry'
       ) AS ok;

COMMIT;
