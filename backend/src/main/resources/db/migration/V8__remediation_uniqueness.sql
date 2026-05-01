CREATE TEMP TABLE remediation_dedup_map AS
SELECT duplicate.id AS duplicate_id,
       keeper.id AS keeper_id
FROM remediation duplicate
JOIN remediation keeper
  ON duplicate.violation_id = keeper.violation_id
 AND (
      duplicate.created_at < keeper.created_at
      OR (duplicate.created_at = keeper.created_at AND duplicate.id < keeper.id)
      OR (duplicate.created_at IS NULL AND keeper.created_at IS NOT NULL)
 )
LEFT JOIN remediation better_keeper
  ON keeper.violation_id = better_keeper.violation_id
 AND (
      keeper.created_at < better_keeper.created_at
      OR (keeper.created_at = better_keeper.created_at AND keeper.id < better_keeper.id)
      OR (keeper.created_at IS NULL AND better_keeper.created_at IS NOT NULL)
 )
WHERE better_keeper.id IS NULL;

UPDATE notification n
SET remediation_id = m.keeper_id
FROM remediation_dedup_map m
WHERE n.remediation_id = m.duplicate_id;

UPDATE remediation_execution re
SET remediation_id = m.keeper_id
FROM remediation_dedup_map m
WHERE re.remediation_id = m.duplicate_id;

DELETE FROM remediation r
USING remediation_dedup_map m
WHERE r.id = m.duplicate_id;

DROP TABLE remediation_dedup_map;

CREATE UNIQUE INDEX uk_remediation_violation_id
    ON remediation (violation_id);
