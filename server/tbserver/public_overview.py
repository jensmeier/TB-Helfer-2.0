from __future__ import annotations

import time
from threading import Lock
from .database import transaction
from .survey_catalog import _coverage_summary_data

_lock = Lock()
_cached = None
_cached_until = 0.0


def public_counts():
    """Publish only aggregate totals, using the same length definition as admin."""
    global _cached, _cached_until
    with _lock:
        if _cached is not None and time.monotonic() < _cached_until:
            return dict(_cached)
        # Canonical coverage handles station/geometry lengths and manual overlays.
        # Parallel tracks count separately; this is not route-chainage coverage.
        coverage = _coverage_summary_data()
        result = {
            'kilometres': round(coverage['total_length_m'] / 1000, 2),
            'known_kilometres': round(coverage['known_km_length_m'] / 1000, 2),
            'stations': coverage['station_count'],
            'routes': coverage['route_count'],
        }
        with transaction() as conn, conn.cursor() as cur:
            # Includes test sites and archives; excludes trash/deleted objects.
            cur.execute("SELECT count(*) AS count FROM tb.worksites WHERE deleted_at IS NULL AND status<>'TRASHED'")
            result['worksites'] = cur.fetchone()['count']
        _cached = result
        _cached_until = time.monotonic() + 900
        return dict(result)
