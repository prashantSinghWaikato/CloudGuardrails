CREATE TABLE account_scan_run (
    id BIGSERIAL PRIMARY KEY,
    cloud_account_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    status VARCHAR(64),
    message VARCHAR(1024),
    events_seen INTEGER,
    events_ingested INTEGER,
    duplicates_skipped INTEGER,
    violations_created INTEGER,
    posture_findings_created INTEGER,
    CONSTRAINT fk_account_scan_run_cloud_account
        FOREIGN KEY (cloud_account_id) REFERENCES cloud_account (id),
    CONSTRAINT fk_account_scan_run_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id)
);

CREATE INDEX idx_account_scan_run_account_started
    ON account_scan_run (cloud_account_id, started_at DESC);
