"""Optional real PostgreSQL publication regression. Never targets the NAS by default.

Requires psycopg and TB_SYNC_TEST_DSN pointing to an isolated database whose name
contains 'test'. Creates/drops only a uniquely named test schema. No real secrets
in argv, source or reports. Not run when no explicit DSN is supplied.
"""
import os
import threading
from uuid import uuid4
from sync_fix_1103 import production
from typing import Any
from uuid import UUID

def main():
    dsn = os.environ.get("TB_SYNC_TEST_DSN")
    if not dsn:
        raise SystemExit("NOT RUN: set TB_SYNC_TEST_DSN for an isolated PostgreSQL test database")
    import psycopg
    from psycopg import sql
    from psycopg.rows import dict_row
    emit = production("tbserver/events.py", {"emit_change"}, dict(Any=Any, UUID=UUID))["emit_change"]
    schema = "sync_fix_" + uuid4().hex
    with psycopg.connect(dsn, autocommit=True, row_factory=dict_row) as setup:
        database = setup.execute("SELECT current_database() AS name").fetchone()["name"]
        if "test" not in database.lower():
            raise SystemExit("REFUSED: test database name required")
        setup.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
        try:
            setup.execute(sql.SQL("""CREATE TABLE {}.change_feed (
                change_id bigserial PRIMARY KEY, company_id uuid, worksite_id uuid,
                entity_type text, entity_id uuid, operation text,
                entity_version int, actor_user_id uuid)""").format(sql.Identifier(schema)))
            for rollback in (False, True):
                a = psycopg.connect(dsn, row_factory=dict_row)
                b = psycopg.connect(dsn, row_factory=dict_row)
                started, allocated = threading.Event(), threading.Event()
                errors = []
                class Cursor:
                    def __init__(self, conn):
                        self.connection = conn
                        self.cur = conn.cursor()
                    def execute(self, statement, params):
                        self.cur.execute(statement.replace("tb.change_feed", schema + ".change_feed"), params)
                    def fetchone(self): return self.cur.fetchone()
                def publish(conn):
                    return emit(Cursor(conn), company_id=uuid4(), worksite_id=uuid4(),
                                entity_type="BTB", entity_id=uuid4(), operation="UPDATE",
                                entity_version=1, actor_user_id=uuid4())
                first = publish(a)
                def second():
                    started.set()
                    try:
                        second_id = publish(b)
                        assert second_id > first
                        allocated.set()
                        b.commit()
                    except BaseException as error:
                        errors.append(type(error).__name__)
                        b.rollback()
                thread = threading.Thread(target=second)
                thread.start()
                try:
                    assert started.wait(2)
                    assert not allocated.wait(.1), "later ID allocated before first transaction ended"
                    if rollback: a.rollback()
                    else: a.commit()
                    thread.join(5)
                    assert not thread.is_alive(), "publication remained blocked"
                    assert not errors, errors
                    assert allocated.is_set()
                finally:
                    a.rollback()
                    b.rollback()
                    a.close()
                    b.close()
            print("PASS: real PostgreSQL blocks later publication until commit/rollback")
        finally:
            setup.execute(sql.SQL("DROP SCHEMA {} CASCADE").format(sql.Identifier(schema)))

if __name__ == "__main__":
    main()

