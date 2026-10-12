-- Bind new rector reviews to the immutable report version; retain legacy cut-only reviews.
ALTER TABLE rector_review ADD COLUMN IF NOT EXISTS report_id BIGINT REFERENCES school_final_report(id);
ALTER TABLE rector_review ADD COLUMN IF NOT EXISTS report_version BIGINT;
ALTER TABLE rector_review ADD COLUMN IF NOT EXISTS actor_id BIGINT REFERENCES professor(id);
ALTER TABLE rector_review ADD COLUMN IF NOT EXISTS command_key VARCHAR(128);
ALTER TABLE rector_review ADD COLUMN IF NOT EXISTS evidence_urls JSONB NOT NULL DEFAULT '[]';
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_review_sequence ON rector_review(report_id,review_version);
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_review_command ON rector_review(report_id,actor_id,command_key);
ALTER TABLE rector_review ADD CONSTRAINT ck_versioned_rector_review CHECK (
    report_id IS NULL OR (
        report_version IS NOT NULL AND report_version > 0 AND
        actor_id IS NOT NULL AND command_key IS NOT NULL AND LENGTH(TRIM(command_key)) > 0 AND
        observations IS NOT NULL AND LENGTH(TRIM(observations)) > 0 AND
        status IN ('APPROVED','OBSERVED')
    )
);
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS finished_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS finished_by BIGINT REFERENCES professor(id);
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS finish_key VARCHAR(128);
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS final_review_id BIGINT REFERENCES rector_review(id);
ALTER TABLE school_final_report ADD CONSTRAINT ck_report_finish_metadata CHECK (
    (finished_at IS NULL AND finished_by IS NULL AND finish_key IS NULL AND final_review_id IS NULL) OR
    (finished_at IS NOT NULL AND finished_by IS NOT NULL AND finish_key IS NOT NULL AND final_review_id IS NOT NULL)
);
