-- ============================================================================
-- V19: Expand E9 Operations, Parallel Monitoring Branches and Documented Execution
-- Stories: HT-075 to HT-094
-- Invariants: AD-1, AD-2, AD-3, AD-4, AD-6, AD-7, AD-8, AD-10, AD-11, AD-12, AD-13
-- ============================================================================

-- 1. Monitoring Branches (Four parallel branches per cut: RECTOR, DECANO, DIRECTOR, DEPARTMENT)
CREATE TABLE IF NOT EXISTS monitoring_branch (
  id BIGSERIAL PRIMARY KEY,
  cut_id BIGINT NOT NULL REFERENCES monitoring_cut(id) ON DELETE CASCADE,
  branch_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  as_of TIMESTAMP NOT NULL,
  department_id BIGINT REFERENCES department(id),
  department_name VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_monitoring_branch_type CHECK (branch_type IN ('RECTOR', 'DECANO', 'DIRECTOR', 'DEPARTMENT')),
  CONSTRAINT chk_monitoring_branch_status CHECK (status IN ('OPEN', 'SUBMITTED', 'REVIEWED', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_monitoring_branch_cut ON monitoring_branch(cut_id);
CREATE INDEX IF NOT EXISTS idx_monitoring_branch_dept ON monitoring_branch(department_id);

-- 2. Monitoring Branch Entries (Check-in entries and reviews within branches)
CREATE TABLE IF NOT EXISTS monitoring_entry (
  id BIGSERIAL PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES monitoring_branch(id) ON DELETE CASCADE,
  actor_email VARCHAR(255) NOT NULL,
  actor_name VARCHAR(255) NOT NULL,
  actor_role VARCHAR(64) NOT NULL,
  observation TEXT NOT NULL,
  status_result VARCHAR(32) NOT NULL DEFAULT 'NO_SITUATIONS',
  evidence_urls TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_monitoring_entry_status CHECK (status_result IN ('NO_SITUATIONS', 'WITH_SITUATIONS', 'RESOLVED'))
);

CREATE INDEX IF NOT EXISTS idx_monitoring_entry_branch ON monitoring_entry(branch_id);

-- 3. Monitoring Situations & Escalations
CREATE TABLE IF NOT EXISTS monitoring_situation (
  id BIGSERIAL PRIMARY KEY,
  entry_id BIGINT NOT NULL REFERENCES monitoring_entry(id) ON DELETE CASCADE,
  title VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  severity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
  escalation_level VARCHAR(32),
  escalated_to VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_situation_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_monitoring_situation_entry ON monitoring_situation(entry_id);

-- 4. Documented Execution Entries for Initiatives (Coexists without modifying KR progress)
CREATE TABLE IF NOT EXISTS initiative_execution_entry (
  id BIGSERIAL PRIMARY KEY,
  initiative_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  actor_email VARCHAR(255) NOT NULL,
  execution_date DATE NOT NULL,
  summary TEXT NOT NULL,
  notes TEXT,
  evidence_url VARCHAR(1024),
  status VARCHAR(32) NOT NULL DEFAULT 'RECORDED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_init_exec_entry_proj ON initiative_execution_entry(initiative_id, execution_date);

-- 5. Dependency Exceptions (Quality Gate E9)
CREATE TABLE IF NOT EXISTS dependency_exception (
  id BIGSERIAL PRIMARY KEY,
  finding_id VARCHAR(128) NOT NULL,
  cve_id VARCHAR(64) NOT NULL,
  package_name VARCHAR(255) NOT NULL,
  reason TEXT NOT NULL,
  approved_by VARCHAR(255) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dep_exception_cve ON dependency_exception(cve_id);

-- 6. Environment Configurations (Demo, Staging, Production Isolation)
CREATE TABLE IF NOT EXISTS environment_config (
  id BIGSERIAL PRIMARY KEY,
  environment_name VARCHAR(64) NOT NULL UNIQUE,
  database_identifier VARCHAR(128) NOT NULL,
  sanitized_endpoint VARCHAR(255) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_env_name CHECK (environment_name IN ('DEMO', 'STAGING', 'PRODUCTION', 'DEVELOPMENT', 'TEST'))
);

-- 7. Dev Environment Seed Runs
CREATE TABLE IF NOT EXISTS dev_environment_seed (
  id BIGSERIAL PRIMARY KEY,
  seed_name VARCHAR(128) NOT NULL,
  seed_version VARCHAR(32) NOT NULL,
  executed_by VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
  executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
