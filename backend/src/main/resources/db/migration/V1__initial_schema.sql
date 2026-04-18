CREATE TABLE organization (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    created_by VARCHAR(255),
    created_at TIMESTAMP
);

CREATE TABLE cloud_account (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255),
    provider VARCHAR(255),
    region VARCHAR(255),
    created_at TIMESTAMP,
    organization_id BIGINT NOT NULL,
    access_key VARCHAR(2048),
    secret_key VARCHAR(4096),
    CONSTRAINT fk_cloud_account_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id)
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    name VARCHAR(255),
    role VARCHAR(255),
    organization_id BIGINT,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id)
);

CREATE TABLE user_cloud_accounts (
    user_id BIGINT NOT NULL,
    cloud_account_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, cloud_account_id),
    CONSTRAINT fk_user_cloud_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_cloud_accounts_cloud_account
        FOREIGN KEY (cloud_account_id) REFERENCES cloud_account (id)
);

CREATE TABLE rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    severity VARCHAR(255),
    enabled BOOLEAN,
    auto_remediation BOOLEAN,
    remediation_action VARCHAR(255),
    CONSTRAINT uk_rule_name UNIQUE (rule_name)
);

CREATE TABLE event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(255),
    resource_id VARCHAR(512),
    external_event_id VARCHAR(255),
    payload JSONB,
    timestamp TIMESTAMP,
    organization_id BIGINT NOT NULL,
    cloud_account_id BIGINT NOT NULL,
    CONSTRAINT fk_event_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_event_cloud_account
        FOREIGN KEY (cloud_account_id) REFERENCES cloud_account (id),
    CONSTRAINT uk_event_cloud_account_external_event
        UNIQUE (cloud_account_id, external_event_id)
);

CREATE TABLE violation (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(255),
    severity VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    resource_id VARCHAR(512),
    organization_id BIGINT NOT NULL,
    cloud_account_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    CONSTRAINT fk_violation_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_violation_cloud_account
        FOREIGN KEY (cloud_account_id) REFERENCES cloud_account (id),
    CONSTRAINT fk_violation_event
        FOREIGN KEY (event_id) REFERENCES event (id),
    CONSTRAINT fk_violation_rule
        FOREIGN KEY (rule_id) REFERENCES rule (id)
);

CREATE TABLE remediation (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(255),
    status VARCHAR(255),
    attempt_count INTEGER,
    executed_at TIMESTAMP,
    created_at TIMESTAMP,
    response JSONB,
    violation_id BIGINT NOT NULL,
    CONSTRAINT fk_remediation_violation
        FOREIGN KEY (violation_id) REFERENCES violation (id)
);

CREATE INDEX idx_cloud_account_org ON cloud_account (organization_id);
CREATE INDEX idx_event_org ON event (organization_id);
CREATE INDEX idx_event_account ON event (cloud_account_id);
CREATE INDEX idx_violation_org_account ON violation (organization_id, cloud_account_id);
CREATE INDEX idx_remediation_violation ON remediation (violation_id);
