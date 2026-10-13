CREATE TABLE tb.system_schedule_runs (
    id bigserial PRIMARY KEY,
    schedule_key text NOT NULL,
    slot_key text NOT NULL,
    scheduled_for timestamptz NOT NULL,
    background_job_id uuid REFERENCES tb.background_jobs(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(schedule_key, slot_key)
);
CREATE INDEX system_schedule_runs_time_idx
    ON tb.system_schedule_runs(scheduled_for DESC, schedule_key);

INSERT INTO tb.server_settings(setting_key, value) VALUES
(
    'admin_automation',
    '{
      "email_enabled": false,
      "email_frequency": "WEEKLY",
      "email_hour": 8,
      "email_weekday": 0,
      "email_monthday": 1,
      "email_recipients": [],
      "timezone": "Europe/Berlin",
      "user_expiry_warning_days": 30,
      "storage_warning_percent": 85.0,
      "automatic_backup_enabled": false,
      "backup_kind": "DATABASE",
      "backup_hour": 4,
      "automatic_retention_enabled": false,
      "retention_hour": 5,
      "backup_retention_days": 30,
      "backup_retention_min_per_kind": 3
    }'::jsonb
)
ON CONFLICT (setting_key) DO NOTHING;
