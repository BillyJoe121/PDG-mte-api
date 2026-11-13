-- Initiatives report their own progress for information only; links to key results carry no weight or type.
ALTER TABLE project_key_result_link DROP COLUMN IF EXISTS contribution_weight;
ALTER TABLE project_key_result_link DROP COLUMN IF EXISTS contribution_type;
ALTER TABLE project DROP COLUMN IF EXISTS contribution_weight;
