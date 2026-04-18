ALTER TABLE remediation
    ADD COLUMN target_account_id VARCHAR(255),
    ADD COLUMN target_resource_id VARCHAR(512),
    ADD COLUMN last_triggered_by VARCHAR(255),
    ADD COLUMN last_trigger_source VARCHAR(64),
    ADD COLUMN last_verified_at TIMESTAMP,
    ADD COLUMN verification_status VARCHAR(64),
    ADD COLUMN verification_message VARCHAR(1024);
