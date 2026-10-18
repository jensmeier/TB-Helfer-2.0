"""Report-time inheritance; pure functions shared by header mutations and tests."""
from datetime import datetime
from zoneinfo import ZoneInfo


def clock(value):
    if not value:
        return None
    if isinstance(value, str):
        value = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if value.tzinfo is not None:
        value = value.astimezone(ZoneInfo("Europe/Berlin"))
    return value.strftime("%H:%M")


def inherited_firm_times(payload, before, after):
    result = dict(payload)
    for prefix in ("personal", "maschinen"):
        binding = payload.get(prefix + "Zeitbindung")
        if binding == "MANUAL":
            continue
        old = (clock(before.get("started_at")), clock(before.get("ended_at")))
        actual = (payload.get(prefix + "Von") or None, payload.get(prefix + "Bis") or None)
        # Legacy records with individual times must not be silently changed.
        if binding != "REPORT" and any(v and v != old[i] for i, v in enumerate(actual)):
            continue
        result[prefix + "Von"] = clock(after.get("started_at"))
        result[prefix + "Bis"] = clock(after.get("ended_at"))
        result[prefix + "Zeitbindung"] = "REPORT"
    return result


def preserve_entry_time_fields(previous, incoming, btb):
    """Older Android versions do not know end time/binding fields yet."""
    if not isinstance(incoming, dict):
        return incoming
    result = dict(incoming)
    if 'uhrzeitBis' not in result and 'uhrzeitBis' in previous:
        result['uhrzeitBis'] = previous['uhrzeitBis']
    report = (clock(btb.get('started_at')), clock(btb.get('ended_at')))
    for prefix in ('personal', 'maschinen'):
        key = prefix + 'Zeitbindung'
        if key not in result and key in previous:
            actual = (result.get(prefix + 'Von') or None, result.get(prefix + 'Bis') or None)
            was = (previous.get(prefix + 'Von') or None, previous.get(prefix + 'Bis') or None)
            result[key] = previous[key] if actual == was else ('REPORT' if actual == report else 'MANUAL')
    return result
