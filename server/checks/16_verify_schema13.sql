DO $$
DECLARE
    schema_version integer;
    node_count integer;
BEGIN
    SELECT max(version) INTO schema_version FROM tb.schema_versions;
    IF schema_version < 13 THEN
        RAISE EXCEPTION 'SCHEMA_13_NOT_APPLIED: %', schema_version;
    END IF;

    IF to_regclass('tb.collab_worksite_nodes') IS NULL THEN
        RAISE EXCEPTION 'COLLAB_WORKSITE_NODES_MISSING';
    END IF;

    SELECT count(*) INTO node_count
    FROM information_schema.columns
    WHERE table_schema='tb' AND table_name='collab_worksite_nodes'
      AND column_name IN (
          'id','worksite_id','client_node_key','area_scope','status','payload',
          'current_version','created_by','updated_by','retain_permanent',
          'expires_at','created_at','updated_at','deleted_at'
      );
    IF node_count <> 14 THEN
        RAISE EXCEPTION 'COLLAB_WORKSITE_NODE_COLUMNS_INCOMPLETE: %', node_count;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname='tb' AND tablename='collab_worksite_nodes'
          AND indexdef ILIKE '%UNIQUE%worksite_id%client_node_key%'
    ) THEN
        RAISE EXCEPTION 'COLLAB_WORKSITE_NODE_STABLE_KEY_INDEX_MISSING';
    END IF;
END $$;

SELECT version,name,applied_at
FROM tb.schema_versions
WHERE version=13;
