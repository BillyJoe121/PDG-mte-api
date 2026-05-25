INSERT INTO school (name, description)
SELECT 'Escuela TDI', 'Escuela de Tecnologias, Diseno e Innovacion.'
WHERE NOT EXISTS (SELECT 1 FROM school s WHERE lower(s.name) = lower('Escuela TDI'));

INSERT INTO department (name, description, school_id, external_department_id)
SELECT 'Departamento de Computación y Sistemas inteligentes.', 'Computacion, sistemas inteligentes, datos e inteligencia artificial.', s.id, 1
FROM school s
WHERE s.name = 'Escuela TDI'
  AND NOT EXISTS (
      SELECT 1
      FROM department d
      WHERE lower(d.name) = lower('Departamento de Computación y Sistemas inteligentes.')
  );

INSERT INTO department (name, description, school_id, external_department_id)
SELECT 'Dirección TDI', 'Direccion estrategica de la Escuela TDI.', s.id, 4
FROM school s
WHERE s.name = 'Escuela TDI'
  AND NOT EXISTS (SELECT 1 FROM department d WHERE lower(d.name) = lower('Dirección TDI'));

INSERT INTO department (name, description, school_id, external_department_id)
SELECT 'TI Institucional', 'Administracion y soporte institucional de sistemas MTE.', s.id, 5
FROM school s
WHERE s.name = 'Escuela TDI'
  AND NOT EXISTS (SELECT 1 FROM department d WHERE lower(d.name) = lower('TI Institucional'));

INSERT INTO position (name, description)
SELECT 'Director Escuela TDI', 'Responsable directivo de la Escuela TDI.'
WHERE NOT EXISTS (SELECT 1 FROM position p WHERE lower(p.name) = lower('Director Escuela TDI'));

INSERT INTO position (name, description)
SELECT 'Jefa de Departamento', 'Responsable de direccion departamental.'
WHERE NOT EXISTS (SELECT 1 FROM position p WHERE lower(p.name) = lower('Jefa de Departamento'));

INSERT INTO position (name, description)
SELECT 'Tutor de Proyectos', 'Acompana la gestion y seguimiento de proyectos.'
WHERE NOT EXISTS (SELECT 1 FROM position p WHERE lower(p.name) = lower('Tutor de Proyectos'));

INSERT INTO position (name, description)
SELECT 'Administrador', 'Administrador institucional de la plataforma MTE.'
WHERE NOT EXISTS (SELECT 1 FROM position p WHERE lower(p.name) = lower('Administrador'));

INSERT INTO professor (name, email, department_id)
SELECT 'Hugo Arboleda', 'hugo.arboleda@icesi.edu.co', d.id
FROM department d
WHERE lower(d.name) = lower('Dirección TDI')
  AND NOT EXISTS (SELECT 1 FROM professor p WHERE lower(p.email) = lower('hugo.arboleda@icesi.edu.co'));

INSERT INTO professor (name, email, department_id)
SELECT 'Rocío Segovia', 'rocio.segovia@icesi.edu.co', d.id
FROM department d
WHERE lower(d.name) = lower('Departamento de Computación y Sistemas inteligentes.')
  AND NOT EXISTS (SELECT 1 FROM professor p WHERE lower(p.email) = lower('rocio.segovia@icesi.edu.co'));

INSERT INTO professor (name, email, department_id)
SELECT 'Leonardo Bustamante', 'leonardo.bustamante@icesi.edu.co', d.id
FROM department d
WHERE lower(d.name) = lower('Departamento de Computación y Sistemas inteligentes.')
  AND NOT EXISTS (SELECT 1 FROM professor p WHERE lower(p.email) = lower('leonardo.bustamante@icesi.edu.co'));

INSERT INTO professor (name, email, department_id)
SELECT 'Sistemas MTE', 'sistemas.mte@icesi.edu.co', d.id
FROM department d
WHERE lower(d.name) = lower('TI Institucional')
  AND NOT EXISTS (SELECT 1 FROM professor p WHERE lower(p.email) = lower('sistemas.mte@icesi.edu.co'));

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

INSERT INTO teacher_position (position_id, teacher_id, is_active)
SELECT pos.id, prof.id, true
FROM position pos
JOIN professor prof ON lower(prof.email) = lower('rocio.segovia@icesi.edu.co')
WHERE lower(pos.name) = lower('Jefa de Departamento')
  AND NOT EXISTS (
      SELECT 1
      FROM teacher_position tp
      WHERE tp.position_id = pos.id
        AND tp.teacher_id = prof.id
  );

INSERT INTO teacher_position (position_id, teacher_id, is_active)
SELECT pos.id, prof.id, true
FROM position pos
JOIN professor prof ON lower(prof.email) = lower('leonardo.bustamante@icesi.edu.co')
WHERE lower(pos.name) = lower('Tutor de Proyectos')
  AND NOT EXISTS (
      SELECT 1
      FROM teacher_position tp
      WHERE tp.position_id = pos.id
        AND tp.teacher_id = prof.id
  );

INSERT INTO teacher_position (position_id, teacher_id, is_active)
SELECT pos.id, prof.id, true
FROM position pos
JOIN professor prof ON lower(prof.email) = lower('sistemas.mte@icesi.edu.co')
WHERE lower(pos.name) = lower('Administrador')
  AND NOT EXISTS (
      SELECT 1
      FROM teacher_position tp
      WHERE tp.position_id = pos.id
        AND tp.teacher_id = prof.id
  );
