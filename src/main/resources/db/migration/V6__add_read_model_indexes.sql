CREATE INDEX IF NOT EXISTS idx_project_status ON project (status);
CREATE INDEX IF NOT EXISTS idx_project_type ON project (type);
CREATE INDEX IF NOT EXISTS idx_project_department ON project (department_id);
CREATE INDEX IF NOT EXISTS idx_project_period_range ON project (start_period, end_period);

CREATE INDEX IF NOT EXISTS idx_objective_department ON objective (department_id);
CREATE INDEX IF NOT EXISTS idx_objective_academic_period ON objective (academic_period_id);
CREATE INDEX IF NOT EXISTS idx_objective_strategic_bet ON objective (strategic_bet_id);
CREATE INDEX IF NOT EXISTS idx_objective_goal ON objective (goal_id);

CREATE INDEX IF NOT EXISTS idx_key_result_objective ON key_result (objective_id);

CREATE INDEX IF NOT EXISTS idx_project_key_result_link_project_active
    ON project_key_result_link (project_id, active);

CREATE INDEX IF NOT EXISTS idx_project_key_result_link_key_result_active
    ON project_key_result_link (key_result_id, active);

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log (created_at);
