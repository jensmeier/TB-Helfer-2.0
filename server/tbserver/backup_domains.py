"""Fail-closed row ownership for OPS-RESTORE-005 (schema 001--021).

Pure planning functions: no database writes, deletion, restore or live switching.
An adapter must read all rows in ONE repeatable-read snapshot. Unknown tables
require an explicit contract update, never a default 'ignore' decision.
"""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from typing import Mapping, Sequence


USER = "USER"
TRACK = "TRACK"
CONTROL = "CONTROL"

USER_TABLES = frozenset({
    "companies", "users", "portal_credentials", "portal_sessions", "devices",
    "device_activation_tokens", "permission_profiles", "worksites", "worksite_nodes",
    "worksite_memberships", "worksite_invitations", "teams", "team_members", "btbs",
    "btb_entries", "btb_original_snapshots", "btb_participant_states", "upload_sessions",
    "defects", "acceptances", "track_data", "track_data_sources", "track_data_km_points",
    "mutation_receipts", "change_feed", "sync_cursors", "deletion_tombstones", "audit_log",
})
TRACK_TABLES = frozenset({
    "catalog_routes", "catalog_stations", "catalog_sections", "catalog_objects",
    "catalog_history", "survey_network_entities", "survey_network_changes",
})
CONTROL_TABLES = frozenset({
    "schema_migrations", "releases", "background_jobs", "backup_runs",
    "system_metrics_hourly", "server_settings", "system_schedule_runs",
    # Receipts are transient responses tied both to a user and a topology epoch.
    # The runtime adapter must explicitly reset them after a restore.
    "survey_network_receipts",
})
MIXED_TABLES = frozenset({
    "survey_originals", "survey_parse_catalog", "survey_catalog_assets",
    "survey_catalog_axes", "survey_catalog_segments", "survey_catalog_conflicts",
    "survey_catalog_history", "files", "file_links",
})
TABLES = USER_TABLES | TRACK_TABLES | CONTROL_TABLES | MIXED_TABLES
PRIMARY_KEYS = {table: ("id",) for table in TABLES}
PRIMARY_KEYS.update({
    "schema_migrations": ("version",), "permission_profiles": ("profile_key",),
    "portal_credentials": ("user_id",), "team_members": ("team_id", "user_id"),
    "btb_original_snapshots": ("btb_id",), "btb_participant_states": ("btb_id", "user_id"),
    "file_links": ("file_id", "entity_type", "entity_id", "purpose"),
    "survey_parse_catalog": ("original_id",), "track_data_sources": ("dataset_id", "original_id", "source_role"),
    "sync_cursors": ("device_id", "company_id", "scope_key"),
    "change_feed": ("change_id",), "catalog_history": ("change_id",),
    "survey_catalog_history": ("change_id",), "survey_network_changes": ("change_id",),
    "survey_network_receipts": ("user_id", "client_change_id"),
    "system_metrics_hourly": ("bucket_start",), "server_settings": ("setting_key",),
})
CATALOG_ENTITIES = {
    "ASSET": "survey_catalog_assets", "AXIS": "survey_catalog_axes",
    "SEGMENT": "survey_catalog_segments", "CONFLICT": "survey_catalog_conflicts",
}
FILE_LINK_ENTITIES = {"WORKSITE": "worksites", "BTB": "btbs", "DEFECT": "defects", "ACCEPTANCE": "acceptances"}


class DomainError(RuntimeError):
    def __init__(self, code: str, table: str = "", field: str = ""):
        self.code, self.table, self.field = code, table, field
        super().__init__(":".join(x for x in (code, table, field) if x))


def _key(value):
    # psycopg UUID and JSON string representations describe the same identity.
    return str(value) if value is not None else None


def row_key(table: str, row: Mapping) -> tuple:
    key = tuple(_key(row.get(c)) for c in PRIMARY_KEYS[table])
    if any(value is None for value in key):
        raise DomainError("BACKUP_PRIMARY_KEY_MISSING", table)
    return key


def _scope(row: Mapping, table: str) -> str:
    scope, company = row.get("scope"), row.get("company_id")
    if scope == "GLOBAL" and company is None:
        return TRACK
    if scope == "COMPANY" and company is not None:
        return USER
    raise DomainError("BACKUP_SCOPE_INVALID", table)


class DomainSnapshot:
    def __init__(self, rows: Mapping[str, Sequence[Mapping]]):
        if set(rows) != TABLES:
            raise DomainError("BACKUP_TABLE_SET_MISMATCH")
        self.rows = rows
        self.index = {}
        self.owners = {}
        self.visiting = set()
        self.global_original_keys = {row.get("storage_key") for row in rows["survey_originals"]
                                     if row.get("company_id") is None and row.get("storage_key")}
        for table, records in rows.items():
            indexed = {}
            for row in records:
                key = row_key(table, row)
                if key in indexed:
                    raise DomainError("BACKUP_DUPLICATE_PRIMARY_KEY", table)
                indexed[key] = row
            self.index[table] = indexed

    def _parent(self, table: str, identifier) -> Mapping:
        parent = self.index[table].get((_key(identifier),))
        if parent is None:
            raise DomainError("BACKUP_SCOPE_PARENT_MISSING", table)
        return parent

    def _parent_owner(self, table: str, identifier) -> str:
        return self.owner(table, self._parent(table, identifier))

    def owner(self, table: str, row: Mapping) -> str:
        if table not in TABLES:
            raise DomainError("BACKUP_TABLE_UNKNOWN", table)
        identity = (table, row_key(table, row))
        if identity in self.owners:
            return self.owners[identity]
        if identity in self.visiting:
            raise DomainError("BACKUP_SCOPE_CYCLE", table)
        self.visiting.add(identity)
        try:
            owner = self._owner(table, row)
            self.owners[identity] = owner
            return owner
        finally:
            self.visiting.remove(identity)

    def _owner(self, table: str, row: Mapping) -> str:
        if table in USER_TABLES:
            return USER
        if table in TRACK_TABLES:
            return TRACK
        if table in CONTROL_TABLES:
            return CONTROL
        if table == "survey_originals":
            if "company_id" not in row:
                raise DomainError("BACKUP_SCOPE_INVALID", table)
            return TRACK if row["company_id"] is None else USER
        if table == "survey_parse_catalog":
            return self._parent_owner("survey_originals", row.get("original_id"))
        if table in {"survey_catalog_assets", "survey_catalog_axes"}:
            return _scope(row, table)
        if table in {"survey_catalog_segments", "survey_catalog_conflicts"}:
            return self._parent_owner("survey_catalog_axes", row.get("axis_id"))
        if table == "files":
            # A legacy GLOBAL survey original may also have a tb.files row.
            # Company/worksite files remain in USER even when bytes are shared.
            if "company_id" not in row or "worksite_id" not in row:
                raise DomainError("BACKUP_SCOPE_INVALID", table)
            if row["company_id"] is not None or row["worksite_id"] is not None:
                return USER
            key = row.get("storage_key")
            return TRACK if key in self.global_original_keys else USER
        if table == "file_links":
            if row.get("entity_type") not in FILE_LINK_ENTITIES:
                raise DomainError("BACKUP_FILE_LINK_TYPE_UNKNOWN", table)
            # A worksite link to a shared original belongs to the worksite,
            # not to the shared file. Validate the cross-domain file FK later.
            return USER
        if table == "survey_catalog_history":
            target = CATALOG_ENTITIES.get(row.get("entity_type"))
            if target is None:
                raise DomainError("BACKUP_HISTORY_ENTITY_UNKNOWN", table)
            parent = self.index[target].get((_key(row.get("entity_id")),))
            if parent is not None:
                return self.owner(target, parent)
            # Deleted historic entities need explicit provenance; never guess
            # GLOBAL from a missing company_id on a segment/history row.
            evidence = set()
            for image in (row.get("before_data"), row.get("after_data")):
                if not isinstance(image, dict):
                    continue
                if "scope" in image:
                    evidence.add(_scope(image, table))
                elif image.get("axis_id") is not None:
                    evidence.add(self._parent_owner("survey_catalog_axes", image["axis_id"]))
            if len(evidence) != 1:
                raise DomainError("BACKUP_HISTORY_SCOPE_AMBIGUOUS", table)
            return evidence.pop()
        raise DomainError("BACKUP_TABLE_UNCLASSIFIED", table)

    def partition(self) -> dict:
        result = {domain: {table: [] for table in TABLES} for domain in (USER, TRACK, CONTROL)}
        for table, rows in self.rows.items():
            for row in rows:
                result[self.owner(table, row)][table].append(dict(row))
        return result


@dataclass(frozen=True)
class ForeignKey:
    table: str
    columns: tuple[str, ...]
    target: str
    target_columns: tuple[str, ...]
    match_full: bool = False


def validate_foreign_keys(rows: Mapping, foreign_keys: Sequence[ForeignKey]) -> None:
    """Reject dangling values even for ON DELETE SET NULL/CASCADE constraints.

    The adapter supplies ALL FK definitions read from pg_constraint. PostgreSQL
    must still validate the fully restored target (unique/check/FK constraints).
    """
    indices = {}
    for fk in foreign_keys:
        if (fk.table not in rows or fk.target not in rows or not fk.columns
                or len(fk.columns) != len(fk.target_columns)):
            raise DomainError("BACKUP_FOREIGN_KEY_SCHEMA_INVALID", fk.table)
        index_key = (fk.target, fk.target_columns)
        if index_key not in indices:
            indices[index_key] = {tuple(_key(row.get(c)) for c in fk.target_columns)
                                  for row in rows[fk.target]}
        for row in rows[fk.table]:
            if any(c not in row for c in fk.columns):
                raise DomainError("BACKUP_FOREIGN_KEY_FIELD_MISSING", fk.table)
            values = tuple(_key(row[c]) for c in fk.columns)
            if any(value is None for value in values):
                if fk.match_full and not all(value is None for value in values):
                    raise DomainError("BACKUP_FOREIGN_KEY_PARTIAL_NULL", fk.table)
                continue
            if values not in indices[index_key]:
                raise DomainError("BACKUP_CROSS_REFERENCE_MISSING", fk.table, ",".join(fk.columns))


def combine(current: DomainSnapshot, saved: DomainSnapshot, kind: str) -> dict:
    """Plan USER/TRACK replacement without modifying inputs or operational rows.

    Return value is NOT a validated restore target. The caller must run all FK,
    JSON, file-content, identity/version and schema checks before any activation.
    Nullable authors are deliberately not nulled here: explicit provenance
    handling in the target adapter is required, or FK validation rejects it.
    """
    if kind not in {USER, TRACK}:
        raise DomainError("BACKUP_PARTIAL_KIND_INVALID")
    live_parts, saved_parts = current.partition(), saved.partition()
    combined = {table: [] for table in TABLES}
    for domain in (USER, TRACK, CONTROL):
        source = saved_parts if domain == kind else live_parts
        for table in TABLES:
            combined[table].extend(source[domain][table])
    # History counters from two snapshots may collide. Do not silently re-ID or
    # discard them; that needs the target importer's explicit sequence policy.
    DomainSnapshot(combined)
    return combined


def domain_counts(snapshot: DomainSnapshot) -> dict:
    counts = {domain: defaultdict(int) for domain in (USER, TRACK, CONTROL)}
    for table, rows in snapshot.rows.items():
        for row in rows:
            counts[snapshot.owner(table, row)][table] += 1
    return {domain: dict(tables) for domain, tables in counts.items()}


def validate_domain_references(snapshot: DomainSnapshot) -> None:
    """Known non-FK references only; not a substitute for business validation.

    Historical snapshots/responses can legitimately point at older versions.
    Validate current topology and active workspace assignments, not their logs.
    Existing stale version markers are left visible rather than silently changed.
    """
    def reference(table, identifier, field):
        if identifier is None or (_key(identifier),) not in snapshot.index[table]:
            raise DomainError("BACKUP_JSON_REFERENCE_MISSING", table, field)
        return snapshot.index[table][(_key(identifier),)]

    def source(binding):
        if not isinstance(binding, dict):
            raise DomainError("BACKUP_JSON_REFERENCE_INVALID")
        axis = reference("survey_catalog_axes", binding.get("axis_id"), "axis_id")
        segment = reference("survey_catalog_segments", binding.get("segment_id"), "segment_id")
        if _key(segment.get("axis_id")) != _key(axis.get("id")):
            raise DomainError("BACKUP_JSON_AXIS_SEGMENT_MISMATCH")

    for row in snapshot.rows["file_links"]:
        table = FILE_LINK_ENTITIES.get(row.get("entity_type"))
        if table is None:
            raise DomainError("BACKUP_FILE_LINK_TYPE_UNKNOWN", "file_links")
        reference(table, row.get("entity_id"), "entity_id")
        reference("files", row.get("file_id"), "file_id")

    for row in snapshot.rows["survey_network_entities"]:
        data = row.get("data")
        if not isinstance(data, dict):
            raise DomainError("BACKUP_JSON_REFERENCE_INVALID", "survey_network_entities")
        if not row.get("active"):
            continue
        if data.get("binding") is not None:
            source(data["binding"])
        if data.get("km_reference") is not None:
            source(data["km_reference"])
        for binding in data.get("path_sections", []):
            source(binding)
        if data.get("owner_axis_id") is not None:
            reference("survey_catalog_axes", data["owner_axis_id"], "owner_axis_id")
        if data.get("catalog_link") is not None:
            reference("catalog_objects", data["catalog_link"].get("id"), "catalog_link")
        for endpoint in data.get("endpoints", []):
            node = reference("survey_network_entities", endpoint.get("node_id"), "node_id")
            if node.get("entity_type") != "NODE" or not node.get("active"):
                raise DomainError("BACKUP_JSON_NODE_UNAVAILABLE")

    for row in snapshot.rows["survey_catalog_axes"]:
        for record in (row.get("metadata") or {}).get("workspace_records", []):
            if record.get("active"):
                for anchor in record.get("anchors", []):
                    source(anchor)


def validate_original_dependencies(current: DomainSnapshot, saved: DomainSnapshot, kind: str) -> None:
    """Cross-domain original UUIDs must still identify the same immutable bytes."""
    if kind not in {USER, TRACK}:
        raise DomainError("BACKUP_PARTIAL_KIND_INVALID")
    columns = {
        "track_data": "source_original_id", "track_data_sources": "original_id",
        "track_data_km_points": "source_original_id", "survey_catalog_assets": "original_id",
        "survey_catalog_segments": "source_original_id",
    }
    for origin, other in ((saved, current), (current, saved)):
        for table, field in columns.items():
            for row in origin.rows[table]:
                # Only rows that actually survive this partial restore.
                owner = origin.owner(table, row)
                if (owner == kind) != (origin is saved):
                    continue
                original_id = row.get(field)
                if original_id is None:
                    continue
                original = origin._parent("survey_originals", original_id)
                if origin.owner("survey_originals", original) == owner:
                    continue
                replacement = other._parent("survey_originals", original_id)
                if other.owner("survey_originals", replacement) != origin.owner("survey_originals", original):
                    raise DomainError("BACKUP_ORIGINAL_SCOPE_CHANGED", table, field)
                for column in ("sha256", "byte_size", "storage_key"):
                    if original.get(column) is None or original.get(column) != replacement.get(column):
                        raise DomainError("BACKUP_ORIGINAL_IDENTITY_CHANGED", table, field)
