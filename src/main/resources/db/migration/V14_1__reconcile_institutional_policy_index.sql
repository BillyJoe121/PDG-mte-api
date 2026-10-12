-- V11 is the canonical versioned policy schema. V15's optional catalog index
-- uses policy_code; add its navigation alias before V15 without replacing rows.
ALTER TABLE institutional_policy ADD COLUMN IF NOT EXISTS policy_code VARCHAR(100);
UPDATE institutional_policy SET policy_code=policy_key WHERE policy_code IS NULL;
