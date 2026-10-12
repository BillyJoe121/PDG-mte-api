-- Serialize publication of a new version and dependent lifecycle decisions.
CREATE TABLE policy_registry_lock (policy_key VARCHAR(64) PRIMARY KEY);
INSERT INTO policy_registry_lock(policy_key) VALUES ('KR_LINK_MINIMUMS');
