package co.edu.icesi.pdg.mte.config;

import co.edu.icesi.pdg.mte.catalog.*;
import co.edu.icesi.pdg.mte.people.*;
import co.edu.icesi.pdg.mte.strategy.World;
import co.edu.icesi.pdg.mte.strategy.WorldRepository;

import java.time.LocalDate;

/**
 * Datos de demo de catalogos base y personas: unidades, periodos, escuela, departamentos, mundo, roles, cargos y profesores.
 */
final class SeedCatalogData {
    static final String COMPUTING_DEPARTMENT = "Departamento de Computaci\u00f3n y Sistemas inteligentes.";
    static final String DESIGN_DEPARTMENT = "Departamento de Dise\u00f1o e Innovaci\u00f3n";
    static final String SCIENCES_DEPARTMENT = "Departamento de Ciencias F\u00edsicas y Exactas.";

    private SeedCatalogData() {
    }

    static void seedUnits(MeasurementUnitRepository repository) {
        createUnit(repository, "Porcentaje", MeasurementUnitType.PORCENTAJE, "Avance o cumplimiento expresado de 0 a 100.");
        createUnit(repository, "Numero", MeasurementUnitType.NUMERICA, "Conteo numerico simple.");
        createUnit(repository, "Si/No", MeasurementUnitType.BOOLEANA, "Indicador booleano de cumplimiento.");
        createUnit(repository, "Indice", MeasurementUnitType.NUMERICA, "Puntaje compuesto normalizado.");
    }

    private static void createUnit(MeasurementUnitRepository repository, String name, MeasurementUnitType type, String description) {
        if (repository.existsByNameIgnoreCase(name)) {
            return;
        }
        MeasurementUnit unit = new MeasurementUnit();
        unit.setName(name);
        unit.setType(type);
        unit.setDescription(description);
        repository.save(unit);
    }

    static void seedPeriods(AcademicPeriodRepository repository) {
        createPeriod(repository, "2026-1", LocalDate.of(2026, 1, 15), LocalDate.of(2026, 6, 30), PeriodStatus.ACTIVO);
        createPeriod(repository, "2026-2", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 15), PeriodStatus.PLANIFICACION);
        createPeriod(repository, "2027-1", LocalDate.of(2027, 1, 15), LocalDate.of(2027, 6, 30), PeriodStatus.PLANIFICACION);
    }

    private static void createPeriod(
            AcademicPeriodRepository repository,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            PeriodStatus status
    ) {
        if (repository.existsByNameIgnoreCase(name)) {
            return;
        }
        AcademicPeriod period = new AcademicPeriod();
        period.setName(name);
        period.setStartDate(startDate);
        period.setEndDate(endDate);
        period.setStatus(status);
        repository.save(period);
    }

    static School seedSchool(SchoolRepository repository) {
        return repository.findByNameIgnoreCase("Escuela TDI").orElseGet(() -> {
            School school = new School();
            school.setName("Escuela TDI");
            school.setDescription("Escuela de Tecnologias, Diseno e Innovacion.");
            return repository.save(school);
        });
    }

    static void seedDepartments(DepartmentRepository repository, School school) {
        createDepartment(repository, COMPUTING_DEPARTMENT, "Computacion, sistemas inteligentes, datos e inteligencia artificial.", 1L, school);
        createDepartment(repository, DESIGN_DEPARTMENT, "Diseno, innovacion, experiencia de usuario y transformacion digital.", 2L, school);
        createDepartment(repository, SCIENCES_DEPARTMENT, "Ciencias fisicas, exactas, modelacion matematica y fundamentos cuantitativos.", 3L, school);
        createDepartment(repository, "Dirección TDI", "Direccion estrategica de la Escuela TDI.", 4L, school);
        createDepartment(repository, "TI Institucional", "Administracion y soporte institucional de sistemas MTE.", 5L, school);
    }

    private static void createDepartment(DepartmentRepository repository, String name, String description, Long externalId, School school) {
        if (repository.findByNameIgnoreCase(name).isPresent()) {
            return;
        }
        Department department = new Department();
        department.setName(name);
        department.setDescription(description);
        department.setExternalDepartmentId(externalId);
        department.setSchool(school);
        repository.save(department);
    }

    static void seedWorlds(WorldRepository repository) {
        if (repository.existsByNameIgnoreCase("Mundo Institucional")) {
            return;
        }
        World world = new World();
        world.setName("Mundo Institucional");
        world.setDescription("Marco estrategico institucional de la Escuela TDI.");
        repository.save(world);
    }

    static void seedRoles(RoleRepository repository) {
        createRole(repository, "Lider", "Responsable principal de un proyecto.");
        createRole(repository, "Colaborador", "Participante del proyecto.");
        createRole(repository, "Tutor", "Acompana entregables y seguimiento academico.");
    }

    private static void createRole(RoleRepository repository, String name, String description) {
        if (repository.existsByNameIgnoreCase(name)) {
            return;
        }
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        repository.save(role);
    }

    static void seedPositions(PositionRepository repository) {
        createPosition(repository, "Profesor", "Cargo docente base.");
        createPosition(repository, "Director de Departamento", "Cargo directivo de departamento.");
        createPosition(repository, "Coordinador de Proyecto", "Responsable de seguimiento operativo.");
        createPosition(repository, "Director Escuela TDI", "Responsable directivo de la Escuela TDI.");
        createPosition(repository, "Jefa de Departamento", "Responsable de direccion departamental.");
        createPosition(repository, "Tutor de Proyectos", "Acompana la gestion y seguimiento de proyectos.");
        createPosition(repository, "Administrador", "Administrador institucional de la plataforma MTE.");
    }

    private static void createPosition(PositionRepository repository, String name, String description) {
        if (repository.existsByNameIgnoreCase(name)) {
            return;
        }
        Position position = new Position();
        position.setName(name);
        position.setDescription(description);
        repository.save(position);
    }

    static void seedProfessors(ProfessorRepository repository, DepartmentRepository departmentRepository) {
        createProfessor(repository, departmentRepository, "Roc\u00edo Segovia", "rocio.segovia@icesi.edu.co", COMPUTING_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Leonardo Bustamante", "leonardo.bustamante@icesi.edu.co", COMPUTING_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Profesor Demo", "demo.profesor@icesi.edu.co", COMPUTING_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Hugo Arboleda", "hugo.arboleda@icesi.edu.co", "Dirección TDI");
        createProfessor(repository, departmentRepository, "Carlos Mejia", "carlos.mejia@icesi.edu.co", DESIGN_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Laura Gomez", "laura.gomez@icesi.edu.co", SCIENCES_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Mariana Torres", "mariana.torres@icesi.edu.co", DESIGN_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Felipe Arango", "felipe.arango@icesi.edu.co", COMPUTING_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Valentina Ruiz", "valentina.ruiz@icesi.edu.co", DESIGN_DEPARTMENT);
        // Same initial administrator as migration V11_1: the seed wipes people, so it must recreate one ADMIN.
        createProfessor(repository, departmentRepository, "Sistemas MTE", "sistemas.mte@icesi.edu.co", "Dirección TDI", "ADMIN");
    }

    private static void createProfessor(
            ProfessorRepository repository,
            DepartmentRepository departmentRepository,
            String name,
            String email,
            String departmentName
    ) {
        createProfessor(repository, departmentRepository, name, email, departmentName, "CONTRIBUTOR");
    }

    private static void createProfessor(
            ProfessorRepository repository,
            DepartmentRepository departmentRepository,
            String name,
            String email,
            String departmentName,
            String accessRole
    ) {
        if (repository.existsByEmailIgnoreCase(email)) {
            return;
        }
        departmentRepository.findByNameIgnoreCase(departmentName).ifPresent(department -> {
            Professor professor = new Professor();
            professor.setName(name);
            professor.setEmail(email);
            professor.setDepartment(department);
            professor.setAccessRole(accessRole);
            repository.save(professor);
        });
    }

    static void seedTeacherPositions(
            TeacherPositionRepository repository,
            PositionRepository positionRepository,
            ProfessorRepository professorRepository
    ) {
        Position professorPosition = positionRepository.findByNameIgnoreCase("Profesor").orElse(null);
        Position directorPosition = positionRepository.findByNameIgnoreCase("Director Escuela TDI").orElse(null);
        Position coordinatorPosition = positionRepository.findByNameIgnoreCase("Coordinador de Proyecto").orElse(null);
        Position headPosition = positionRepository.findByNameIgnoreCase("Jefa de Departamento").orElse(null);
        Position tutorPosition = positionRepository.findByNameIgnoreCase("Tutor de Proyectos").orElse(null);
        if (professorPosition == null) {
            return;
        }
        professorRepository.findAll().forEach(professor -> createTeacherPosition(repository, professorPosition, professor, true));
        professorRepository.findByEmailIgnoreCase("rocio.segovia@icesi.edu.co")
                .filter(professor -> headPosition != null)
                .ifPresent(professor -> createTeacherPosition(repository, headPosition, professor, true));
        professorRepository.findByEmailIgnoreCase("leonardo.bustamante@icesi.edu.co")
                .filter(professor -> tutorPosition != null)
                .ifPresent(professor -> createTeacherPosition(repository, tutorPosition, professor, true));
        professorRepository.findByEmailIgnoreCase("hugo.arboleda@icesi.edu.co")
                .filter(professor -> directorPosition != null)
                .ifPresent(professor -> createTeacherPosition(repository, directorPosition, professor, true));
        professorRepository.findByEmailIgnoreCase("mariana.torres@icesi.edu.co")
                .filter(professor -> coordinatorPosition != null)
                .ifPresent(professor -> createTeacherPosition(repository, coordinatorPosition, professor, true));
    }

    private static void createTeacherPosition(
            TeacherPositionRepository repository,
            Position position,
            Professor professor,
            boolean active
    ) {
        TeacherPositionId id = new TeacherPositionId(position.getId(), professor.getId());
        if (repository.existsById(id)) {
            return;
        }
        TeacherPosition teacherPosition = new TeacherPosition();
        teacherPosition.setId(id);
        teacherPosition.setPosition(position);
        teacherPosition.setTeacher(professor);
        teacherPosition.setIsActive(active);
        repository.save(teacherPosition);
    }
}
