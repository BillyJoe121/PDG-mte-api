ALTER TABLE cycle_closure_snapshot ADD COLUMN source_report_id BIGINT REFERENCES school_final_report(id);
ALTER TABLE cycle_closure_snapshot ADD COLUMN source_review_id BIGINT REFERENCES rector_review(id);
ALTER TABLE cycle_closure_snapshot ADD COLUMN closure_origin VARCHAR(32);
ALTER TABLE cycle_closure_snapshot ADD CONSTRAINT ck_closure_source_provenance CHECK (
 closure_origin IS NULL OR
 (closure_origin IN ('FINAL_REVIEW','INDIVIDUAL_CLOSURE','LIFECYCLE_RECONCILIATION')
 AND source_report_id IS NOT NULL AND source_review_id IS NOT NULL)
);
