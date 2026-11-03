"""Optional real PostgreSQL cursor-order regression. Isolated test DB only.
TB_SYNC_TEST_DSN must resolve to a database whose name contains 'test'.
Runs the production changes handler with a schema-translating cursor.
No production/NAS fallback. Requires psycopg; not run by local-double checks.
"""
import ast
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from threading import Event
from uuid import uuid4
import os
import psycopg
from psycopg.rows import dict_row


def main():
    dsn=os.environ.get('TB_SYNC_TEST_DSN')
    if not dsn:raise SystemExit('TB_SYNC_TEST_DSN required: isolated test database only')
    schema='shared_km_test_'+uuid4().hex
    with psycopg.connect(dsn,autocommit=True) as setup:
        name=setup.execute('SELECT current_database()').fetchone()[0]
        if 'test' not in name.lower():raise SystemExit('Refusing non-test database')
        setup.execute(f'CREATE SCHEMA {schema}')
        try:
            setup.execute(f'''CREATE TABLE {schema}.survey_catalog_history(
                change_id bigserial PRIMARY KEY,entity_type text,entity_id uuid,entity_version integer,
                operation text,after_data jsonb,changed_at timestamptz DEFAULT now())''')
            source=Path(__file__).resolve().parents[1]/'tbserver/survey_catalog.py'
            tree=ast.parse(source.read_text())
            fn=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='changes')
            fn.decorator_list=[]
            insert=f'''INSERT INTO {schema}.survey_catalog_history(entity_type,entity_id,entity_version,operation,after_data)
                VALUES ('AXIS',%s,1,'UPDATE','{{"scope":"GLOBAL"}}') RETURNING change_id'''
            for rollback in [False,True]:
                setup.execute(f'TRUNCATE {schema}.survey_catalog_history RESTART IDENTITY')
                with psycopg.connect(dsn) as first,psycopg.connect(dsn) as second:
                    assert first.execute(insert,(uuid4(),)).fetchone()[0]==1
                    assert second.execute(insert,(uuid4(),)).fetchone()[0]==2
                    second.commit()
                    entered=Event()
                    class Cursor:
                        def __init__(self,cur):self.cur=cur
                        def __enter__(self):return self
                        def __exit__(self,*args):self.cur.close()
                        def execute(self,sql,params=()):
                            if sql.startswith('LOCK TABLE'):entered.set()
                            return self.cur.execute(sql.replace('tb.',schema+'.'),params)
                        def fetchone(self):return self.cur.fetchone()
                        def fetchall(self):return self.cur.fetchall()
                    class Connection:
                        def __init__(self):self.conn=psycopg.connect(dsn,row_factory=dict_row)
                        def __enter__(self):return self
                        def __exit__(self,*args):self.conn.close()
                        def cursor(self):return Cursor(self.conn.cursor())
                    env=dict(transaction=Connection,Query=lambda **k:k.get('default'),Depends=lambda f:None,
                             current_portal_user=lambda:None,Any=object,_SCOPE_COMPANY='COMPANY',_catalog_response=lambda u,r:r)
                    exec(compile(ast.Module(body=[fn],type_ignores=[]),str(source),'exec'),env)
                    with ThreadPoolExecutor(max_workers=1) as pool:
                        job=pool.submit(env['changes'],0,200,{'company_role':'ADMIN'})
                        assert entered.wait(5),'reader did not start'
                        try:
                            # A table SHARE lock must not complete while first holds
                            # its INSERT lock, even though ID2 is already committed.
                            from concurrent.futures import TimeoutError
                            try:job.result(timeout=.15)
                            except TimeoutError:pass
                            else:raise AssertionError('cursor skipped uncommitted smaller ID')
                        finally:
                            first.rollback() if rollback else first.commit()
                        result=job.result(timeout=5)
                    expected=[2] if rollback else [1,2]
                    assert [r['change_id'] for r in result['changes']]==expected,result
                    assert result['next_after']==2 and not result['has_more']
            print('PASS: real PostgreSQL catalog cursor barrier, commit and rollback')
        finally:setup.execute(f'DROP SCHEMA {schema} CASCADE')

if __name__=='__main__':main()
