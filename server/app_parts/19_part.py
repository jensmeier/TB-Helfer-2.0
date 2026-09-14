# Schema-12-Erweiterung: firmenweites BTB/PDF-Logo-Layout.
# Das Original-Firmenlogo bleibt unveraendert. Dieser Teil ist vor Migration 012
# absichtlich kompatibel und antwortet dann mit COMPANY_PDF_LAYOUT_SCHEMA_REQUIRED.

COMPANY_PDF_LAYOUT_COLUMNS = {
    "pdf_logo_width_pt",
    "pdf_logo_offset_x_pt",
    "pdf_logo_offset_y_pt",
    "pdf_logo_show_company_name",
    "pdf_logo_show_company_email",
    "pdf_logo_show_company_phone",
    "pdf_logo_contacts_in_image",
    "pdf_logo_layout_version",
}


def company_pdf_layout_schema_ready(cur):
    cur.execute(
        """SELECT column_name
           FROM information_schema.columns
           WHERE table_schema='tb' AND table_name='company_profiles'
             AND column_name = ANY(%s)""",
        (list(COMPANY_PDF_LAYOUT_COLUMNS),),
    )
    found={row["column_name"] for row in cur.fetchall()}
    return COMPANY_PDF_LAYOUT_COLUMNS.issubset(found)


def company_pdf_layout_payload(row=None):
    row=row or {}
    return {
        "logo_width_pt":int(row.get("pdf_logo_width_pt") or 90),
        "offset_x_pt":int(row.get("pdf_logo_offset_x_pt") or 0),
        "offset_y_pt":int(row.get("pdf_logo_offset_y_pt") or 0),
        "show_company_name":bool(row.get("pdf_logo_show_company_name",True)),
        "show_company_email":bool(row.get("pdf_logo_show_company_email",True)),
        "show_company_phone":bool(row.get("pdf_logo_show_company_phone",True)),
        "contacts_in_image":bool(row.get("pdf_logo_contacts_in_image",False)),
        "layout_version":int(row.get("pdf_logo_layout_version") or 1),
    }


def company_pdf_layout_require_schema(cur):
    if not company_pdf_layout_schema_ready(cur):
        raise HTTPException(409,"COMPANY_PDF_LAYOUT_SCHEMA_REQUIRED")


def company_pdf_layout_bool(payload,key,default):
    if key not in payload:
        return default
    value=payload.get(key)
    if not isinstance(value,bool):
        raise HTTPException(400,"COMPANY_PDF_LAYOUT_INVALID")
    return value


@app.get("/api/v1/company/profile/pdf-logo-layout")
def mobile_company_pdf_logo_layout(authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    if d.get("company_id") is None:
        raise HTTPException(404,"COMPANY_NOT_ASSIGNED")
    with db() as conn, conn.cursor() as cur:
        company_pdf_layout_require_schema(cur)
        cur.execute(
            """SELECT pdf_logo_width_pt,pdf_logo_offset_x_pt,pdf_logo_offset_y_pt,
                      pdf_logo_show_company_name,pdf_logo_show_company_email,
                      pdf_logo_show_company_phone,pdf_logo_contacts_in_image,
                      pdf_logo_layout_version
               FROM tb.company_profiles WHERE company_id=%s""",
            (d["company_id"],),
        )
        row=cur.fetchone()
    return {"status":"ok","layout":company_pdf_layout_payload(row)}


@app.put("/api/v1/company/profile/pdf-logo-layout")
async def mobile_company_pdf_logo_layout_update(request:Request,
                                                  authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    if d.get("company_id") is None:
        raise HTTPException(404,"COMPANY_NOT_ASSIGNED")
    try:
        payload=await request.json()
    except Exception:
        raise HTTPException(400,"COMPANY_PDF_LAYOUT_JSON_INVALID")
    if not isinstance(payload,dict):
        raise HTTPException(400,"COMPANY_PDF_LAYOUT_JSON_INVALID")

    try:
        width=int(payload.get("logo_width_pt",90))
        offset_x=int(payload.get("offset_x_pt",0))
        offset_y=int(payload.get("offset_y_pt",0))
    except (TypeError,ValueError):
        raise HTTPException(400,"COMPANY_PDF_LAYOUT_INVALID")
    if width < 40 or width > 180 or offset_x < -120 or offset_x > 120 or offset_y < -60 or offset_y > 180:
        raise HTTPException(400,"COMPANY_PDF_LAYOUT_INVALID")

    show_name=company_pdf_layout_bool(payload,"show_company_name",True)
    show_email=company_pdf_layout_bool(payload,"show_company_email",True)
    show_phone=company_pdf_layout_bool(payload,"show_company_phone",True)
    contacts_in_image=company_pdf_layout_bool(payload,"contacts_in_image",False)

    with db() as conn, conn.cursor() as cur:
        company_pdf_layout_require_schema(cur)
        cur.execute("SELECT id FROM tb.companies WHERE id=%s",(d["company_id"],))
        if not cur.fetchone():
            raise HTTPException(404,"COMPANY_NOT_FOUND")
        cur.execute(
            """INSERT INTO tb.company_profiles(
                   company_id,pdf_logo_width_pt,pdf_logo_offset_x_pt,pdf_logo_offset_y_pt,
                   pdf_logo_show_company_name,pdf_logo_show_company_email,pdf_logo_show_company_phone,
                   pdf_logo_contacts_in_image,pdf_logo_layout_version,profile_version
               ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,1,1)
               ON CONFLICT (company_id) DO UPDATE SET
                   pdf_logo_width_pt=EXCLUDED.pdf_logo_width_pt,
                   pdf_logo_offset_x_pt=EXCLUDED.pdf_logo_offset_x_pt,
                   pdf_logo_offset_y_pt=EXCLUDED.pdf_logo_offset_y_pt,
                   pdf_logo_show_company_name=EXCLUDED.pdf_logo_show_company_name,
                   pdf_logo_show_company_email=EXCLUDED.pdf_logo_show_company_email,
                   pdf_logo_show_company_phone=EXCLUDED.pdf_logo_show_company_phone,
                   pdf_logo_contacts_in_image=EXCLUDED.pdf_logo_contacts_in_image,
                   pdf_logo_layout_version=tb.company_profiles.pdf_logo_layout_version+1,
                   profile_version=tb.company_profiles.profile_version+1,
                   updated_at=now()
               RETURNING pdf_logo_width_pt,pdf_logo_offset_x_pt,pdf_logo_offset_y_pt,
                         pdf_logo_show_company_name,pdf_logo_show_company_email,
                         pdf_logo_show_company_phone,pdf_logo_contacts_in_image,
                         pdf_logo_layout_version""",
            (d["company_id"],width,offset_x,offset_y,show_name,show_email,show_phone,contacts_in_image),
        )
        row=cur.fetchone()
        conn.commit()
    audit_event(
        "COMPANY_PDF_LOGO_LAYOUT_UPDATED",
        d.get("user_id"),d.get("device_id"),"COMPANY",str(d["company_id"]),
        {"layout_version":int(row.get("pdf_logo_layout_version") or 1)},
    )
    return {"status":"ok","layout":company_pdf_layout_payload(row)}
