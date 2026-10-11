from __future__ import annotations

import math
import time
from threading import Lock
from .database import transaction

_lock=Lock()
_cached=None
_cached_until=0.0


def aggregate_segments(rows):
    routes=set(); stations=set(); intervals={}
    for row in rows:
        route=(row.get('route_number') or '').strip()
        station=(row.get('station_code') or row.get('station_name') or '').strip().casefold()
        if route: routes.add(route)
        if station: stations.add(station)
        start,end=row.get('km_from_m'),row.get('km_to_m')
        if route and start is not None and end is not None and math.isfinite(start) and math.isfinite(end):
            intervals.setdefault(route,[]).append((min(start,end),max(start,end)))
    metres=0.0
    for parts in intervals.values():
        left=right=None
        for start,end in sorted(parts):
            if left is None: left,right=start,end
            elif start<=right: right=max(right,end)
            else: metres+=right-left; left,right=start,end
        if left is not None: metres+=right-left
    return {'kilometres':round(metres/1000,1),'routes':len(routes),'stations':len(stations)}


def public_counts():
    global _cached,_cached_until
    with _lock:
        if _cached is not None and time.monotonic()<_cached_until:
            return dict(_cached)
        with transaction() as conn, conn.cursor() as cur:
            cur.execute('''SELECT a.route_number,a.station_code,a.station_name,s.km_from_m,s.km_to_m
              FROM tb.survey_catalog_axes a JOIN tb.survey_catalog_segments s ON s.axis_id=a.id
              WHERE a.scope='GLOBAL' AND a.status='ACTIVE' AND s.segment_status='ACTIVE' ''')
            result=aggregate_segments(cur.fetchall())
            # Includes test sites and archives; excludes trash/deleted objects.
            cur.execute("SELECT count(*) AS count FROM tb.worksites WHERE deleted_at IS NULL AND status<>'TRASHED'")
            result['worksites']=cur.fetchone()['count']
        _cached=result;_cached_until=time.monotonic()+900
        return dict(result)
