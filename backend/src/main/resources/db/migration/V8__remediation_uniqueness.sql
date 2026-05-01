DELETE FROM remediation r
USING remediation newer
WHERE r.violation_id = newer.violation_id
  AND (
    r.created_at < newer.created_at
    OR (r.created_at = newer.created_at AND r.id < newer.id)
    OR (r.created_at IS NULL AND newer.created_at IS NOT NULL)
  );

CREATE UNIQUE INDEX uk_remediation_violation_id
    ON remediation (violation_id);
