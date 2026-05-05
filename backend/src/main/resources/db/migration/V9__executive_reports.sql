CREATE TABLE report_definition (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    report_type VARCHAR(64) NOT NULL,
    name VARCHAR(255),
    frequency VARCHAR(64),
    day_of_week INTEGER,
    scheduled_time TIME,
    time_zone VARCHAR(128),
    recipient_emails JSONB,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    last_run_at TIMESTAMP,
    CONSTRAINT fk_report_definition_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id)
);

CREATE UNIQUE INDEX uk_report_definition_org_type
    ON report_definition (organization_id, report_type);

CREATE TABLE report_run (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    report_definition_id BIGINT,
    report_type VARCHAR(64) NOT NULL,
    report_name VARCHAR(255),
    trigger_type VARCHAR(64),
    requested_by VARCHAR(255),
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    status VARCHAR(64),
    ai_provider VARCHAR(64),
    summary_text VARCHAR(12000),
    summary_data JSONB,
    email_status VARCHAR(64),
    email_recipients JSONB,
    emailed_at TIMESTAMP,
    error_message VARCHAR(2048),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_report_run_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_report_run_definition
        FOREIGN KEY (report_definition_id) REFERENCES report_definition (id)
);

CREATE INDEX idx_report_run_org_type_created
    ON report_run (organization_id, report_type, created_at DESC);

CREATE INDEX idx_report_run_definition_period
    ON report_run (report_definition_id, period_start, period_end);
