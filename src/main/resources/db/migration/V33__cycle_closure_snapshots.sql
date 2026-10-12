ALTER TABLE cycle_closure ADD COLUMN actor_id BIGINT REFERENCES professor(id);
ALTER TABLE cycle_closure ADD COLUMN command_key VARCHAR(128);
ALTER TABLE cycle_closure ADD COLUMN request_fingerprint VARCHAR(64);
ALTER TABLE cycle_closure ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE cycle_closure ADD COLUMN reports_snapshot JSONB;
ALTER TABLE cycle_closure_snapshot ALTER COLUMN final_manual_progress DROP NOT NULL;
ALTER TABLE cycle_closure_snapshot ADD COLUMN definition_snapshot JSONB;
CREATE UNIQUE INDEX uq_cycle_snapshot_kr ON cycle_closure_snapshot(cycle_closure_id,key_result_id);
ALTER TABLE cycle_closure ADD CONSTRAINT ck_governed_cycle_closure CHECK (
 actor_id IS NULL OR (command_key IS NOT NULL AND request_fingerprint IS NOT NULL
 AND reports_snapshot IS NOT NULL AND rector_review_id IS NOT NULL)
);
