-- Preserve the original finding; each transition appends a history entry.
ALTER TABLE situation ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE evidence ADD COLUMN IF NOT EXISTS situation_id BIGINT REFERENCES situation(id);
CREATE INDEX IF NOT EXISTS idx_evidence_situation ON evidence(situation_id);
