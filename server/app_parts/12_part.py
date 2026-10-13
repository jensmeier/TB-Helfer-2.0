# Finaler 0.7-Katalogfix nach dem verlustfreien Split des produktiven 0.6-Runtimes.
# Der zuvor registrierte GET-Endpunkt wird gezielt ersetzt, damit Bahnhöfe wie Strecken
# `current_version` liefern. Android 200018 nutzt dieses Feld für den Offline-Sync.
_tb_catalog_routes = [
    route for route in list(app.router.routes)
    if getattr(route, "path", None) == "/api/v1/sync/catalog"
    and "GET" in (getattr(route, "methods", set()) or set())
]
if len(_tb_catalog_routes) != 1:
    raise RuntimeError(f"SYNC_CATALOG_ROUTE_COUNT:{len(_tb_catalog_routes)}")
for _tb_catalog_route in _tb_catalog_routes:
    app.router.routes.remove(_tb_catalog_route)


@app.get("/api/v1/sync/catalog")
def sync_catalog(authorization: str | None = Header(default=None)):
    require_device(authorization)

    routes=q(
        """SELECT r.id,r.route_number,r.name,r.description,r.current_version,r.updated_at,
                  count(DISTINCT rs.id) AS section_count,
                  count(DISTINCT io.id) AS object_count
           FROM tb.routes r
           LEFT JOIN tb.route_sections rs ON rs.route_id=r.id AND rs.deleted_at IS NULL
           LEFT JOIN tb.infrastructure_objects io ON io.route_id=r.id AND io.deleted_at IS NULL
           WHERE r.deleted_at IS NULL
           GROUP BY r.id,r.route_number,r.name,r.description,r.current_version,r.updated_at
           ORDER BY r.route_number,r.name"""
    )

    stations=q(
        """SELECT s.id,s.station_code,s.name,s.short_name,s.description,s.current_version,s.updated_at,
                  count(DISTINCT io.id) AS object_count
           FROM tb.stations s
           LEFT JOIN tb.infrastructure_objects io ON io.station_id=s.id AND io.deleted_at IS NULL
           WHERE s.deleted_at IS NULL
           GROUP BY s.id,s.station_code,s.name,s.short_name,s.description,s.current_version,s.updated_at
           ORDER BY s.name,s.station_code"""
    )

    return {
        "status":"ok",
        "server_version":SERVER_VERSION,
        "routes":routes,
        "stations":stations,
    }


del _tb_catalog_route, _tb_catalog_routes
