INSERT INTO professor (name, email, department_id, access_role)
SELECT 'Sistemas MTE', 'sistemas.mte@icesi.edu.co', d.id, 'ADMIN'
FROM department d
WHERE lower(d.name) = lower('Dirección TDI')
  AND NOT EXISTS (
      SELECT 1
      FROM professor p
      WHERE lower(p.email) = lower('sistemas.mte@icesi.edu.co')
  );

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM professor WHERE access_role = 'ADMIN' AND active) THEN
        RAISE EXCEPTION 'No active administrator after migration';
    END IF;
END $$;
