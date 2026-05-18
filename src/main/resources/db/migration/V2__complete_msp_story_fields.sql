ALTER TABLE key_result ADD COLUMN name VARCHAR(255);

UPDATE key_result
SET name = COALESCE(NULLIF(metric, ''), LEFT(description, 120), 'Key Result');

ALTER TABLE key_result ALTER COLUMN name SET NOT NULL;

ALTER TABLE project_key_result_link
    ADD COLUMN contribution_type VARCHAR(255) NOT NULL DEFAULT 'DIRECTA';
