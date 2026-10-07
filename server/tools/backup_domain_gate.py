"""Destructive fixtures ONLY in a specifically named disposable CI database."""
from pathlib import Path
import os
import sys
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


def main():
    from tbserver.config import settings
    if (os.getenv("CI") != "true" or settings.environment != "test"
            or settings.db_name != "tb_gate_domains" or settings.db_host != "127.0.0.1"):
        raise RuntimeError("DISPOSABLE_CI_DOMAIN_DATABASE_REQUIRED")
    import psycopg
    from psycopg.rows import dict_row
    from tbserver.migrations import migrate
    from tbserver.backup_domains import (DomainError, DomainSnapshot, TRACK, USER,
        combine, validate_foreign_keys, validate_domain_references, validate_original_dependencies)
    from tbserver.backup_domain_snapshot import capture_domains
    migrate()
    company, site, original, axis, segment, dataset = [uuid4() for _ in range(6)]
    def connect():
        return psycopg.connect(host=settings.db_host, port=settings.db_port,
            dbname=settings.db_name, user=settings.db_user, password=settings.db_password,
            autocommit=True, row_factory=dict_row)
    with connect() as conn, conn.cursor() as cur:
        cur.execute("SELECT count(*) AS n FROM tb.companies")
        assert cur.fetchone()["n"] == 0, "EMPTY_DATABASE_REQUIRED"
        empty, fk = capture_domains(conn)
        with conn.transaction():
            cur.execute("INSERT INTO tb.companies(id,name) VALUES (%s,'CI domain fixture')", (company,))
            cur.execute("INSERT INTO tb.worksites(id,company_id,name) VALUES (%s,%s,'CI site')", (site,company))
            cur.execute("""INSERT INTO tb.survey_originals(id,data_kind,storage_key,original_name,sha256,byte_size)
                           VALUES (%s,'TRA','fixture/global.tra','global.tra',%s,10)""", (original,"a"*64))
            cur.execute("""INSERT INTO tb.survey_catalog_axes(id,scope,identity_key,axis_type,display_name)
                           VALUES (%s,'GLOBAL','ci-axis','TRACK','CI axis')""", (axis,))
            cur.execute("""INSERT INTO tb.survey_catalog_segments(id,axis_id,source_kind,source_original_id,merge_status)
                           VALUES (%s,%s,'ORIGINAL_TRA',%s,'PRIMARY')""", (segment,axis,original))
            cur.execute("""INSERT INTO tb.track_data(id,worksite_id,dataset_key,dataset_kind,source_original_id)
                           VALUES (%s,%s,'ci-track','TRA',%s)""", (dataset,site,original))
            cur.execute("""INSERT INTO tb.track_data_sources(dataset_id,original_id,source_role)
                           VALUES (%s,%s,'GEOMETRY'),(%s,%s,'KM')""", (dataset,original,dataset,original))
        populated, foreign_keys = capture_domains(conn)
        assert len(foreign_keys) > 60, len(foreign_keys)
        validate_foreign_keys(populated.rows, foreign_keys)
        validate_domain_references(populated)
        assert len(populated.partition()[USER]["track_data_sources"]) == 2
        assert len(populated.partition()[TRACK]["survey_originals"]) == 1
        validate_original_dependencies(populated, populated, USER)
        validate_original_dependencies(populated, populated, TRACK)
        combined = combine(populated, empty, TRACK)
        try:
            validate_foreign_keys(combined, foreign_keys)
        except DomainError as exc:
            assert exc.code == "BACKUP_CROSS_REFERENCE_MISSING", exc.code
        else:
            raise AssertionError("DANGLING_SOURCE_ACCEPTED")
        # The planner is read-only: the source FK and original row still exist.
        cur.execute("SELECT source_original_id FROM tb.track_data WHERE id=%s", (dataset,))
        assert cur.fetchone()["source_original_id"] == original
        try:
            capture_domains(conn, max_bytes=1)
        except DomainError as exc:
            assert exc.code == "BACKUP_SNAPSHOT_PLANNING_LIMIT", exc.code
        else:
            raise AssertionError("SNAPSHOT_LIMIT_NOT_ENFORCED")
        cur.execute("CREATE TABLE tb.ci_unknown_domain(id integer PRIMARY KEY)")
        try:
            try:
                capture_domains(conn)
            except DomainError as exc:
                assert exc.code == "BACKUP_TABLE_SET_MISMATCH", exc.code
            else:
                raise AssertionError("UNKNOWN_TABLE_SILENTLY_IGNORED")
        finally:
            cur.execute("DROP TABLE tb.ci_unknown_domain")
        # Repeat after errors: transaction state and schema remain usable.
        repeated, repeated_fks = capture_domains(conn)
        validate_foreign_keys(repeated.rows, repeated_fks)
    print("PASS: PostgreSQL17 schema/PK/FK capture, scoped rows, dangling source rejection, read-only preservation, size/unknown-table guards")


if __name__ == "__main__":
    main()
