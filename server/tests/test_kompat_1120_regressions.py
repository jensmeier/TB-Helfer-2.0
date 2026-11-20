"""KOMPAT-1120: executable security and visibility regression tests.

No NAS/network access, no migration and no production data. These use real handlers
with lightweight SQL boundary doubles and exercise refused writes / pagination.
"""
import contextlib
import unittest
from unittest.mock import patch

from fastapi import HTTPException

from tbserver import survey_sections, track_data, survey_originals
from tbserver.permissions import require_permission


class Cursor:
    def __init__(self, rows=None):
        self.rows = list(rows or [])
        self.calls = []
        self.last = None

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def execute(self, sql, params=None):
        self.last = (str(sql), tuple(params or ()))
        self.calls.append(self.last)

    def fetchone(self):
        if "FROM tb.files" in (self.last or ("",))[0]:
            return {
                "id": "file", "worksite_id": "site", "company_id": "company",
                "deleted_at": None, "created_by": "other",
            }
        return None

    def fetchall(self):
        assert self.last is not None
        offset = int(self.last[1][-1])
        size = int(self.last[1][-2])
        return self.rows[offset:offset+size]


class Connection:
    def __init__(self, cursor):
        self._cursor = cursor

    def cursor(self):
        return self._cursor


class KompatSecurityTests(unittest.TestCase):
    def setUp(self):
        self.user = {"user_id": "user", "company_id": "company", "company_role": "USER"}
        self.context = {
            "company_id": "company", "id": "site",
            "effective_permissions": {"track.edit": True, "worksite.view": True},
            "effective_area_scope": {"km_from_m": 0, "km_to_m": 100},
        }

    def test_track_create_rejects_axis_outside_km_scope_before_database_insert(self):
        cursor = Cursor()
        payload = track_data.TrackDataCreate(
            dataset_key="track-1", dataset_kind="AXIS",
            payload={"samples": [{"km_meter": 1}, {"km_meter": 101}]},
        )
        with patch.object(track_data, "transaction", lambda: contextlib.nullcontext(Connection(cursor))), \
             patch.object(track_data, "worksite_context", lambda *a: self.context), \
             patch.object(track_data, "begin_mutation", lambda *a, **k: None):
            with self.assertRaises(HTTPException) as captured:
                track_data.create_track_data("site", payload, self.user, object())
        self.assertEqual(captured.exception.status_code, 403)
        self.assertFalse(any("INSERT INTO tb.track_data" in sql for sql, _ in cursor.calls))

    def test_survey_list_finds_visible_rows_after_200_hidden_rows(self):
        cursor = Cursor([
            {"id": f"hidden-{i}", "dataset_kind": "SURVEY_SECTION",
             "payload": {"calibration": {"km_at_start_m": 1000, "km_at_end_m": 1100}}}
            for i in range(220)
        ] + [
            {"id": "visible", "dataset_kind": "SURVEY_SECTION",
             "payload": {"calibration": {"km_at_start_m": 10, "km_at_end_m": 20}}}
        ])
        with patch.object(survey_sections, "transaction", lambda: contextlib.nullcontext(Connection(cursor))), \
             patch.object(survey_sections, "worksite_context", lambda *a: self.context):
            result = survey_sections.list_survey_sections("site", limit=1, user=self.user)
        self.assertEqual([row["id"] for row in result["survey_sections"]], ["visible"])
        self.assertGreaterEqual(len(cursor.calls), 3)

    def test_read_only_cannot_register_a_worksite_file_as_survey_original(self):
        cursor = Cursor()
        self.context["effective_permissions"]["track.edit"] = False
        payload = survey_originals.SurveyOriginalFromFile(file_id="00000000-0000-0000-0000-000000000001", data_kind="TRA")
        with patch.object(survey_originals, "transaction", lambda: contextlib.nullcontext(Connection(cursor))), \
             patch.object(survey_originals, "worksite_context", lambda *a: self.context):
            with self.assertRaises(HTTPException) as captured:
                survey_originals.import_survey_original_from_file(payload, self.user)
        self.assertEqual(captured.exception.status_code, 403)
        self.assertFalse(any("UPDATE tb.files" in sql for sql, _ in cursor.calls))


if __name__ == "__main__":
    unittest.main()
