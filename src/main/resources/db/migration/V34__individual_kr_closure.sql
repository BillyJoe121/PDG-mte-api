CREATE TABLE key_result_closure (
 id BIGSERIAL PRIMARY KEY,
 key_result_id BIGINT NOT NULL UNIQUE REFERENCES key_result(id),
 cycle_id BIGINT NOT NULL REFERENCES strategic_cycle(id),
 report_id BIGINT NOT NULL REFERENCES school_final_report(id),
 rector_review_id BIGINT NOT NULL REFERENCES rector_review(id),
 actor_id BIGINT NOT NULL REFERENCES professor(id),
 reason TEXT NOT NULL,
 command_key VARCHAR(128) NOT NULL,
 request_fingerprint VARCHAR(64) NOT NULL,
 closed_version BIGINT NOT NULL,
 definition_snapshot JSONB NOT NULL,
 capture_snapshot JSONB NOT NULL,
 closed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK (closed_version > 0 AND LENGTH(TRIM(reason)) > 0)
);
