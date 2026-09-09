-- TB-Helfer Server 0.7 – READ-ONLY Nachkontrolle nach Migration 008/009
-- Alle Pflichtobjekte müssen ok=true sein; alle violations müssen 0 sein.
BEGIN READ ONLY;

SELECT version,name FROM tb.schema_versions WHERE version IN (8,9) ORDER BY version;

SELECT required_object,to_regclass(required_object) IS NOT NULL AS ok
FROM (VALUES
 ('tb.route_section_versions'),('tb.sync_mutation_receipts'),('tb.sync_device_state'),('tb.device_sync_state'),
 ('tb.user_storage_preferences'),('tb.worksite_groups'),('tb.worksite_group_members'),
 ('tb.collab_btb_entries'),('tb.collab_defects'),('tb.collab_acceptances'),
 ('tb.collab_attachments'),('tb.collab_change_log'),('tb.collab_device_state'),('tb.collab_mutation_receipts')
) AS required(required_object)
ORDER BY required_object;

SELECT table_name,column_name,
       EXISTS (SELECT 1 FROM information_schema.columns c
               WHERE c.table_schema='tb' AND c.table_name=required.table_name AND c.column_name=required.column_name) AS ok
FROM (VALUES
 ('routes','current_version'),('stations','current_version'),
 ('sync_device_state','last_sequence_no'),('change_log','changed_by_device'),('object_versions','changed_by_device'),
 ('worksite_group_members','area_scope'),('collab_btb_entries','retain_permanent'),
 ('collab_defects','area_scope'),('collab_acceptances','area_scope'),
 ('collab_attachments','storage_path'),('collab_change_log','changed_by_device'),
 ('collab_mutation_receipts','http_status')
) AS required(table_name,column_name)
ORDER BY table_name,column_name;

SELECT required_trigger,
       EXISTS (SELECT 1 FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
               WHERE NOT t.tgisinternal AND n.nspname='tb' AND t.tgname=required.required_trigger) AS ok
FROM (VALUES
 ('trg_validate_worksite_group_membership'),('trg_validate_worksite_group_lead'),
 ('trg_validate_collab_btb_group_worksite'),('trg_prevent_reopening_collab_defect'),
 ('trg_prevent_reopening_collab_acceptance'),('trg_validate_collab_attachment_parent'),
 ('trg_validate_collab_change_group_worksite'),('trg_cleanup_memberships_after_user_company_change'),
 ('trg_cleanup_memberships_after_worksite_company_change'),('trg_collab_btb_entries_updated_at'),
 ('trg_collab_defects_updated_at'),('trg_collab_acceptances_updated_at')
) AS required(required_trigger)
ORDER BY required_trigger;

SELECT 'group_member_worksite_mismatch' AS check_name,COUNT(*) AS violations
FROM tb.worksite_group_members gm JOIN tb.worksite_groups g ON g.id=gm.group_id
WHERE gm.worksite_id IS DISTINCT FROM g.worksite_id
UNION ALL
SELECT 'group_member_company_or_access_mismatch',COUNT(*)
FROM tb.worksite_group_members gm JOIN tb.worksites w ON w.id=gm.worksite_id JOIN tb.users u ON u.id=gm.user_id
WHERE w.company_id IS NULL OR u.company_id IS NULL OR w.company_id IS DISTINCT FROM u.company_id
   OR u.role IS DISTINCT FROM 'USER' OR u.status IS DISTINCT FROM 'ACTIVE'
UNION ALL
SELECT 'group_lead_company_or_access_mismatch',COUNT(*)
FROM tb.worksite_groups g JOIN tb.worksites w ON w.id=g.worksite_id JOIN tb.users u ON u.id=g.lead_user_id
WHERE g.lead_user_id IS NOT NULL AND (w.company_id IS NULL OR u.company_id IS NULL OR w.company_id IS DISTINCT FROM u.company_id
   OR u.role IS DISTINCT FROM 'USER' OR u.status IS DISTINCT FROM 'ACTIVE')
UNION ALL
SELECT 'group_lead_membership_missing_or_wrong',COUNT(*)
FROM tb.worksite_groups g LEFT JOIN tb.worksite_group_members gm ON gm.group_id=g.id AND gm.user_id=g.lead_user_id AND gm.member_role='LEAD'
WHERE g.lead_user_id IS NOT NULL AND gm.user_id IS NULL
UNION ALL
SELECT 'btb_group_worksite_mismatch',COUNT(*)
FROM tb.collab_btb_entries b JOIN tb.worksite_groups g ON g.id=b.group_id WHERE b.worksite_id IS DISTINCT FROM g.worksite_id
UNION ALL
SELECT 'change_group_worksite_mismatch',COUNT(*)
FROM tb.collab_change_log l JOIN tb.worksite_groups g ON g.id=l.group_id
WHERE l.group_id IS NOT NULL AND l.worksite_id IS DISTINCT FROM g.worksite_id
UNION ALL
SELECT 'invalid_general_device_sequence',COUNT(*) FROM tb.sync_device_state WHERE last_sequence_no<0
UNION ALL
SELECT 'invalid_collab_device_sequence',COUNT(*) FROM tb.collab_device_state WHERE last_sequence_no<0
UNION ALL
SELECT 'invalid_route_version',COUNT(*) FROM tb.routes WHERE current_version<1
UNION ALL
SELECT 'invalid_station_version',COUNT(*) FROM tb.stations WHERE current_version<1
UNION ALL
SELECT 'invalid_btb_version',COUNT(*) FROM tb.collab_btb_entries WHERE current_version<1
UNION ALL
SELECT 'invalid_defect_version',COUNT(*) FROM tb.collab_defects WHERE current_version<1
UNION ALL
SELECT 'invalid_acceptance_version',COUNT(*) FROM tb.collab_acceptances WHERE current_version<1;

SELECT 'attachment_parent_missing_or_wrong_worksite' AS check_name,COUNT(*) AS violations
FROM tb.collab_attachments a
LEFT JOIN tb.collab_btb_entries b ON a.entity_type='BTB' AND b.id=a.entity_id
LEFT JOIN tb.collab_defects d ON a.entity_type='DEFECT' AND d.id=a.entity_id
LEFT JOIN tb.collab_acceptances ac ON a.entity_type='ACCEPTANCE' AND ac.id=a.entity_id
WHERE (a.entity_type='BTB' AND (b.id IS NULL OR b.worksite_id IS DISTINCT FROM a.worksite_id))
   OR (a.entity_type='DEFECT' AND (d.id IS NULL OR d.worksite_id IS DISTINCT FROM a.worksite_id))
   OR (a.entity_type='ACCEPTANCE' AND (ac.id IS NULL OR ac.worksite_id IS DISTINCT FROM a.worksite_id));

COMMIT;
