-- HT-039: no institutional approval matrix is seeded.
INSERT INTO policy_registry_lock(policy_key) VALUES ('KR_TARGET_APPROVAL');
ALTER TABLE kr_target_revision ADD COLUMN policy_id BIGINT REFERENCES institutional_policy(id);
ALTER TABLE kr_target_revision ADD COLUMN kr_version BIGINT;
ALTER TABLE kr_target_revision ADD COLUMN actor_id BIGINT REFERENCES professor(id);
ALTER TABLE kr_target_revision ADD COLUMN request_key VARCHAR(128);
ALTER TABLE kr_target_revision ADD COLUMN request_fingerprint VARCHAR(64);
ALTER TABLE kr_target_revision ADD COLUMN request_snapshot JSONB;
ALTER TABLE kr_target_revision ADD COLUMN decision_actor_id BIGINT REFERENCES professor(id);
ALTER TABLE kr_target_revision ADD COLUMN decision_reason TEXT;
ALTER TABLE kr_target_revision ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX uq_target_request_command ON kr_target_revision(actor_id,request_key) WHERE actor_id IS NOT NULL;
CREATE INDEX idx_target_request_history ON kr_target_revision(key_result_id,id DESC);
ALTER TABLE kr_target_revision ADD CONSTRAINT ck_target_governed_request CHECK (
  policy_id IS NULL OR (kr_version IS NOT NULL AND actor_id IS NOT NULL AND request_key IS NOT NULL
    AND request_fingerprint IS NOT NULL AND request_snapshot IS NOT NULL
    AND approval_status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))
);
