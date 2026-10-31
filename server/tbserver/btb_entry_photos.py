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


def matching_entry_context(contexts, metadata):
    """Only a unique same-section/company/node caption may supply a missing binding."""
    def norm(value):
        return ' '.join(str(value or '').split()).casefold()
    text = norm(metadata.get('kurztext') or metadata.get('notiz'))
    if not text or 'btbEntryId' in metadata:
        return None
    matches = [entry for entry in contexts
               if norm(entry.get('kurztext')) == text
               and entry.get('typ') == metadata.get('typ', 'FOTODOKUMENTATION')
               and norm(entry.get('firma')) == norm(metadata.get('firma'))
               and (not metadata.get('firmaId') or entry.get('firmaId') == metadata.get('firmaId'))
               and str(entry.get('worksiteNodeKey') or '') == str(metadata.get('worksiteNodeKey') or '')]
    return matches[0] if len(matches) == 1 else None


def matching_btb_entry_context(cur, btb_id, metadata):
    cur.execute("SELECT id FROM tb.btb_entries WHERE btb_id=%s AND deleted_at IS NULL AND section=ANY(%s) ORDER BY id", (btb_id, list(PHOTO_TYPES)))
    ids = [entry['id'] for entry in cur.fetchall()]
    return matching_entry_context([entry_photo_context(cur, btb_id, entry_id) for entry_id in ids], metadata)


def bind_matching_entry_photos(cur, btb_id, user_id):
    """Parent BTB is locked by caller; never override an explicit link or unlink."""
    from psycopg.types.json import Jsonb
    from .btb_photo_meta import merge_btb_photo_metadata
    cur.execute("SELECT l.file_id,l.link_metadata,f.photo_metadata FROM tb.file_links l JOIN tb.files f ON f.id=l.file_id WHERE l.entity_type='BTB' AND l.entity_id=%s AND f.deleted_at IS NULL", (btb_id,))
    photos = list(cur.fetchall())
    changed = []
    for photo in photos:
        link = photo.get('link_metadata') or {}
        if 'btbEntryId' in link:
            continue
        metadata = merge_btb_photo_metadata(photo.get('photo_metadata') or {}, link)
        target = matching_btb_entry_context(cur, btb_id, metadata)
        if target:
            cur.execute("UPDATE tb.file_links SET link_metadata=link_metadata || %s,version=version+1,updated_by=%s,updated_at=now() WHERE file_id=%s AND entity_type='BTB' AND entity_id=%s RETURNING file_id,version", (Jsonb(target), user_id, photo['file_id'], btb_id))
            changed.append(dict(cur.fetchone()))
    return changed

