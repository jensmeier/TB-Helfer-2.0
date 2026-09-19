-- Allow a login name to be reused after a user was soft-deleted.
-- Active users remain unique by normalized login name.

DROP INDEX IF EXISTS tb.users_login_name_uq;

CREATE UNIQUE INDEX users_login_name_uq
ON tb.users (lower(login_name))
WHERE deleted_at IS NULL;
