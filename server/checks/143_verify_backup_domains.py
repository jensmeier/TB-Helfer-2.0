"""Pure data-boundary and merge-planning checks; no actual restore performed."""
from copy import deepcopy
from pathlib import Path
import re
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tbserver.backup_domains import (TABLES, PRIMARY_KEYS, USER, TRACK, CONTROL,
    DomainError, DomainSnapshot, ForeignKey, combine, validate_foreign_keys,
    validate_domain_references, validate_original_dependencies)


def fixture():
    rows = {name: [] for name in TABLES}
    rows.update({
        "companies": [{"id": "company", "name": "old"}],
        "users": [{"id": "user", "company_id": "company"}],
        "worksites": [{"id": "site", "company_id": "company"}],
        "survey_originals": [
            {"id": "global", "company_id": None, "storage_key": "global.dat", "sha256": "a" * 64, "byte_size": 10},
            {"id": "private", "company_id": "company", "storage_key": "private.dat", "sha256": "b" * 64, "byte_size": 12}],
        "survey_parse_catalog": [{"original_id": "global"}, {"original_id": "private"}],
        "survey_catalog_assets": [
            {"id": "ag", "scope": "GLOBAL", "company_id": None, "original_id": "global"},
            {"id": "ap", "scope": "COMPANY", "company_id": "company", "original_id": "private"}],
        "survey_catalog_axes": [
            {"id": "axis-g", "scope": "GLOBAL", "company_id": None},
            {"id": "axis-p", "scope": "COMPANY", "company_id": "company"}],
        "survey_catalog_segments": [
            {"id": "sg", "axis_id": "axis-g", "source_original_id": "global"},
            {"id": "sp", "axis_id": "axis-p", "source_original_id": "private"}],
        "survey_catalog_conflicts": [{"id": "conflict", "axis_id": "axis-p"}],
        "survey_catalog_history": [
            {"change_id": 1, "entity_type": "SEGMENT", "entity_id": "sg"},
            {"change_id": 2, "entity_type": "SEGMENT", "entity_id": "sp"}],
        "track_data": [{"id": "dataset", "worksite_id": "site", "source_original_id": "global"}],
        "track_data_sources": [{"dataset_id": "dataset", "original_id": "global", "source_role": "KM"}],
        "files": [
            {"id": "fg", "company_id": None, "worksite_id": None, "storage_key": "global.dat"},
            {"id": "fp", "company_id": "company", "worksite_id": "site", "storage_key": "photo.jpg"}],
        "file_links": [{"file_id": "fg", "entity_type": "WORKSITE", "entity_id": "site", "purpose": "ATTACHMENT"}],
        "backup_runs": [{"id": "backup", "kind": "FULL"}],
        "server_settings": [{"setting_key": "admin_automation", "value": {"enabled": True}}],
    })
    return rows


class Tests(unittest.TestCase):
    def test_schema_table_coverage_is_exact(self):
        root = Path(__file__).resolve().parents[1] / "v1_migrations"
        declared = set()
        for path in root.glob("*.sql"):
            declared.update(re.findall(r"CREATE TABLE tb\.([a-z_]+)", path.read_text()))
        self.assertEqual(TABLES, declared | {"schema_migrations"})

    def test_global_private_and_worksite_split(self):
        rows = fixture()
        parts = DomainSnapshot(rows).partition()
        self.assertEqual([x["id"] for x in parts[TRACK]["survey_originals"]], ["global"])
        self.assertEqual([x["id"] for x in parts[USER]["survey_originals"]], ["private"])
        self.assertEqual(parts[USER]["track_data"], rows["track_data"])
        self.assertEqual(parts[USER]["file_links"], rows["file_links"])
        self.assertEqual([x["id"] for x in parts[TRACK]["files"]], ["fg"])
        self.assertEqual([x["id"] for x in parts[USER]["files"]], ["fp"])
        self.assertEqual(parts[CONTROL]["backup_runs"], rows["backup_runs"])
        for table, records in rows.items():
            self.assertEqual(len(records), sum(len(p[table]) for p in parts.values()))

    def test_unknown_missing_tables_and_duplicates_block(self):
        for mutate in (lambda r: r.update(unknown=[]), lambda r: r.pop("companies"),
                       lambda r: r["users"].append(dict(r["users"][0]))):
            rows = fixture()
            mutate(rows)
            with self.assertRaises(DomainError):
                DomainSnapshot(rows)

    def test_missing_parent_and_invalid_scope_never_silently_drop_rows(self):
        for table, field, value in (("survey_catalog_segments", "axis_id", "missing"),
                                    ("survey_catalog_axes", "scope", "OTHER")):
            rows = fixture()
            rows[table][0][field] = value
            with self.assertRaises(DomainError):
                DomainSnapshot(rows).partition()

    def test_historic_scope_requires_unambiguous_provenance(self):
        rows = fixture()
        history = {"change_id": 3, "entity_type": "AXIS", "entity_id": "deleted",
                   "after_data": {"scope": "GLOBAL", "company_id": None}}
        rows["survey_catalog_history"].append(history)
        self.assertEqual(DomainSnapshot(rows).owner("survey_catalog_history", history), TRACK)
        history["before_data"] = {"scope": "COMPANY", "company_id": "company"}
        with self.assertRaises(DomainError):
            DomainSnapshot(rows).partition()
        history.pop("before_data")
        history["after_data"] = {}
        with self.assertRaises(DomainError):
            DomainSnapshot(rows).partition()

    def test_each_partial_restore_preserves_other_domain_and_control(self):
        saved, current = fixture(), fixture()
        current["companies"][0]["name"] = "current"
        current["survey_catalog_axes"][0]["name"] = "current axis"
        current["server_settings"][0]["value"] = {"enabled": False}
        original = deepcopy(current)
        user = combine(DomainSnapshot(current), DomainSnapshot(saved), USER)
        track = combine(DomainSnapshot(current), DomainSnapshot(saved), TRACK)
        self.assertEqual(user["companies"], saved["companies"])
        self.assertEqual(user["survey_catalog_axes"][1], current["survey_catalog_axes"][0])
        self.assertEqual(track["companies"], current["companies"])
        self.assertEqual(track["survey_catalog_axes"][1], saved["survey_catalog_axes"][0])
        self.assertEqual(user["server_settings"], current["server_settings"])
        self.assertEqual(track["server_settings"], current["server_settings"])
        self.assertEqual(current, original)

    def test_source_references_block_partial_restore_when_source_disappears(self):
        current, saved = fixture(), fixture()
        saved["survey_originals"] = [x for x in saved["survey_originals"] if x["id"] != "global"]
        saved["survey_parse_catalog"] = []
        saved["files"] = [x for x in saved["files"] if x["id"] != "fg"]
        combined = combine(DomainSnapshot(current), DomainSnapshot(saved), TRACK)
        keys = [ForeignKey("track_data", ("source_original_id",), "survey_originals", ("id",)),
                ForeignKey("track_data_sources", ("original_id",), "survey_originals", ("id",))]
        with self.assertRaises(DomainError) as caught:
            validate_foreign_keys(combined, keys)
        self.assertEqual(caught.exception.code, "BACKUP_CROSS_REFERENCE_MISSING")
        self.assertEqual(current["track_data"][0]["source_original_id"], "global")

    def test_two_source_roles_are_not_duplicate_primary_keys(self):
        rows = fixture()
        rows["track_data_sources"].append({"dataset_id": "dataset", "original_id": "global", "source_role": "GEOMETRY"})
        DomainSnapshot(rows)

    def test_history_collision_blocks_instead_of_overwriting(self):
        current, saved = fixture(), fixture()
        saved["survey_catalog_history"][1]["change_id"] = 1
        saved["survey_catalog_history"] = saved["survey_catalog_history"][1:]
        with self.assertRaises(DomainError):
            combine(DomainSnapshot(current), DomainSnapshot(saved), USER)

    def test_nullable_reference_is_checked_when_populated(self):
        rows = fixture()
        fk = ForeignKey("track_data", ("source_original_id",), "survey_originals", ("id",))
        validate_foreign_keys(rows, [fk])
        rows["track_data"][0]["source_original_id"] = "missing"
        with self.assertRaises(DomainError):
            validate_foreign_keys(rows, [fk])
        rows["track_data"][0]["source_original_id"] = None
        validate_foreign_keys(rows, [fk])

    def test_foreign_key_match_full_rejects_half_null(self):
        rows = fixture()
        rows["track_data_sources"][0]["source_role"] = None
        fk = ForeignKey("track_data_sources", ("dataset_id", "source_role"),
                        "track_data_sources", ("dataset_id", "source_role"), match_full=True)
        with self.assertRaises(DomainError):
            validate_foreign_keys(rows, [fk])

    def test_existing_uuid_with_different_original_bytes_is_rejected(self):
        current, saved = fixture(), fixture()
        for kind in (USER, TRACK):
            validate_original_dependencies(DomainSnapshot(current), DomainSnapshot(saved), kind)
        saved["survey_originals"][0]["sha256"] = "c" * 64
        for kind in (USER, TRACK):
            with self.assertRaises(DomainError) as caught:
                validate_original_dependencies(DomainSnapshot(current), DomainSnapshot(saved), kind)
            self.assertEqual(caught.exception.code, "BACKUP_ORIGINAL_IDENTITY_CHANGED")

    def test_polymorphic_file_link_needs_real_target(self):
        rows = fixture()
        validate_domain_references(DomainSnapshot(rows))
        rows["file_links"][0]["entity_id"] = "missing-site"
        with self.assertRaises(DomainError):
            validate_domain_references(DomainSnapshot(rows))

    def test_network_and_workspace_json_references(self):
        rows = fixture()
        binding = {"axis_id": "axis-g", "segment_id": "sg"}
        rows["survey_network_entities"] = [
            {"id": "node", "entity_type": "NODE", "active": True, "data": {"binding": dict(binding)}},
            {"id": "object", "entity_type": "OBJECT", "active": True, "data": {
                "endpoints": [{"node_id": "node"}], "km_reference": dict(binding), "path_sections": [dict(binding)]}}]
        rows["survey_catalog_axes"][0]["metadata"] = {"workspace_records": [{"active": True, "anchors": [dict(binding)]}]}
        validate_domain_references(DomainSnapshot(rows))
        rows["survey_network_entities"][0]["data"]["binding"]["segment_id"] = "sp"
        with self.assertRaises(DomainError):
            validate_domain_references(DomainSnapshot(rows))
        rows["survey_network_entities"][0]["data"]["binding"]["segment_id"] = "sg"
        rows["survey_catalog_axes"][0]["metadata"]["workspace_records"][0]["anchors"][0]["axis_id"] = "gone"
        with self.assertRaises(DomainError):
            validate_domain_references(DomainSnapshot(rows))


if __name__ == "__main__":
    unittest.main()
