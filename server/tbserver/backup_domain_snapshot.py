"""Read-only PostgreSQL snapshot adapter for the partial-backup planner.

This is deliberately not a restore writer. Large datasets exceeding the bounded
in-memory planning budget fail explicitly; a streaming writer is a separate gate.
"""
from __future__ import annotations

import json

from .backup_domains import TABLES, PRIMARY_KEYS, DomainError, DomainSnapshot, ForeignKey


def capture_domains(conn, *, max_bytes: int = 64 * 1024 * 1024):
    from psycopg import sql
    from psycopg.pq import TransactionStatus
    if conn.info.transaction_status != TransactionStatus.IDLE:
        raise DomainError("BACKUP_SNAPSHOT_IDLE_CONNECTION_REQUIRED")
    if not isinstance(max_bytes, int) or isinstance(max_bytes, bool) or max_bytes < 1:
        raise DomainError("BACKUP_SNAPSHOT_LIMIT_INVALID")
    with conn.transaction(), conn.cursor() as cur:
        cur.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY")
        cur.execute("""SELECT table_name FROM information_schema.tables
                       WHERE table_schema='tb' AND table_type='BASE TABLE'""")
        tables = {row["table_name"] for row in cur.fetchall()}
        if tables != TABLES:
            raise DomainError("BACKUP_TABLE_SET_MISMATCH")
        cur.execute("""SELECT t.relname AS table_name,
                              array_agg(a.attname ORDER BY k.ordinality) AS columns
                       FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
                       JOIN pg_namespace n ON n.oid=t.relnamespace
                       CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY k(attnum,ordinality)
                       JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.attnum
                       WHERE n.nspname='tb' AND c.contype='p'
                       GROUP BY t.relname""")
        keys = {row["table_name"]: tuple(row["columns"]) for row in cur.fetchall()}
        if keys != PRIMARY_KEYS:
            raise DomainError("BACKUP_PRIMARY_KEY_SCHEMA_CHANGED")
        cur.execute("""SELECT c.oid,c.confmatchtype,n2.nspname AS target_schema,
                              t.relname AS table_name,t2.relname AS target,
                              array_agg(a.attname ORDER BY k.i) AS columns,
                              array_agg(a2.attname ORDER BY k.i) AS target_columns
                       FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
                       JOIN pg_namespace n ON n.oid=t.relnamespace
                       JOIN pg_class t2 ON t2.oid=c.confrelid
                       JOIN pg_namespace n2 ON n2.oid=t2.relnamespace
                       CROSS JOIN LATERAL generate_subscripts(c.conkey,1) k(i)
                       JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=c.conkey[k.i]
                       JOIN pg_attribute a2 ON a2.attrelid=t2.oid AND a2.attnum=c.confkey[k.i]
                       WHERE n.nspname='tb' AND c.contype='f'
                       GROUP BY c.oid,c.confmatchtype,n2.nspname,t.relname,t2.relname""")
        foreign_keys = []
        for row in cur.fetchall():
            if row["target_schema"] != "tb" or row["confmatchtype"] not in {"s", "f"}:
                raise DomainError("BACKUP_FOREIGN_KEY_SCHEMA_INVALID", row["table_name"])
            foreign_keys.append(ForeignKey(row["table_name"], tuple(row["columns"]),
                                          row["target"], tuple(row["target_columns"]),
                                          row["confmatchtype"] == "f"))
        rows, used = {}, 0
        for table in sorted(tables):
            rows[table] = []
            with conn.cursor(name="backup_domain_rows") as stream:
                stream.execute(sql.SQL("SELECT row_to_json(t)::text AS payload FROM tb.{} t").format(sql.Identifier(table)))
                for row in stream:
                    payload = row["payload"]
                    used += len(payload.encode("utf-8"))
                    if used > max_bytes:
                        raise DomainError("BACKUP_SNAPSHOT_PLANNING_LIMIT", table)
                    rows[table].append(json.loads(payload))
        snapshot = DomainSnapshot(rows)
        # Force classification inside the same transaction before returning.
        snapshot.partition()
        return snapshot, tuple(foreign_keys)
