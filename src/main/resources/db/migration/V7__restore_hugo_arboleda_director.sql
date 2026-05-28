INSERT INTO department (name, description, school_id, external_department_id)
SELECT 'Dirección TDI', 'Direccion estrategica de la Escuela TDI.', s.id, 4
FROM school s
WHERE lower(s.name) = lower('Escuela TDI')
  AND NOT EXISTS (
      SELECT 1
      FROM department d
      WHERE lower(d.name) IN (lower('Dirección TDI'), lower('DirecciÃ³n TDI'))
  );

UPDATE department
SET name = 'Dirección TDI',
    description = 'Direccion estrategica de la Escuela TDI.',
    external_department_id = 4
WHERE lower(name) = lower('DirecciÃ³n TDI');

INSERT INTO position (name, description)
SELECT 'Director Escuela TDI', 'Responsable directivo de la Escuela TDI.'
WHERE NOT EXISTS (
    SELECT 1
    FROM position p
    WHERE lower(p.name) = lower('Director Escuela TDI')
);

UPDATE professor p
SET name = 'Hugo Arboleda',
    email = 'hugo.arboleda@icesi.edu.co',
    department_id = d.id
FROM department d
WHERE lower(d.name) = lower('Dirección TDI')
  AND (lower(p.email) = lower('ana.rojas@icesi.edu.co') OR lower(p.name) = lower('Ana Maria Rojas'))
  AND NOT EXISTS (
      SELECT 1
      FROM professor existing
      WHERE lower(existing.email) = lower('hugo.arboleda@icesi.edu.co')
        AND existing.id <> p.id
  );

INSERT INTO professor (name, email, department_id)
SELECT 'Hugo Arboleda', 'hugo.arboleda@icesi.edu.co', d.id
FROM department d
WHERE lower(d.name) = lower('Dirección TDI')
  AND NOT EXISTS (
      SELECT 1
      FROM professor p
      WHERE lower(p.email) = lower('hugo.arboleda@icesi.edu.co')
  );

DELETE FROM teacher_position tp
USING professor prof, position pos
WHERE tp.teacher_id = prof.id
  AND tp.position_id = pos.id
  AND lower(prof.email) = lower('hugo.arboleda@icesi.edu.co')
  AND lower(pos.name) = lower('Director de Departamento');

DELETE FROM teacher_position tp
USING professor prof, position pos
WHERE tp.teacher_id = prof.id
  AND tp.position_id = pos.id
  AND lower(prof.email) = lower('ana.rojas@icesi.edu.co')
  AND lower(pos.name) IN (lower('Director de Departamento'), lower('Director Escuela TDI'));

INSERT INTO teacher_position (position_id, teacher_id, is_active)
SELECT pos.id, prof.id, true
FROM position pos
JOIN professor prof ON lower(prof.email) = lower('hugo.arboleda@icesi.edu.co')
WHERE lower(pos.name) = lower('Director Escuela TDI')
  AND NOT EXISTS (
      SELECT 1
      FROM teacher_position tp
      WHERE tp.position_id = pos.id
        AND tp.teacher_id = prof.id
  );
