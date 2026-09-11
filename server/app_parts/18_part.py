# Schema-11-Erweiterung: persoenliche Kontaktdaten je Benutzer.
# Firmenstammdaten bleiben getrennt in tb.company_profiles.

class UserContactProfileUpdate(BaseModel):
    email: str | None = Field(default=None, max_length=320)
    phone: str | None = Field(default=None, max_length=100)


def _user_profile_schema_ready(cur):
    cur.execute(
        """SELECT
             EXISTS (
               SELECT 1 FROM information_schema.columns
               WHERE table_schema='tb' AND table_name='users' AND column_name='phone'
             ) AS has_phone,
             EXISTS (
               SELECT 1 FROM information_schema.columns
               WHERE table_schema='tb' AND table_name='users' AND column_name='profile_version'
             ) AS has_profile_version"""
    )
    row=cur.fetchone() or {}
    return bool(row.get("has_phone")) and bool(row.get("has_profile_version"))


def _normalize_user_email(value):
    text=(value or "").strip()
    if not text:
        return None
    if "@" not in text or any(ch.isspace() for ch in text):
        raise HTTPException(400,"USER_EMAIL_INVALID")
    return text


def _normalize_user_phone(value):
    text=(value or "").strip()
    return text or None


def _user_profile_payload(row):
    return {
        "user_id":str(row["id"]),
        "full_name":row.get("full_name"),
        "email":row.get("email"),
        "phone":row.get("phone"),
        "profile_version":int(row.get("profile_version") or 1),
        "updated_at":row.get("updated_at"),
    }


@app.get("/api/v1/user/profile")
def user_contact_profile_get(authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    with db() as conn, conn.cursor() as cur:
        if not _user_profile_schema_ready(cur):
            raise HTTPException(409,"USER_PROFILE_SCHEMA_REQUIRED")
        cur.execute(
            """SELECT id,full_name,email,phone,profile_version,updated_at
               FROM tb.users WHERE id=%s""",
            (d["user_id"],),
        )
        row=cur.fetchone()
        if not row:
            raise HTTPException(404,"USER_NOT_FOUND")
        return {"profile":_user_profile_payload(row)}


@app.put("/api/v1/user/profile")
def user_contact_profile_put(req:UserContactProfileUpdate,
                             authorization: str | None = Header(default=None)):
    d=require_device(authorization)
    email=_normalize_user_email(req.email)
    phone=_normalize_user_phone(req.phone)
    try:
        with db() as conn, conn.cursor() as cur:
            if not _user_profile_schema_ready(cur):
                raise HTTPException(409,"USER_PROFILE_SCHEMA_REQUIRED")
            cur.execute(
                """UPDATE tb.users
                   SET email=%s,
                       phone=%s,
                       profile_version=profile_version+1,
                       updated_at=now()
                   WHERE id=%s
                   RETURNING id,full_name,email,phone,profile_version,updated_at""",
                (email,phone,d["user_id"]),
            )
            row=cur.fetchone()
            if not row:
                raise HTTPException(404,"USER_NOT_FOUND")
            conn.commit()
    except UniqueViolation:
        raise HTTPException(409,"USER_EMAIL_ALREADY_USED")

    audit_event(
        "USER_PROFILE_UPDATED",
        d["user_id"],
        d["device_id"],
        "USER",
        d["user_id"],
        {"email_changed":True,"phone_changed":True},
    )
    return {"profile":_user_profile_payload(row)}
