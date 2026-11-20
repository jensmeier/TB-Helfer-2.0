"""KOMPAT-1120: executable security and visibility regression tests.

No NAS/network access, no migration and no production data. These use real handlers
with lightweight SQL boundary doubles and exercise refused writes / pagination.
"""
import contextlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi import HTTPException

from tbserver import files as upload_api, survey_sections, track_data, survey_originals
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


    def test_committed_upload_survives_stale_pdf_cleanup_error(self):
        # Exercise the real complete_upload handler and real filesystem paths.
        # SQL and dependencies are replaced, but no production data is touched.
        class UploadCursor:
            last = ""

            def __enter__(self):
                return self

            def __exit__(self, *args):
                return False

            def execute(self, sql, params=None):
                self.last = str(sql)

            def fetchone(self):
                if "SELECT now()" in self.last:
                    return {"expired": False}
                if "INSERT INTO tb.files" in self.last:
                    return {"id": "committed-file", "worksite_id": "site"}
                raise AssertionError(f"Unexpected fetchone after: {self.last[:100]}")

        cursor = UploadCursor()
        committed = []
        @contextlib.contextmanager
        def transaction():
            yield Connection(cursor)
            committed.append(True)

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "uploaded.part").write_bytes(b"test-data")
            upload = {
                "worksite_id": "site", "company_id": "company",
                "user_id": "user", "status": "OPEN", "temp_storage_key": "uploaded.part",
                "received_bytes": 9, "expected_bytes": 9, "expected_sha256": None,
                "target_kind": "ATTACHMENT", "original_name": "evidence.bin",
                "mime_type": "application/octet-stream", "expires_at": None,
            }
            context = {**self.context, "company_id": "company"}
            with contextlib.ExitStack() as stack:
                for name, replacement in {
                    "transaction": transaction,
                    "begin_mutation": lambda *a, **kw: None,
                    "_load_upload": lambda *a, **kw: upload,
                    "_authorize_upload": lambda *a, **kw: context,
                    "_absolute_storage_path": lambda key: root / key,
                    "_validate_link": lambda *a, **kw: None,
                    "canonical_photo_metadata": lambda **kw: {
                        "captured_at": None, "gps_lat": None, "gps_lon": None,
                        "gps_accuracy_m": None, "photo_metadata": {},
                    },
                    "emit_change": lambda *a, **kw: None,
                    "audit": lambda *a, **kw: None,
                    "finish_mutation": lambda *a, **kw: None,
                }.items():
                    stack.enter_context(patch.object(upload_api, name, replacement))
                stack.enter_context(patch.object(upload_api, "remove_storage_keys", side_effect=OSError("cleanup unavailable")))
                result = upload_api.complete_upload(
                    upload_id="00000000-0000-0000-0000-000000000002",
                    payload=upload_api.UploadComplete(),
                    user=self.user,
                    mutation=object(),
                )
            self.assertEqual(result["file"]["id"], "committed-file")
            self.assertEqual(committed, [True])
            self.assertFalse((root / "uploaded.part").exists())
            matches = list((root / "companies").rglob("evidence.bin"))
            self.assertEqual(len(matches), 1)
            self.assertEqual(matches[0].read_bytes(), b"test-data")

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
