CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(1024) NOT NULL,
    severity VARCHAR(255),
    resource_id VARCHAR(512),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    organization_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    violation_id BIGINT,
    remediation_id BIGINT,
    CONSTRAINT fk_notification_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_notification_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notification_violation
        FOREIGN KEY (violation_id) REFERENCES violation (id),
    CONSTRAINT fk_notification_remediation
        FOREIGN KEY (remediation_id) REFERENCES remediation (id)
);

CREATE INDEX idx_notification_user_read_created ON notification (user_id, read, created_at DESC);
CREATE INDEX idx_notification_org_created ON notification (organization_id, created_at DESC);
