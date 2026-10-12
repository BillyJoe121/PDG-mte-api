-- Report format decisions are explicit, versioned institutional configuration.
-- This lock does not create or approve a format policy.
INSERT INTO policy_registry_lock(policy_key)
SELECT 'SCHOOL_REPORT_FORMAT'
WHERE NOT EXISTS (SELECT 1 FROM policy_registry_lock WHERE policy_key='SCHOOL_REPORT_FORMAT');
