ALTER TABLE key_result ADD COLUMN status VARCHAR(255);
UPDATE key_result SET status = 'ACTIVO' WHERE status IS NULL;
ALTER TABLE key_result ALTER COLUMN status SET NOT NULL;

ALTER TABLE objective ADD COLUMN status_before_archive VARCHAR(255);
UPDATE objective SET status = 'ACTIVO' WHERE status IS NULL;
