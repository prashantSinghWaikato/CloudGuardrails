CREATE TABLE remediation_execution (
    id BIGSERIAL PRIMARY KEY,
    remediation_id BIGINT NOT NULL,
    action VARCHAR(255),
    trigger_source VARCHAR(64),
    triggered_by VARCHAR(255),
    attempt_number INTEGER,
    target_account_id VARCHAR(255),
    target_resource_id VARCHAR(512),
    status VARCHAR(64),
    verification_status VARCHAR(64),
    verification_message VARCHAR(1024),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    response JSONB,
    CONSTRAINT fk_remediation_execution_remediation
        FOREIGN KEY (remediation_id) REFERENCES remediation (id)
);

CREATE INDEX idx_remediation_execution_remediation_started
    ON remediation_execution (remediation_id, started_at DESC);
