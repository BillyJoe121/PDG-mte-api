ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS cut_id BIGINT REFERENCES monitoring_cut(id);
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS report_version BIGINT;
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS conclusions TEXT;
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS content_snapshot JSONB;
ALTER TABLE school_final_report ADD COLUMN IF NOT EXISTS creation_key VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS uq_school_report_cut_version ON school_final_report(cut_id,report_version);
CREATE UNIQUE INDEX IF NOT EXISTS uq_school_report_command ON school_final_report(cut_id,author_id,creation_key);
