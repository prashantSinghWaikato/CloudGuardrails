ALTER TABLE cloud_account ADD COLUMN name VARCHAR(255);

UPDATE cloud_account
SET name = CONCAT(provider, ' ', account_id)
WHERE name IS NULL;

ALTER TABLE cloud_account
ALTER COLUMN name SET NOT NULL;

CREATE UNIQUE INDEX uk_cloud_account_org_name
    ON cloud_account (organization_id, name);

CREATE UNIQUE INDEX uk_cloud_account_org_account_id
    ON cloud_account (organization_id, account_id);
