-- TB-Server 1.0: device bearer credentials and single-active-device rule.
-- Immutable after application.

ALTER TABLE tb.devices
    ADD COLUMN auth_token_hash text,
    ADD COLUMN auth_token_hint text;

CREATE UNIQUE INDEX devices_auth_token_hash_uq
    ON tb.devices(auth_token_hash)
    WHERE auth_token_hash IS NOT NULL;

CREATE UNIQUE INDEX devices_one_active_per_user_uq
    ON tb.devices(user_id)
    WHERE status = 'ACTIVE' AND revoked_at IS NULL;
