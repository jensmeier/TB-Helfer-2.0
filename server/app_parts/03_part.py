def system_snapshot(settings=None):
    settings=settings or get_system_settings()
    warning_days=int(settings.get("expiry_warning_days") or 30)
    counts=q(
        """SELECT
          (SELECT count(*) FROM tb.companies WHERE status='ACTIVE') companies,
          (SELECT count(*) FROM tb.users WHERE role='USER') users,
          (SELECT count(*) FROM tb.users WHERE role='USER' AND status='ACTIVE'
             AND (access_valid_until IS NULL OR access_valid_until>now())) active_users,
          (SELECT count(*) FROM tb.users WHERE role='USER' AND status='DISABLED') disabled_users,
          (SELECT count(*) FROM tb.users WHERE role='USER' AND status='ACTIVE'
             AND access_valid_until IS NOT NULL AND access_valid_until<=now()) expired_users,
          (SELECT count(*) FROM tb.users WHERE role='USER' AND status='ACTIVE'
             AND access_valid_until>now() AND access_valid_until<=now()+make_interval(days => %s)) expiring_users,
          (SELECT count(*) FROM tb.devices WHERE revoked_at IS NULL) active_devices,
          (SELECT count(*) FROM tb.devices WHERE revoked_at IS NOT NULL) revoked_devices,
          (SELECT count(*) FROM tb.routes WHERE deleted_at IS NULL) routes,
          (SELECT count(*) FROM tb.stations WHERE deleted_at IS NULL) stations,
          (SELECT count(*) FROM tb.infrastructure_objects WHERE deleted_at IS NULL) objects,
          (SELECT count(*) FROM tb.worksites) worksites,
          (SELECT count(*) FROM tb.source_files) files,
          (SELECT count(*) FROM tb.app_releases) releases,
          (SELECT COALESCE(sum(file_size_bytes),0) FROM tb.source_files) tracked_file_bytes,
          (SELECT max(last_seen_at) FROM tb.devices) last_device_seen,
          (SELECT max(last_sync_at) FROM tb.device_sync_state) last_sync,
          (SELECT pg_database_size(current_database())) db_bytes""",
        (warning_days,),
    )[0]
    schema=q("SELECT version,name,applied_at FROM tb.schema_versions ORDER BY version DESC LIMIT 1")[0]
    last_backup=q("SELECT * FROM tb.backup_runs ORDER BY started_at DESC LIMIT 1")
    last_report=q("SELECT * FROM tb.system_report_runs ORDER BY started_at DESC LIMIT 1")
    recent_audit=q("SELECT id,action,entity_type,created_at,details FROM tb.audit_log ORDER BY created_at DESC LIMIT 12")
    audit_7d=q("SELECT count(*) n FROM tb.audit_log WHERE created_at>=now()-interval '7 days'")[0]["n"]
    failed_7d=q("SELECT count(*) n FROM tb.audit_log WHERE created_at>=now()-interval '7 days' AND action ILIKE '%FAIL%'")[0]["n"]
    expiring=q(
        """SELECT full_name,company,email,access_valid_until,
                  CEIL(EXTRACT(EPOCH FROM (access_valid_until-now()))/86400.0)::int AS days_left
           FROM tb.users
           WHERE role='USER' AND status='ACTIVE' AND access_valid_until>now()
             AND access_valid_until<=now()+make_interval(days => %s)
           ORDER BY access_valid_until""",
        (warning_days,),
    )
    storage=filesystem_usage(STORAGE_ROOT)
    backup_storage=filesystem_usage(BACKUP_ROOT)
    warnings=[]
    if counts["expired_users"]:
        warnings.append(f"{counts['expired_users']} Benutzer abgelaufen")
    if counts["expiring_users"]:
        warnings.append(f"{counts['expiring_users']} Benutzer laufen innerhalb {warning_days} Tagen ab")
    limit=int(settings.get("storage_warning_percent") or 85)
    if storage.get("available") and storage.get("percent",0)>=limit:
        warnings.append(f"TB-Helfer-Speicher zu {storage['percent']:.1f}% belegt")
    if settings.get("backup_enabled"):
        lb=last_backup[0] if last_backup else None
        if not lb or lb.get("status") != "SUCCESS":
            warnings.append("Kein erfolgreiches Datenbankbackup vorhanden")
        elif lb.get("finished_at") and lb["finished_at"] < datetime.now(timezone.utc)-timedelta(hours=36):
            warnings.append("Letztes Datenbankbackup ist älter als 36 Stunden")
    return {
        "settings":settings,"counts":counts,"schema":schema,
        "storage":storage,"backup_storage":backup_storage,
        "last_backup":last_backup[0] if last_backup else None,
        "last_report":last_report[0] if last_report else None,
        "recent_audit":recent_audit,"audit_7d":audit_7d,"failed_7d":failed_7d,
        "expiring":expiring,"companies":company_system_stats(),"warnings":warnings,
    }


def parse_recipients(raw):
    parts=[]
    for item in (raw or "").replace(";",",").replace("\n",",").split(","):
        item=item.strip()
        if item and "@" in item and item not in parts:
            parts.append(item)
    return parts


def smtp_status():
    host=os.getenv("SMTP_HOST","").strip()
    sender=os.getenv("SMTP_FROM","").strip()
    return {
        "configured":bool(host and sender),
        "host":host,
        "port":int(os.getenv("SMTP_PORT","587")),
        "user_set":bool(os.getenv("SMTP_USER","").strip()),
        "password_set":bool(os.getenv("SMTP_PASSWORD","").strip()),
        "from":sender,
        "starttls":os.getenv("SMTP_STARTTLS","true").strip().lower() in ("1","true","yes","on"),
        "ssl":os.getenv("SMTP_SSL","false").strip().lower() in ("1","true","yes","on"),
    }


def send_email(subject, body, recipients):
    cfg=smtp_status()
    if not cfg["configured"]:
        raise RuntimeError("SMTP_HOST und SMTP_FROM fehlen in der .env")
    recipients=parse_recipients(recipients) if isinstance(recipients,str) else list(recipients)
    if not recipients:
        raise RuntimeError("Kein gültiger E-Mail-Empfänger eingetragen")
    msg=EmailMessage()
    msg["Subject"]=subject
    msg["From"]=cfg["from"]
    msg["To"]=', '.join(recipients)
    msg.set_content(body)
    user=os.getenv("SMTP_USER","").strip()
    password=os.getenv("SMTP_PASSWORD","")
    context=ssl.create_default_context()
    if cfg["ssl"]:
        with smtplib.SMTP_SSL(cfg["host"],cfg["port"],timeout=20,context=context) as server:
            if user:
                server.login(user,password)
            server.send_message(msg)
    else:
        with smtplib.SMTP(cfg["host"],cfg["port"],timeout=20) as server:
            server.ehlo()
            if cfg["starttls"]:
                server.starttls(context=context); server.ehlo()
            if user:
                server.login(user,password)
            server.send_message(msg)


def build_report_text(snapshot):
    s=snapshot; c=s["counts"]; st=s["storage"]
    lines=[
        "TB-Helfer Systemstatus",
        datetime.now(timezone.utc).astimezone(ZoneInfo(s["settings"].get("report_timezone") or "Europe/Berlin")).strftime("%d.%m.%Y %H:%M"),
        "",
        f"Server/API: OK · Version {SERVER_VERSION}",
        f"PostgreSQL: OK · Schema {s['schema']['version']} / {s['schema']['name']}",
        f"Datenbankgröße: {human_bytes(c['db_bytes'])}",
        "",
        "Benutzer / Firmen",
        f"Firmen: {c['companies']}",
        f"Benutzer: {c['users']} · aktiv {c['active_users']} · abgelaufen {c['expired_users']} · deaktiviert {c['disabled_users']}",
        f"Bald fällig: {c['expiring_users']}",
        f"Geräte: {c['active_devices']} aktiv · {c['revoked_devices']} freigegeben/gesperrt",
        "",
        "Daten",
        f"Strecken: {c['routes']} · Bahnhöfe: {c['stations']} · Objekte: {c['objects']}",
        f"Baustellen: {c['worksites']} · Originaldateien: {c['files']} ({human_bytes(c['tracked_file_bytes'])})",
        f"App-Versionen: {c['releases']}",
    ]
    if st.get("available"):
        lines += [f"NAS/TB-Helfer Speicher: {human_bytes(st['used'])} belegt / {human_bytes(st['free'])} frei ({st['percent']:.1f}% belegt)"]
    lb=s.get("last_backup")
    if lb:
        lines += ["",f"Letztes DB-Backup: {lb['status']} · {lb['started_at'].astimezone(ZoneInfo(s['settings'].get('report_timezone') or 'Europe/Berlin')).strftime('%d.%m.%Y %H:%M')} · {human_bytes(lb.get('file_size_bytes'))}"]
    else:
        lines += ["","Letztes DB-Backup: noch keines"]
    if s["companies"]:
        lines += ["","Je Firma:"]
        for co in s["companies"]:
            if co["user_count"] or co["worksite_count"] or co["file_count"]:
                lines.append(f"- {co['name']}: {co['user_count']} Nutzer, {co['active_device_count']} Geräte, {co['worksite_count']} Baustellen, {co['file_count']} Dateien ({human_bytes(co['file_bytes'])})")
    if s["warnings"]:
        lines += ["","WARNUNGEN:"]+[f"- {w}" for w in s["warnings"]]
    else:
        lines += ["","Warnungen: keine"]
    return "\n".join(lines)+"\n"


def record_report_run(kind, trigger, subject, recipients, created_by=None):
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """INSERT INTO tb.system_report_runs(report_kind,trigger_type,status,subject,recipient_count,created_by)
               VALUES (%s,%s,'RUNNING',%s,%s,%s) RETURNING id""",
            (kind,trigger,subject,len(recipients),created_by),
        )
        rid=cur.fetchone()["id"]; conn.commit(); return rid


def send_system_report(kind="STATUS", trigger="MANUAL", created_by=None):
    settings=get_system_settings()
    recipients=parse_recipients(settings.get("report_recipients"))
    snapshot=system_snapshot(settings)
    subject=("[TEST] " if kind=="TEST" else "")+"TB-Helfer Systemstatus"
    if snapshot["warnings"]:
        subject+=(f" · {len(snapshot['warnings'])} Warnung(en)")
    rid=record_report_run(kind,trigger,subject,recipients,created_by)
    try:
        send_email(subject,build_report_text(snapshot),recipients)
        with db() as conn, conn.cursor() as cur:
            cur.execute("UPDATE tb.system_report_runs SET status='SUCCESS',finished_at=now() WHERE id=%s",(rid,))
            if kind=="STATUS":
                cur.execute("UPDATE tb.system_settings SET last_report_at=now(),updated_at=now() WHERE id=1")
            conn.commit()
        return True,None
    except Exception as exc:
        with db() as conn, conn.cursor() as cur:
            cur.execute("UPDATE tb.system_report_runs SET status='FAILED',finished_at=now(),error_message=%s WHERE id=%s",(str(exc)[:2000],rid))
            conn.commit()
        return False,str(exc)


def cleanup_old_database_backups(retention_days):
    cutoff=datetime.now(timezone.utc)-timedelta(days=int(retention_days))
    folder=BACKUP_ROOT / "database"
    if not folder.exists():
        return
    for path in folder.glob("tb_helfer_*.dump"):
        try:
            mtime=datetime.fromtimestamp(path.stat().st_mtime,tz=timezone.utc)
            if mtime < cutoff:
                path.unlink()
        except Exception:
            pass


def run_database_backup(trigger="MANUAL", created_by=None):
    settings=get_system_settings()
    folder=BACKUP_ROOT / "database"
    folder.mkdir(parents=True,exist_ok=True)
    filename="tb_helfer_"+datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")+".dump"
    output=folder / filename
    with db() as conn, conn.cursor() as cur:
        cur.execute(
            """INSERT INTO tb.backup_runs(backup_type,trigger_type,status,storage_path,created_by)
               VALUES ('DATABASE',%s,'RUNNING',%s,%s) RETURNING id""",
            (trigger,str(output),created_by),
        )
        run_id=cur.fetchone()["id"]
        cur.execute("UPDATE tb.system_settings SET last_backup_attempt_at=now(),updated_at=now() WHERE id=1")
        conn.commit()
    env=os.environ.copy(); env["PGPASSWORD"]=os.environ.get("DB_PASSWORD","")
    cmd=[
        "pg_dump","--format=custom","--no-owner","--no-privileges",
        "--host",os.getenv("DB_HOST","tb-helfer-db"),"--port",os.getenv("DB_PORT","5432"),
        "--username",os.getenv("DB_USER","tb_helfer"),"--file",str(output),os.getenv("DB_NAME","tb_helfer")
    ]
    try:
        result=subprocess.run(cmd,env=env,capture_output=True,text=True,timeout=1800,check=False)
        if result.returncode != 0:
            raise RuntimeError((result.stderr or result.stdout or f"pg_dump Exit {result.returncode}").strip())
        size=output.stat().st_size
        with db() as conn, conn.cursor() as cur:
            cur.execute("UPDATE tb.backup_runs SET status='SUCCESS',file_size_bytes=%s,finished_at=now() WHERE id=%s",(size,run_id))
            cur.execute("UPDATE tb.system_settings SET last_backup_at=now(),updated_at=now() WHERE id=1")
            conn.commit()
        cleanup_old_database_backups(settings.get("backup_retention_days") or 30)
        return True,str(output)
    except Exception as exc:
        try:
            if output.exists() and output.stat().st_size==0:
                output.unlink()
        except Exception:
            pass
        with db() as conn, conn.cursor() as cur:
            cur.execute("UPDATE tb.backup_runs SET status='FAILED',finished_at=now(),error_message=%s WHERE id=%s",(str(exc)[:2000],run_id))
            conn.commit()
        return False,str(exc)


def scheduled_moment(settings, kind, now_utc):
    tz=ZoneInfo(settings.get("report_timezone") or "Europe/Berlin")
    now=now_utc.astimezone(tz)
    if kind=="BACKUP":
        return now.replace(hour=int(settings.get("backup_hour") or 3),minute=0,second=0,microsecond=0)
    hour=int(settings.get("report_hour") or 8)
    freq=settings.get("report_frequency") or "WEEKLY"
    if freq=="DAILY":
        return now.replace(hour=hour,minute=0,second=0,microsecond=0)
    if freq=="WEEKLY":
        monday=(now-timedelta(days=now.weekday())).date()
        d=monday+timedelta(days=int(settings.get("report_weekday") or 0))
        return datetime(d.year,d.month,d.day,hour,tzinfo=tz)
    day=min(int(settings.get("report_monthday") or 1),28)
    return datetime(now.year,now.month,day,hour,tzinfo=tz)


def run_scheduled_jobs():
    settings=get_system_settings(); now=datetime.now(timezone.utc)
    if settings.get("backup_enabled"):
        scheduled=scheduled_moment(settings,"BACKUP",now)
        last=settings.get("last_backup_attempt_at")
        last_local=last.astimezone(scheduled.tzinfo) if last else None
        if now.astimezone(scheduled.tzinfo)>=scheduled and (last_local is None or last_local<scheduled):
            run_database_backup("SCHEDULED",None)
            settings=get_system_settings()
    if settings.get("report_enabled"):
        scheduled=scheduled_moment(settings,"REPORT",now)
        last=settings.get("last_report_attempt_at")
        last_local=last.astimezone(scheduled.tzinfo) if last else None
        if now.astimezone(scheduled.tzinfo)>=scheduled and (last_local is None or last_local<scheduled):
            with db() as conn, conn.cursor() as cur:
                cur.execute("UPDATE tb.system_settings SET last_report_attempt_at=now(),updated_at=now() WHERE id=1")
                conn.commit()
            send_system_report("STATUS","SCHEDULED",None)
    # Temporäre Baustellendaten erst nach Ablauf und bestätigter Verteilung bereinigen.
    cleanup_collab_temp_data()


def scheduler_loop():
    while not _scheduler_stop.wait(SCHEDULER_INTERVAL_SECONDS):
        try:
            run_scheduled_jobs()
        except Exception:
            pass


@app.on_event("startup")
def start_system_scheduler():
    global _scheduler_thread
    _scheduler_stop.clear()
    if _scheduler_thread is None or not _scheduler_thread.is_alive():
        _scheduler_thread=threading.Thread(target=scheduler_loop,name="tb-system-scheduler",daemon=True)
        _scheduler_thread.start()


@app.on_event("shutdown")
def stop_system_scheduler():
    _scheduler_stop.set()


def admin_count():
    return q("SELECT count(*) n FROM tb.users WHERE role='ADMIN' AND status='ACTIVE'")[0]["n"]

def current_admin(request):
    s=ser()
    token=request.cookies.get(COOKIE)
    if not s or not token: return None
    try:
        data=s.loads(token,max_age=MAX_AGE)
    except (BadSignature,SignatureExpired):
        return None
    rows=q("SELECT id,full_name,email,role,status FROM tb.users WHERE id=%s AND role='ADMIN' AND status='ACTIVE'",(data.get("uid"),))
    if not rows: return None
    rows[0]["csrf"]=data.get("csrf","")
    return rows[0]

def require_admin(request):
    a=current_admin(request)
    if not a: raise HTTPException(401)
    return a

def ctx(request, admin=None, **kw):
    return {"request":request,"admin":admin,**kw}

def login_response(uid):
    s=ser()
    if not s: raise HTTPException(503,"ADMIN_SESSION_SECRET fehlt")
    csrf=secrets.token_urlsafe(24)
    token=s.dumps({"uid":str(uid),"csrf":csrf})
    r=RedirectResponse("/admin",303)
    r.set_cookie(COOKIE,token,max_age=MAX_AGE,httponly=True,secure=os.getenv("ADMIN_COOKIE_SECURE","false").strip().lower() in ("1","true","yes","on"),samesite="strict",path="/admin")
    return r

def check_csrf(admin,data):
    if not admin.get("csrf") or not hmac.compare_digest(admin["csrf"],data.get("csrf","")):
        raise HTTPException(403,"Ungültiges CSRF-Token")

@app.exception_handler(HTTPException)
async def errors(request,exc):
    if request.url.path.startswith("/admin") and exc.status_code==401:
        return RedirectResponse("/admin/login",303)
    return JSONResponse({"detail":exc.detail},status_code=exc.status_code)

@app.get("/health")
def health():
    return {"status":"ok","service":"tb-helfer-api","version":SERVER_VERSION}

@app.get("/health/db")
def health_db():
    r=q("SELECT version,name FROM tb.schema_versions ORDER BY version DESC LIMIT 1")[0]
    return {"status":"ok","database":"tb_helfer","schema_version":r["version"],"schema_name":r["name"]}


