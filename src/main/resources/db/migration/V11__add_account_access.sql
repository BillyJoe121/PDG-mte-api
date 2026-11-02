ALTER TABLE professor ADD COLUMN IF NOT EXISTS auth_user_id UUID;
ALTER TABLE professor ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE professor ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITH TIME ZONE;

ALTER TABLE professor ADD CONSTRAINT uk_professor_auth_user_id UNIQUE (auth_user_id);

ALTER TABLE professor DROP CONSTRAINT IF EXISTS ck_professor_access_role;
ALTER TABLE professor ADD CONSTRAINT ck_professor_access_role
    CHECK (access_role IN ('ADMIN', 'MANAGER', 'CONTRIBUTOR', 'VISITOR'));
