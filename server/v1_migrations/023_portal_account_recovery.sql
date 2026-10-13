-- Portal recovery tokens contain hashes only; no device credentials are changed.
ALTER TABLE tb.portal_credentials ADD COLUMN verified_email text;
CREATE TABLE tb.portal_account_tokens (
 token_hash text PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES tb.users(id) ON DELETE CASCADE,
 purpose text NOT NULL CHECK(purpose IN ('VERIFY_EMAIL','RESET_PASSWORD')),
 email text,
 expires_at timestamptz NOT NULL,
 used_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX portal_account_tokens_user_idx ON tb.portal_account_tokens(user_id, purpose);
CREATE TABLE tb.portal_recovery_limits (
 bucket text PRIMARY KEY, window_start timestamptz NOT NULL DEFAULT now(), attempts integer NOT NULL DEFAULT 1
);
-- Missing read permission in the original responsible-TB built-in profile.
-- Explicit individual false overrides remain authoritative.
UPDATE tb.permission_profiles SET permissions=permissions || '{"files.view":true}'::jsonb,
 updated_at=now() WHERE profile_key='RESPONSIBLE_TB' AND NOT (permissions ? 'files.view');

CREATE FUNCTION tb.invalidate_portal_email_verification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF lower(coalesce(OLD.personal_email,'')) IS DISTINCT FROM lower(coalesce(NEW.personal_email,'')) THEN
  UPDATE tb.portal_credentials SET verified_email=NULL WHERE user_id=NEW.id;
  UPDATE tb.portal_account_tokens SET used_at=now() WHERE user_id=NEW.id AND used_at IS NULL;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER portal_email_changed AFTER UPDATE OF personal_email ON tb.users
FOR EACH ROW EXECUTE FUNCTION tb.invalidate_portal_email_verification();
