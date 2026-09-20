ALTER TABLE professor ADD COLUMN IF NOT EXISTS access_role VARCHAR(32) NOT NULL DEFAULT 'CONTRIBUTOR';
ALTER TABLE professor ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE professor ADD COLUMN IF NOT EXISTS last_access_at TIMESTAMP WITH TIME ZONE;

UPDATE professor
SET access_role = 'ADMIN'
WHERE lower(email) = lower('sistemas.mte@icesi.edu.co');

UPDATE professor
SET access_role = 'MANAGER'
WHERE lower(email) = lower('hugo.arboleda@icesi.edu.co');

ALTER TABLE professor ADD CONSTRAINT ck_professor_access_role
    CHECK (access_role IN ('ADMIN', 'MANAGER', 'CONTRIBUTOR'));
