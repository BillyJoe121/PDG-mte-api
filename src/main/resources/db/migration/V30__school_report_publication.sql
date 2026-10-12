-- Additive publication metadata. Existing report content and legacy rows remain intact.
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS transition_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS published_by BIGINT REFERENCES professor(id);
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS publication_key VARCHAR(128);
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS format_policy_id BIGINT REFERENCES institutional_policy(id);
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS format_snapshot JSONB;
