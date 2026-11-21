"""Server security regressions for destructive operations and release files.

These execute the actual FastAPI handlers with isolated filesystem/SQL fakes.
No NAS access, external calls, migrations or production changes.
"""
from __future__ import annotations

import asyncio
from contextlib import contextmanager, ExitStack
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
from unittest.mock import patch
from uuid import UUID

from tbserver import admin_purge, releases


SITE = UUID("b07d8392-82e7-44a4-8e55-cb074e4d9c11")
COMPANY = UUID("d010865d-01b4-4c12-926d-22caa6721b20")
RELEASE = UUID("ef9a82e8-7383-44b1-9b31-bc45a90382ab")
ACTOR = UUID("a05c2e43-cf37-459a-814e-ac93ed1555d1")


class Cursor:
    def __init__(self):
        self.sql = ""
        self.executed = []
        self._row = None

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def execute(self, sql, params=()):
        self.sql = " ".join(str(sql).split())
        self.executed.append((self.sql, tuple(params or ())))
        self._row = None
        if "SELECT id, company_id, name, status FROM tb.worksites" in self.sql:
            self._row = {"id": SITE, "company_id": COMPANY, "name": "Test", "status": "ACTIVE"}
        elif "SELECT id, name, status FROM tb.companies" in self.sql:
            self._row = {"id": COMPANY, "name": "Testfirma", "status": "ACTIVE"}
        elif "count(*)" in self.sql:
            self._row = {"count": 0}

    def fetchone(self):
        return self._row

    def fetchall(self):
        if "FROM tb.files f WHERE f.worksite_id" in self.sql:
            # Simulated Postgres result after the NOT EXISTS guard:
            # shared original remains, ordinary photo is removed.
            return [{"storage_key": "worksite/photo.jpg"}]
        return []


class Connection:
    def __init__(self, cur):
        self.cur = cur

    def cursor(self):
        return self.cur


class PurgeProtectionTests(TestCase):
    def setUp(self):
        self.cursor = Cursor()
        self.deleted = []

        @contextmanager
        def tx():
            yield Connection(self.cursor)

        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(patch.object(admin_purge, "transaction", tx))
        self.stack.enter_context(patch.object(admin_purge, "audit"))
        self.stack.enter_context(patch.object(admin_purge, "_remove_keys_after_commit",
                                              side_effect=lambda keys: self.deleted.extend(keys)))

    def confirm(self, entity_id):
        return admin_purge.PurgeConfirmation(
            confirm_id=entity_id, confirm_text="ENDGUELTIG LOESCHEN"
        )

    def test_worksite_purge_preserves_same_storage_key_as_company_original(self):
        response = admin_purge.purge_worksite(
            SITE, self.confirm(SITE), {"user_id": ACTOR}
        )
        self.assertEqual(response.status_code, 204)
        sql = next(sql for sql, _ in self.cursor.executed
                   if "FROM tb.files f WHERE f.worksite_id" in sql)
        self.assertIn("NOT EXISTS", sql)
        self.assertIn("tb.survey_originals", sql)
        self.assertIn("o.storage_key = f.storage_key", sql)
        self.assertEqual(self.deleted, ["worksite/photo.jpg"])
        self.assertNotIn("reference/survey-originals/shared.tra", self.deleted)

    def test_company_purge_blocks_deletion_of_published_global_geometry(self):
        class GlobalCursor(Cursor):
            def execute(self, sql, params=()):
                super().execute(sql, params)
                if "FROM tb.survey_catalog_segments s" in self.sql and "ax.scope = 'GLOBAL'" in self.sql:
                    self._row = {"one": 1}
        self.cursor = GlobalCursor()
        from fastapi import HTTPException
        with self.assertRaises(HTTPException) as captured:
            admin_purge.purge_company(COMPANY, self.confirm(COMPANY), {"user_id": ACTOR})
        self.assertEqual(captured.exception.status_code, 409)
        self.assertEqual(captured.exception.detail, "COMPANY_PURGE_GLOBAL_CATALOG_REFERENCES")
        self.assertFalse(any("DELETE FROM tb.worksites" in sql for sql, _ in self.cursor.executed))

    def test_company_purge_does_not_delete_empty_global_axes(self):
        response = admin_purge.purge_company(
            COMPANY, self.confirm(COMPANY), {"user_id": ACTOR}
        )
        self.assertEqual(response.status_code, 204)
        axis_sql = next(sql for sql, _ in self.cursor.executed
                        if "DELETE FROM tb.survey_catalog_axes a" in sql)
        self.assertIn("a.scope = 'COMPANY' AND a.company_id = %s", axis_sql)
        self.assertNotIn("NOT EXISTS", axis_sql)
        self.assertNotIn("OR (", axis_sql)


class RequestStub:
    headers = {"content-length": "12"}

    async def stream(self):
        yield b"new-release!"


class ReleaseCommitTests(TestCase):
    def run_upload(self, *, fail_cleanup=False, fail_audit=False):
        data = b"new-release!"
        row = {
            "id": RELEASE, "platform": "ANDROID", "version_name": "1.3.47",
            "build_number": 200097, "minimum_supported_build": 200050,
            "mandatory": False, "download_url": None, "release_notes": "",
            "storage_key": None, "sha256": None, "byte_size": None,
            "status": "DRAFT", "published_at": None,
        }
        cur = Cursor()
        committed = []
        @contextmanager
        def tx():
            yield Connection(cur)
            committed.append(True)

        # The in-memory DB fake returns the row updated within the transaction.
        def execute(sql, params=()):
            Cursor.execute(cur, sql, params)
            if "UPDATE tb.releases SET storage_key" in cur.sql:
                cur._row = dict(row, storage_key=params[0], byte_size=params[1],
                                sha256=params[2])
        cur.execute = execute

        with TemporaryDirectory() as temp:
            root = Path(temp)
            final = root / releases._release_storage_key(row)
            final.parent.mkdir(parents=True)
            final.write_bytes(b"old-release")
            real_finish = releases._finish_binary_files
            def finish(*paths):
                if fail_cleanup and len(paths) == 2:
                    raise OSError("cleanup after commit unavailable")
                return real_finish(*paths)

            with ExitStack() as st:
                st.enter_context(patch.object(releases, "transaction", tx))
                st.enter_context(patch.object(releases, "_load_release",
                    side_effect=lambda *_a, **_k: dict(row)))
                st.enter_context(patch.object(releases, "_absolute_storage_path",
                    side_effect=lambda key: root / key))
                st.enter_context(patch.object(releases, "_finish_binary_files",
                    side_effect=finish))
                st.enter_context(patch.object(releases, "audit",
                    side_effect=(OSError("injected transaction failure") if fail_audit else None)))
                if fail_audit:
                    with self.assertRaises(OSError):
                        asyncio.run(releases.admin_upload_release_binary(
                            RELEASE, RequestStub(), {"user_id": ACTOR}
                        ))
                else:
                    result = asyncio.run(releases.admin_upload_release_binary(
                        RELEASE, RequestStub(), {"user_id": ACTOR}
                    ))
                    self.assertEqual(result["release"]["binary"]["byte_size"], len(data))
            # The read-only DRAFT preflight is a separate successful transaction.
            # An injected audit exception aborts only the subsequent write.
            self.assertEqual(len(committed), 1 if fail_audit else 2)
            self.assertEqual(final.read_bytes(), b"old-release" if fail_audit else data)

    def test_cleanup_after_commit_keeps_confirmed_apk(self):
        self.run_upload(fail_cleanup=True)

    def test_before_commit_exception_restores_previous_release(self):
        self.run_upload(fail_audit=True)
