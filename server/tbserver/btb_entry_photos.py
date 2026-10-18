"""BTB-local entry/photo bindings. Callers hold the parent BTB write lock."""
from uuid import UUID
from fastapi import HTTPException

PHOTO_TYPES = {'ARBEIT':'FOTODOKUMENTATION','ABNAHME':'ABNAHMEN',
               'EINWEISUNG_UNTERWEISUNG':'EINWEISUNG_UNTERWEISUNG',
               'BESONDERHEIT':'BESONDERHEITEN','MANGEL_SCHADEN':'MAENGEL_SCHAEDEN'}


def entry_photo_context(cur, btb_id, entry_id):
    try:
        entry_id = UUID(str(entry_id))
    except (ValueError, TypeError):
        raise HTTPException(400, 'BTB_ENTRY_INVALID')
    cur.execute("SELECT id, section, payload FROM tb.btb_entries WHERE id=%s AND btb_id=%s AND deleted_at IS NULL FOR UPDATE", (entry_id, btb_id))
    entry = cur.fetchone()
    if not entry or entry['section'] not in PHOTO_TYPES:
        raise HTTPException(400, 'BTB_ENTRY_INVALID')
    payload = entry['payload'] or {}
    firm_id = payload.get('firmaId')
    name = None
    if firm_id:
        cur.execute("SELECT payload->>'name' AS name FROM tb.btb_entries WHERE btb_id=%s AND section='FIRMA' AND deleted_at IS NULL AND payload->>'firmaId'=%s AND coalesce(payload->>'isDeleted','false')<>'true'", (btb_id, str(firm_id)))
        firm = cur.fetchone()
        name = firm['name'] if firm else None
    return {'btbEntryId':str(entry_id), 'typ':PHOTO_TYPES[entry['section']],
            'firmaId':firm_id or None,'firma':name,'worksiteNodeKey':payload.get('worksiteNodeKey') or None,
            'kurztext':str(payload.get('text') or payload.get('beschreibung') or payload.get('kommentar') or '')[:500] or None}


def sync_entry_photos(cur, btb_id, entry_id, metadata, user_id):
    from psycopg.types.json import Jsonb
    cur.execute("""UPDATE tb.file_links SET link_metadata=link_metadata || %s,
        version=version+1, updated_by=%s, updated_at=now()
        WHERE entity_type='BTB' AND entity_id=%s AND link_metadata->>'btbEntryId'=%s
        RETURNING file_id, version""", (Jsonb(metadata), user_id, btb_id, str(entry_id)))
    return list(cur.fetchall())
