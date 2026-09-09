# Mobile Fachänderungen sind immer manuell erzeugte Daten und dürfen niemals durch einen
# manipulierten Client als belastbare Originaldaten (grün) markiert werden. Der vorhandene
# Mutation-Endpunkt bleibt vollständig erhalten; vor seinem Aufruf wird bei SECTION/OBJECT-UPSERT
# der Status serverseitig zwingend auf MANUAL gesetzt.
_tb_sync_mutation_routes = [
    route for route in list(app.router.routes)
    if getattr(route, "path", None) == "/api/v1/sync/mutations"
    and "POST" in (getattr(route, "methods", set()) or set())
]
if len(_tb_sync_mutation_routes) != 1:
    raise RuntimeError(f"SYNC_MUTATION_ROUTE_COUNT:{len(_tb_sync_mutation_routes)}")
_tb_sync_mutations_original = _tb_sync_mutation_routes[0].endpoint
for _tb_sync_mutation_route in _tb_sync_mutation_routes:
    app.router.routes.remove(_tb_sync_mutation_route)


@app.post("/api/v1/sync/mutations")
def sync_mutations_mobile_manual(req: SyncMutationRequest, authorization: str | None = Header(default=None)):
    entity_type = sync_type_for_client(req.entity_type)
    if req.operation.upper() == "UPSERT" and entity_type in ("SECTION", "OBJECT"):
        req.payload["data_status"] = "MANUAL"
    return _tb_sync_mutations_original(req, authorization)


del _tb_sync_mutation_route, _tb_sync_mutation_routes
