ALTER TABLE cloud_account
    ADD COLUMN monitoring_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN activation_status VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN activation_method VARCHAR(64),
    ADD COLUMN role_arn VARCHAR(512),
    ADD COLUMN external_id VARCHAR(2048),
    ADD COLUMN last_activated_at TIMESTAMP,
    ADD COLUMN last_sync_at TIMESTAMP,
    ADD COLUMN last_sync_status VARCHAR(64),
    ADD COLUMN last_sync_message VARCHAR(1024);
