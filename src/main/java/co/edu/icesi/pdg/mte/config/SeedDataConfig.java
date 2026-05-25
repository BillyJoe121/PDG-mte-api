package co.edu.icesi.pdg.mte.config;

import co.edu.icesi.pdg.mte.catalog.*;
import co.edu.icesi.pdg.mte.integration.ContributionType;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.people.*;
import co.edu.icesi.pdg.mte.project.*;
import co.edu.icesi.pdg.mte.strategy.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Configuration
public class SeedDataConfig {
    private static final String COMPUTING_DEPARTMENT = "Departamento de Computaci\u00f3n y Sistemas inteligentes.";
    private static final String TDI_DIRECTION_DEPARTMENT = "Direcci\u00f3n TDI";
    private static final String DESIGN_DEPARTMENT = "Departamento de Dise\u00f1o e Innovaci\u00f3n";
    private static final String SCIENCES_DEPARTMENT = "Departamento de Ciencias F\u00edsicas y Exactas.";
    private static final String INSTITUTIONAL_IT_DEPARTMENT = "TI Institucional";
    private static final String KR_PROSPECTS = "Priorizar 1000 prospectos con modelos analiticos para el final de Q3.";
    private static final String KR_COMPETITIVE_PROGRAMMING = "Entrenar 120 estudiantes en programacion competitiva para el final de Q3.";
    private static final String KR_POSTGRADUATE_OFFER = "Actualizar el 90% de la oferta posgradual en plataforma para el final de Q3.";
    private static final String KR_INTERACTIVE_COURSES = "Intervenir 12 cursos con recursos interactivos inteligentes para el final de Q3.";
    private static final String KR_AI_FACULTY = "Habilitar 60 profesores en entornos de IA y mejora continua para el final de Q3.";
    private static final String KR_APPLIED_CHALLENGES = "Aplicar 18 retos con participacion docente para el final de Q3.";
    private static final String KR_CONSULTING_ASSETS = "Crear 10 activos consultivos reutilizables para el final de Q3.";
    private static final String KR_APPLIED_PROTOTYPES = "Validar 8 prototipos de innovacion aplicada con aliados para el final de Q3.";
    private static final String KR_EMPLOYABILITY_CONNECTIONS = "Activar 300 conexiones de empleabilidad para el final de Q3.";
    private static final String KR_MICROCREDENTIALS = "Lanzar 15 microcredenciales para aprendizaje permanente para el final de Q3.";
    private static final String KR_DIGITAL_SERVICES = "Alcanzar un indice de satisfaccion digital de 90 en servicios de campus para el final de Q3.";
    private static final String KR_SERVICE_CARE = "Implementar 20 acciones de servicio y cuidado para el final de Q3.";

    @Bean
    @ConditionalOnProperty(prefix = "mte.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner seedData(
            JdbcTemplate jdbcTemplate,
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            SchoolRepository schoolRepository,
            DepartmentRepository departmentRepository,
            WorldRepository worldRepository,
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
            ObjectiveRepository objectiveRepository,
            KeyResultRepository keyResultRepository,
            RoleRepository roleRepository,
            PositionRepository positionRepository,
            ProfessorRepository professorRepository,
            TeacherPositionRepository teacherPositionRepository,
            ProjectRepository projectRepository,
            ProjectTeacherRepository projectTeacherRepository,
            ProjectProgressEntryRepository progressRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        return args -> {
            cleanDatabase(jdbcTemplate);

            seedUnits(unitRepository);
            seedPeriods(periodRepository);
            School school = seedSchool(schoolRepository);
            seedDepartments(departmentRepository, school);
            seedWorlds(worldRepository);
            seedRoles(roleRepository);
            seedPositions(positionRepository);
            seedProfessors(professorRepository, departmentRepository);
            seedTeacherPositions(teacherPositionRepository, positionRepository, professorRepository);
            seedStrategicHierarchy(
                    unitRepository,
                    periodRepository,
                    departmentRepository,
                    worldRepository,
                    strategicBetRepository,
                    goalRepository,
                    objectiveRepository,
                    keyResultRepository
            );
            seedProjects(
                    keyResultRepository,
                    departmentRepository,
                    professorRepository,
                    roleRepository,
                    projectRepository,
                    projectTeacherRepository,
                    progressRepository,
                    linkRepository
            );
            restoreSeedKeyResultProgress(jdbcTemplate);
        };
    }

    private void cleanDatabase(JdbcTemplate jdbcTemplate) {
        String databaseName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName()
        );
        if (databaseName != null && databaseName.toLowerCase().contains("h2")) {
            cleanH2Database(jdbcTemplate);
            return;
        }
        jdbcTemplate.execute("TRUNCATE TABLE " + quotedTableList() + " RESTART IDENTITY CASCADE");
    }

    private void cleanH2Database(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            SEED_TABLES.forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE " + quoteIdentifier(table)));
            IDENTITY_TABLES.forEach(table -> jdbcTemplate.execute(
                    "ALTER TABLE " + quoteIdentifier(table) + " ALTER COLUMN " + quoteIdentifier("id") + " RESTART WITH 1"
            ));
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private String quotedTableList() {
        return SEED_TABLES.stream()
                .map(this::quoteIdentifier)
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }

    private static final List<String> SEED_TABLES = List.of(
            "audit_log",
            "project_teacher",
            "project_progress_entry",
            "project_key_result_link",
            "project_tutor",
            "project",
            "key_result",
            "objective",
            "goal_period",
            "goal",
            "strategic_bet",
            "teacher_position",
            "professor",
            "role",
            "position",
            "department",
            "school",
            "period",
            "unit_of_measure",
            "world"
    );

    private static final List<String> IDENTITY_TABLES = List.of(
            "audit_log",
            "project_progress_entry",
            "project_key_result_link",
            "project",
            "key_result",
            "objective",
            "goal",
            "strategic_bet",
            "professor",
            "role",
            "position",
            "department",
            "school",
            "period",
            "unit_of_measure",
            "world"
    );

    private void seedUnits(MeasurementUnitRepository repository) {
        createUnit(repository, "Porcentaje", MeasurementUnitType.PORCENTAJE, "Avance o cumplimiento expresado de 0 a 100.");
        createUnit(repository, "Numero", MeasurementUnitType.NUMERICA, "Conteo numerico simple.");
        createUnit(repository, "Si/No", MeasurementUnitType.BOOLEANA, "Indicador booleano de cumplimiento.");
        createUnit(repository, "Indice", MeasurementUnitType.NUMERICA, "Puntaje compuesto normalizado.");
    }

    private void createUnit(MeasurementUnitRepository repository, String name, MeasurementUnitType type, String description) {
        if (repository.existsByNameIgnoreCase(name)) {
            return;
        }
        MeasurementUnit unit = new MeasurementUnit();
        unit.setName(name);
        unit.setType(type);
        unit.setDescription(description);
        repository.save(unit);
    }

    private void seedPeriods(AcademicPeriodRepository repository) {
        createPeriod(repository, "2026-1", LocalDate.of(2026, 1, 15), LocalDate.of(2026, 6, 30), PeriodStatus.ACTIVO);
        createPeriod(repository, "2026-2", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 15), PeriodStatus.PLANIFICACION);
        createPeriod(repository, "2027-1", LocalDate.of(2027, 1, 15), LocalDate.of(2027, 6, 30), PeriodStatus.PLANIFICACION);
    }

    private void createPeriod(
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

    private School seedSchool(SchoolRepository repository) {
        return repository.findByNameIgnoreCase("Escuela TDI").orElseGet(() -> {
            School school = new School();
            school.setName("Escuela TDI");
            school.setDescription("Escuela de Tecnologias, Diseno e Innovacion.");
            return repository.save(school);
        });
    }

    private void seedDepartments(DepartmentRepository repository, School school) {
        createDepartment(repository, COMPUTING_DEPARTMENT, "Computacion, sistemas inteligentes, datos e inteligencia artificial.", 1L, school);
        createDepartment(repository, DESIGN_DEPARTMENT, "Diseno, innovacion, experiencia de usuario y transformacion digital.", 2L, school);
        createDepartment(repository, SCIENCES_DEPARTMENT, "Ciencias fisicas, exactas, modelacion matematica y fundamentos cuantitativos.", 3L, school);
        createDepartment(repository, TDI_DIRECTION_DEPARTMENT, "Direccion estrategica de la Escuela TDI.", 4L, school);
        createDepartment(repository, INSTITUTIONAL_IT_DEPARTMENT, "Administracion y soporte institucional de sistemas MTE.", 5L, school);
    }

    private void createDepartment(DepartmentRepository repository, String name, String description, Long externalId, School school) {
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

    private void seedWorlds(WorldRepository repository) {
        if (repository.existsByNameIgnoreCase("Mundo Institucional")) {
            return;
        }
        World world = new World();
        world.setName("Mundo Institucional");
        world.setDescription("Marco estrategico institucional de la Escuela TDI.");
        repository.save(world);
    }

    private void seedRoles(RoleRepository repository) {
        createRole(repository, "Lider", "Responsable principal de un proyecto.");
        createRole(repository, "Colaborador", "Participante del proyecto.");
        createRole(repository, "Tutor", "Acompana entregables y seguimiento academico.");
    }

    private void createRole(RoleRepository repository, String name, String description) {
        if (repository.existsByNameIgnoreCase(name)) {
            return;
        }
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        repository.save(role);
    }

    private void seedPositions(PositionRepository repository) {
        createPosition(repository, "Profesor", "Cargo docente base.");
        createPosition(repository, "Director de Departamento", "Cargo directivo de departamento.");
        createPosition(repository, "Coordinador de Proyecto", "Responsable de seguimiento operativo.");
        createPosition(repository, "Director Escuela TDI", "Responsable directivo de la Escuela TDI.");
        createPosition(repository, "Jefa de Departamento", "Responsable de direccion departamental.");
        createPosition(repository, "Tutor de Proyectos", "Acompana la gestion y seguimiento de proyectos.");
        createPosition(repository, "Administrador", "Administrador institucional de la plataforma MTE.");
    }

    private void createPosition(PositionRepository repository, String name, String description) {
        if (repository.existsByNameIgnoreCase(name)) {
            return;
        }
        Position position = new Position();
        position.setName(name);
        position.setDescription(description);
        repository.save(position);
    }

    private void seedProfessors(ProfessorRepository repository, DepartmentRepository departmentRepository) {
        createProfessor(repository, departmentRepository, "Hugo Arboleda", "hugo.arboleda@icesi.edu.co", TDI_DIRECTION_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Roc\u00edo Segovia", "rocio.segovia@icesi.edu.co", COMPUTING_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Leonardo Bustamante", "leonardo.bustamante@icesi.edu.co", COMPUTING_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Sistemas MTE", "sistemas.mte@icesi.edu.co", INSTITUTIONAL_IT_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Profesor Demo", "demo.profesor@icesi.edu.co", COMPUTING_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Ana Maria Rojas", "ana.rojas@icesi.edu.co", COMPUTING_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Carlos Mejia", "carlos.mejia@icesi.edu.co", DESIGN_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Laura Gomez", "laura.gomez@icesi.edu.co", SCIENCES_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Mariana Torres", "mariana.torres@icesi.edu.co", DESIGN_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Felipe Arango", "felipe.arango@icesi.edu.co", COMPUTING_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Valentina Ruiz", "valentina.ruiz@icesi.edu.co", DESIGN_DEPARTMENT);
    }

    private void createProfessor(
            ProfessorRepository repository,
            DepartmentRepository departmentRepository,
            String name,
            String email,
            String departmentName
    ) {
        if (repository.existsByEmailIgnoreCase(email)) {
            return;
        }
        departmentRepository.findByNameIgnoreCase(departmentName).ifPresent(department -> {
            Professor professor = new Professor();
            professor.setName(name);
            professor.setEmail(email);
            professor.setDepartment(department);
            repository.save(professor);
        });
    }

    private void seedTeacherPositions(
            TeacherPositionRepository repository,
            PositionRepository positionRepository,
            ProfessorRepository professorRepository
    ) {
        Position professorPosition = positionRepository.findByNameIgnoreCase("Profesor").orElse(null);
        Position directorPosition = positionRepository.findByNameIgnoreCase("Director de Departamento").orElse(null);
        Position coordinatorPosition = positionRepository.findByNameIgnoreCase("Coordinador de Proyecto").orElse(null);
        Position tdiDirectorPosition = positionRepository.findByNameIgnoreCase("Director Escuela TDI").orElse(null);
        Position headPosition = positionRepository.findByNameIgnoreCase("Jefa de Departamento").orElse(null);
        Position tutorPosition = positionRepository.findByNameIgnoreCase("Tutor de Proyectos").orElse(null);
        Position adminPosition = positionRepository.findByNameIgnoreCase("Administrador").orElse(null);
        if (professorPosition == null) {
            return;
        }
        professorRepository.findAll().forEach(professor -> createTeacherPosition(repository, professorPosition, professor, true));
        professorRepository.findByEmailIgnoreCase("hugo.arboleda@icesi.edu.co")
                .filter(professor -> tdiDirectorPosition != null)
                .ifPresent(professor -> createTeacherPosition(repository, tdiDirectorPosition, professor, true));
        professorRepository.findByEmailIgnoreCase("rocio.segovia@icesi.edu.co")
                .filter(professor -> headPosition != null)
                .ifPresent(professor -> createTeacherPosition(repository, headPosition, professor, true));
        professorRepository.findByEmailIgnoreCase("leonardo.bustamante@icesi.edu.co")
                .filter(professor -> tutorPosition != null)
                .ifPresent(professor -> createTeacherPosition(repository, tutorPosition, professor, true));
        professorRepository.findByEmailIgnoreCase("sistemas.mte@icesi.edu.co")
                .filter(professor -> adminPosition != null)
                .ifPresent(professor -> createTeacherPosition(repository, adminPosition, professor, true));
        professorRepository.findByEmailIgnoreCase("ana.rojas@icesi.edu.co")
                .filter(professor -> directorPosition != null)
                .ifPresent(professor -> createTeacherPosition(repository, directorPosition, professor, true));
        professorRepository.findByEmailIgnoreCase("mariana.torres@icesi.edu.co")
                .filter(professor -> coordinatorPosition != null)
                .ifPresent(professor -> createTeacherPosition(repository, coordinatorPosition, professor, true));
    }

    private void createTeacherPosition(
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

    private void seedStrategicHierarchy(
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            DepartmentRepository departmentRepository,
            WorldRepository worldRepository,
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
            ObjectiveRepository objectiveRepository,
            KeyResultRepository keyResultRepository
    ) {
        World world = worldRepository.findByNameIgnoreCase("Mundo Institucional").orElse(null);
        MeasurementUnit percentage = unitRepository.findByNameIgnoreCase("Porcentaje").orElse(null);
        MeasurementUnit number = unitRepository.findByNameIgnoreCase("Numero").orElse(null);
        MeasurementUnit index = unitRepository.findByNameIgnoreCase("Indice").orElse(null);
        AcademicPeriod period20261 = periodRepository.findByNameIgnoreCase("2026-1").orElse(null);
        AcademicPeriod period20262 = periodRepository.findByNameIgnoreCase("2026-2").orElse(null);
        AcademicPeriod period20271 = periodRepository.findByNameIgnoreCase("2027-1").orElse(null);
        Department computing = departmentRepository.findByNameIgnoreCase(COMPUTING_DEPARTMENT).orElse(null);
        Department design = departmentRepository.findByNameIgnoreCase(DESIGN_DEPARTMENT).orElse(null);
        Department sciences = departmentRepository.findByNameIgnoreCase(SCIENCES_DEPARTMENT).orElse(null);
        if (world == null || percentage == null || number == null || index == null
                || period20261 == null || period20262 == null || period20271 == null
                || computing == null || design == null || sciences == null) {
            return;
        }

        StrategicBet talentBet = seedStrategicBet(
                strategicBetRepository,
                "Atraer, acompanar y formar",
                "A lo largo de la vida, personas talentosas y comprometidas con su futuro, que buscan ser exitosas y contribuir a la sociedad.",
                world
        );
        StrategicBet learningBet = seedStrategicBet(
                strategicBetRepository,
                "Ofrecer experiencias formativas memorables e innovadoras",
                "Con trayectorias flexibles, perspectiva global y exposicion al mundo laboral, que potencien el desarrollo de capacidades excepcionales y permitan el florecimiento personal y crecimiento profesional.",
                world
        );
        StrategicBet facultyBet = seedStrategicBet(
                strategicBetRepository,
                "Atraer a los mejores profesores y colaboradores",
                "Potenciar sus capacidades para estar a la vanguardia del conocimiento, ser innovadores y conectar con las organizaciones y el mercado.",
                world
        );
        StrategicBet alliancesBet = seedStrategicBet(
                strategicBetRepository,
                "Desarrollar alianzas estrategicas",
                "Con organizaciones que valoran el conocimiento y el aprendizaje, para ser su aliado de preferencia en la consecucion y actualizacion del talento, la gestion del conocimiento, la investigacion, desarrollo tecnologico e innovacion y la creacion de valor compartido.",
                world
        );
        StrategicBet communityBet = seedStrategicBet(
                strategicBetRepository,
                "Adaptar y extender nuestra comunidad",
                "Para actuar como plataforma que potencia conexiones significativas, empleabilidad y aprendizaje para toda la vida.",
                world
        );
        StrategicBet campusBet = seedStrategicBet(
                strategicBetRepository,
                "Ofrecer experiencias extraordinarias para nuestra comunidad",
                "Mediante un campus habilitador del aprendizaje y el disfrute, tecnologia de punta y una cultura del servicio y el cuidado.",
                world
        );

        InstitutionalGoal talentGoal = seedGoal(
                goalRepository,
                "Aumentar captacion, permanencia y exito de talento",
                "Elevar la proporcion de estudiantes y participantes que ingresan, permanecen y avanzan satisfactoriamente en sus trayectorias formativas.",
                "Tasa de permanencia y avance",
                BigDecimal.valueOf(88),
                percentage,
                world,
                period20261,
                period20271
        );
        InstitutionalGoal learningGoal = seedGoal(
                goalRepository,
                "Escalar trayectorias flexibles y experiencias memorables",
                "Incrementar programas y rutas con flexibilidad curricular, componentes globales, exposicion laboral y aprendizaje activo.",
                "Programas con trayectorias innovadoras",
                BigDecimal.valueOf(70),
                percentage,
                world,
                period20261,
                period20271
        );
        InstitutionalGoal facultyGoal = seedGoal(
                goalRepository,
                "Fortalecer atraccion y desarrollo de profesores",
                "Aumentar la participacion de profesores y colaboradores en iniciativas de vanguardia, innovacion y conexion con organizaciones.",
                "Profesores en desarrollo de capacidades",
                BigDecimal.valueOf(85),
                percentage,
                world,
                period20261,
                period20271
        );
        InstitutionalGoal alliancesGoal = seedGoal(
                goalRepository,
                "Incrementar alianzas estrategicas activas",
                "Consolidar alianzas con organizaciones para consultoria, investigacion aplicada, innovacion y formacion de talento.",
                "Alianzas y proyectos activos",
                BigDecimal.valueOf(24),
                number,
                world,
                period20261,
                period20271
        );
        InstitutionalGoal communityGoal = seedGoal(
                goalRepository,
                "Activar comunidad extendida y empleabilidad",
                "Aumentar conexiones significativas entre estudiantes, egresados, profesores y organizaciones para aprendizaje permanente y empleabilidad.",
                "Participacion de comunidad extendida",
                BigDecimal.valueOf(65),
                percentage,
                world,
                period20261,
                period20271
        );
        InstitutionalGoal campusGoal = seedGoal(
                goalRepository,
                "Mejorar satisfaccion con servicios y campus habilitador",
                "Elevar la satisfaccion de la comunidad con tecnologia, servicios, espacios y experiencias de cuidado.",
                "Indice de satisfaccion de experiencia",
                BigDecimal.valueOf(90),
                index,
                world,
                period20261,
                period20271
        );

        Objective admissionObjective = seedObjective(objectiveRepository, "Optimizar prospeccion, admision y seguimiento temprano", "Implementar capacidades analiticas y digitales para identificar, atraer y acompanar talento desde el primer contacto.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(55), 1, BigDecimal.valueOf(38), computing, period20261, talentGoal, talentBet);
        Objective competitiveObjective = seedObjective(objectiveRepository, "Fortalecer ingreso y entrenamiento de talento destacado", "Sistematizar rutas de ingreso, entrenamiento y acompanamiento para estudiantes con alto potencial en areas estrategicas.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(45), 2, BigDecimal.valueOf(30), sciences, period20261, talentGoal, talentBet);
        Objective postgraduateObjective = seedObjective(objectiveRepository, "Modernizar oferta posgradual y rutas flexibles", "Gestionar la oferta posgradual con informacion actualizada, trazabilidad comercial y mayor flexibilidad para el estudiante.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(50), 1, BigDecimal.valueOf(42), design, period20261, learningGoal, learningBet);
        Objective learningInnovationObjective = seedObjective(objectiveRepository, "Integrar experiencias inteligentes de aprendizaje", "Desarrollar tutores, simuladores y recursos interactivos que mejoren la experiencia de aprendizaje en cursos complejos.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(50), 2, BigDecimal.valueOf(35), design, period20262, learningGoal, learningBet);
        Objective facultyDevelopmentObjective = seedObjective(objectiveRepository, "Impulsar capacidades docentes en IA, datos e innovacion", "Crear entornos y herramientas para que profesores y colaboradores disenen, ejecuten y mejoren iniciativas de alto impacto.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(60), 1, BigDecimal.valueOf(40), computing, period20261, facultyGoal, facultyBet);
        Objective facultyAnalyticsObjective = seedObjective(objectiveRepository, "Conectar profesores con retos de frontera aplicada", "Articular profesores con proyectos de analitica, energia, salud e innovacion digital junto a organizaciones externas.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(40), 2, BigDecimal.valueOf(28), sciences, period20262, facultyGoal, facultyBet);
        Objective consultingObjective = seedObjective(objectiveRepository, "Consolidar portafolio de consultoria basada en datos", "Estandarizar activos analiticos y agentes consultores para apoyar procesos de mejora y gobierno de datos en organizaciones.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(55), 1, BigDecimal.valueOf(45), computing, period20261, alliancesGoal, alliancesBet);
        Objective appliedInnovationObjective = seedObjective(objectiveRepository, "Acelerar proyectos de innovacion aplicada con aliados", "Desarrollar prototipos y modelos para resolver retos reales de sectores como energia, salud y transformacion digital.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(45), 3, BigDecimal.valueOf(32), design, period20262, alliancesGoal, alliancesBet);
        Objective employabilityObjective = seedObjective(objectiveRepository, "Fortalecer conexiones con egresados y organizaciones", "Crear mecanismos de relacionamiento que conecten talento, oportunidades laborales y aprendizaje para toda la vida.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(50), 2, BigDecimal.valueOf(25), design, period20262, communityGoal, communityBet);
        Objective lifelongLearningObjective = seedObjective(objectiveRepository, "Expandir aprendizaje permanente para comunidad extendida", "Disenar experiencias cortas, modulares y actualizables para egresados, profesionales y aliados.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(50), 3, BigDecimal.valueOf(22), design, period20262, communityGoal, communityBet);
        Objective campusTechnologyObjective = seedObjective(objectiveRepository, "Elevar servicios digitales y tecnologia de campus", "Mejorar disponibilidad, facilidad de uso y trazabilidad de servicios digitales para estudiantes, profesores y colaboradores.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(60), 2, BigDecimal.valueOf(36), computing, period20262, campusGoal, campusBet);
        Objective careObjective = seedObjective(objectiveRepository, "Fortalecer cultura de servicio y cuidado", "Implementar mediciones y acciones de mejora para experiencias de servicio oportunas, empaticas y consistentes.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(40), 4, BigDecimal.valueOf(20), design, period20271, campusGoal, campusBet);

        seedKeyResult(keyResultRepository, KR_PROSPECTS, "Aumentar la proporcion de prospectos priorizados con modelos de analitica comercial.", "Prospectos priorizados", BigDecimal.ZERO, BigDecimal.valueOf(1000), BigDecimal.valueOf(380), BigDecimal.valueOf(38), number, period20261, admissionObjective);
        seedKeyResult(keyResultRepository, KR_COMPETITIVE_PROGRAMMING, "Incrementar estudiantes que completan entrenamiento inicial del club de programacion competitiva.", "Estudiantes entrenados", BigDecimal.ZERO, BigDecimal.valueOf(120), BigDecimal.valueOf(36), BigDecimal.valueOf(30), number, period20261, competitiveObjective);
        seedKeyResult(keyResultRepository, KR_POSTGRADUATE_OFFER, "Publicar programas y rutas posgraduales con informacion completa, vigente y trazable.", "Oferta actualizada", BigDecimal.ZERO, BigDecimal.valueOf(90), BigDecimal.valueOf(38), BigDecimal.valueOf(42), percentage, period20261, postgraduateObjective);
        seedKeyResult(keyResultRepository, KR_INTERACTIVE_COURSES, "Implementar recursos inteligentes en asignaturas priorizadas.", "Cursos intervenidos", BigDecimal.ZERO, BigDecimal.valueOf(12), BigDecimal.valueOf(4), BigDecimal.valueOf(35), number, period20262, learningInnovationObjective);
        seedKeyResult(keyResultRepository, KR_AI_FACULTY, "Habilitar profesores y colaboradores en herramientas de IA aplicada, automatizacion y mejora continua.", "Profesores habilitados", BigDecimal.ZERO, BigDecimal.valueOf(60), BigDecimal.valueOf(24), BigDecimal.valueOf(40), number, period20261, facultyDevelopmentObjective);
        seedKeyResult(keyResultRepository, KR_APPLIED_CHALLENGES, "Vincular profesores a retos con organizaciones y dominios de frontera.", "Retos aplicados", BigDecimal.ZERO, BigDecimal.valueOf(18), BigDecimal.valueOf(5), BigDecimal.valueOf(28), number, period20262, facultyAnalyticsObjective);
        seedKeyResult(keyResultRepository, KR_CONSULTING_ASSETS, "Crear agentes, diagnosticos y modelos reutilizables para servicios de consultoria.", "Activos consultivos", BigDecimal.ZERO, BigDecimal.valueOf(10), BigDecimal.valueOf(5), BigDecimal.valueOf(45), number, period20261, consultingObjective);
        seedKeyResult(keyResultRepository, KR_APPLIED_PROTOTYPES, "Desarrollar prototipos validados con organizaciones externas.", "Prototipos validados", BigDecimal.ZERO, BigDecimal.valueOf(8), BigDecimal.valueOf(3), BigDecimal.valueOf(32), number, period20262, appliedInnovationObjective);
        seedKeyResult(keyResultRepository, KR_EMPLOYABILITY_CONNECTIONS, "Activar conexiones entre comunidad extendida, aliados y oportunidades profesionales.", "Conexiones activas", BigDecimal.ZERO, BigDecimal.valueOf(300), BigDecimal.valueOf(75), BigDecimal.valueOf(25), number, period20262, employabilityObjective);
        seedKeyResult(keyResultRepository, KR_MICROCREDENTIALS, "Lanzar rutas cortas actualizables para egresados, profesionales y aliados.", "Microcredenciales lanzadas", BigDecimal.ZERO, BigDecimal.valueOf(15), BigDecimal.valueOf(3), BigDecimal.valueOf(22), number, period20262, lifelongLearningObjective);
        seedKeyResult(keyResultRepository, KR_DIGITAL_SERVICES, "Mejorar servicios digitales criticos y aumentar su satisfaccion de uso.", "Indice de satisfaccion digital", BigDecimal.ZERO, BigDecimal.valueOf(90), BigDecimal.valueOf(36), BigDecimal.valueOf(36), index, period20262, campusTechnologyObjective);
        seedKeyResult(keyResultRepository, KR_SERVICE_CARE, "Implementar acciones de mejora derivadas de mediciones de experiencia.", "Acciones implementadas", BigDecimal.ZERO, BigDecimal.valueOf(20), BigDecimal.valueOf(4), BigDecimal.valueOf(20), number, period20271, careObjective);
    }

    private StrategicBet seedStrategicBet(StrategicBetRepository repository, String name, String description, World world) {
        return repository.findAll().stream()
                .filter(bet -> bet.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    StrategicBet bet = new StrategicBet();
                    bet.setName(name);
                    bet.setDescription(description);
                    bet.setStartDate(LocalDate.of(2026, 1, 1));
                    bet.setEndDate(LocalDate.of(2028, 12, 31));
                    bet.setStatus(StrategicStatus.ACTIVA);
                    bet.setWorld(world);
                    return repository.save(bet);
                });
    }

    private InstitutionalGoal seedGoal(
            InstitutionalGoalRepository repository,
            String name,
            String description,
            String referenceIndicator,
            BigDecimal expectedValue,
            MeasurementUnit unit,
            World world,
            AcademicPeriod firstPeriod,
            AcademicPeriod lastPeriod
    ) {
        return repository.findAll().stream()
                .filter(goal -> goal.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    InstitutionalGoal goal = new InstitutionalGoal();
                    goal.setName(name);
                    goal.setDescription(description);
                    goal.setReferenceIndicator(referenceIndicator);
                    goal.setExpectedValue(expectedValue);
                    goal.setStartDate(firstPeriod.getStartDate());
                    goal.setEndDate(lastPeriod.getEndDate());
                    goal.setStatus(StrategicStatus.ACTIVA);
                    goal.setMeasurementUnit(unit);
                    goal.setWorld(world);
                    goal.getPeriods().add(firstPeriod);
                    goal.getPeriods().add(lastPeriod);
                    return repository.save(goal);
                });
    }

    private Objective seedObjective(
            ObjectiveRepository repository,
            String name,
            String description,
            ObjectiveStatus status,
            BigDecimal estimatedWeight,
            int quarter,
            BigDecimal completionPercentage,
            Department department,
            AcademicPeriod academicPeriod,
            InstitutionalGoal goal,
            StrategicBet strategicBet
    ) {
        return repository.findAll().stream()
                .filter(objective -> objective.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    Objective objective = new Objective();
                    objective.setName(name);
                    objective.setDescription(description);
                    objective.setStatus(status);
                    objective.setEstimatedWeight(estimatedWeight);
                    objective.setQuarter(quarter);
                    objective.setCompletionPercentage(completionPercentage);
                    objective.setDepartment(department);
                    objective.setAcademicPeriod(academicPeriod);
                    objective.setGoal(goal);
                    objective.setStrategicBet(strategicBet);
                    return repository.save(objective);
                });
    }

    private KeyResult seedKeyResult(
            KeyResultRepository repository,
            String name,
            String description,
            String metric,
            BigDecimal baseValue,
            BigDecimal targetValue,
            BigDecimal currentValue,
            BigDecimal progressPercentage,
            MeasurementUnit unit,
            AcademicPeriod period,
            Objective objective
    ) {
        return repository.findAll().stream()
                .filter(keyResult -> keyResult.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    KeyResult keyResult = new KeyResult();
                    keyResult.setName(name);
                    keyResult.setDescription(description);
                    keyResult.setMetric(metric);
                    keyResult.setBaseValue(baseValue);
                    keyResult.setTargetValue(targetValue);
                    keyResult.setCurrentValue(currentValue);
                    keyResult.setProgressPercentage(progressPercentage);
                    keyResult.setMeasurementUnit(unit);
                    keyResult.setAcademicPeriod(period);
                    keyResult.setObjective(objective);
                    return repository.save(keyResult);
                });
    }

    private void restoreSeedKeyResultProgress(JdbcTemplate jdbcTemplate) {
        updateKeyResultProgress(jdbcTemplate, KR_PROSPECTS, BigDecimal.valueOf(38));
        updateKeyResultProgress(jdbcTemplate, KR_COMPETITIVE_PROGRAMMING, BigDecimal.valueOf(30));
        updateKeyResultProgress(jdbcTemplate, KR_POSTGRADUATE_OFFER, BigDecimal.valueOf(42));
        updateKeyResultProgress(jdbcTemplate, KR_INTERACTIVE_COURSES, BigDecimal.valueOf(35));
        updateKeyResultProgress(jdbcTemplate, KR_AI_FACULTY, BigDecimal.valueOf(40));
        updateKeyResultProgress(jdbcTemplate, KR_APPLIED_CHALLENGES, BigDecimal.valueOf(28));
        updateKeyResultProgress(jdbcTemplate, KR_CONSULTING_ASSETS, BigDecimal.valueOf(45));
        updateKeyResultProgress(jdbcTemplate, KR_APPLIED_PROTOTYPES, BigDecimal.valueOf(32));
        updateKeyResultProgress(jdbcTemplate, KR_EMPLOYABILITY_CONNECTIONS, BigDecimal.valueOf(25));
        updateKeyResultProgress(jdbcTemplate, KR_MICROCREDENTIALS, BigDecimal.valueOf(22));
        updateKeyResultProgress(jdbcTemplate, KR_DIGITAL_SERVICES, BigDecimal.valueOf(36));
        updateKeyResultProgress(jdbcTemplate, KR_SERVICE_CARE, BigDecimal.valueOf(20));
    }

    private void updateKeyResultProgress(JdbcTemplate jdbcTemplate, String keyResultName, BigDecimal progressPercentage) {
        jdbcTemplate.update(
                "UPDATE key_result SET progress_percentage = ? WHERE name = ?",
                progressPercentage,
                keyResultName
        );
    }

    private void seedProjects(
            KeyResultRepository keyResultRepository,
            DepartmentRepository departmentRepository,
            ProfessorRepository professorRepository,
            RoleRepository roleRepository,
            ProjectRepository projectRepository,
            ProjectTeacherRepository projectTeacherRepository,
            ProjectProgressEntryRepository progressRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        Department computing = departmentRepository.findByNameIgnoreCase(COMPUTING_DEPARTMENT).orElse(null);
        Department design = departmentRepository.findByNameIgnoreCase(DESIGN_DEPARTMENT).orElse(null);
        Department sciences = departmentRepository.findByNameIgnoreCase(SCIENCES_DEPARTMENT).orElse(null);
        Professor ana = professorRepository.findByEmailIgnoreCase("ana.rojas@icesi.edu.co").orElse(null);
        Professor carlos = professorRepository.findByEmailIgnoreCase("carlos.mejia@icesi.edu.co").orElse(null);
        Professor laura = professorRepository.findByEmailIgnoreCase("laura.gomez@icesi.edu.co").orElse(null);
        Professor mariana = professorRepository.findByEmailIgnoreCase("mariana.torres@icesi.edu.co").orElse(null);
        Professor felipe = professorRepository.findByEmailIgnoreCase("felipe.arango@icesi.edu.co").orElse(null);
        Professor valentina = professorRepository.findByEmailIgnoreCase("valentina.ruiz@icesi.edu.co").orElse(null);
        Role leader = roleRepository.findByNameIgnoreCase("Lider").orElse(null);
        if (computing == null || design == null || sciences == null
                || ana == null || carlos == null || laura == null || mariana == null || felipe == null || valentina == null || leader == null) {
            return;
        }

        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2001L, "Desarrollo de un sistema web para la configuracion, ejecucion y seguimiento de tareas distribuidas de entrenamiento de modelos de inteligencia artificial", "Plataforma para configurar, ejecutar y monitorear entrenamientos distribuidos de modelos de IA.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, computing, ana, leader, KR_AI_FACULTY, BigDecimal.valueOf(45), BigDecimal.valueOf(48), "2026-1", "2026-2", List.of("Laboratorio de IA Aplicada"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2002L, "Plataforma de gestion de la oferta de formacion posgradual de la Universidad Icesi", "Sistema para administrar, publicar y mantener actualizada la oferta posgradual.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, design, mariana, leader, KR_POSTGRADUATE_OFFER, BigDecimal.valueOf(50), BigDecimal.valueOf(42), "2026-1", "2026-2", List.of("Educacion Continua", "Mercadeo Institucional"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2003L, "Diseno de una Arquitectura Centralizada de Datos y Modelos Analiticos para la Optimizacion de la Prospeccion Comercial en los Servicios de Consultoria de la Universidad Icesi", "Arquitectura de datos y modelos para priorizar prospectos y optimizar procesos comerciales de consultoria.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, computing, felipe, leader, KR_PROSPECTS, BigDecimal.valueOf(40), BigDecimal.valueOf(38), "2026-1", "2026-2", List.of("Centro de Consultoria"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2004L, "Prototipo de Agente IA Consultor para la ejecucion y seguimiento de iniciativas de mejora continua de procesos LEAN", "Agente de IA para apoyar seguimiento, recomendaciones y trazabilidad de iniciativas Lean.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, computing, ana, leader, KR_CONSULTING_ASSETS, BigDecimal.valueOf(35), BigDecimal.valueOf(46), "2026-1", "2026-2", List.of("Consultoria Lean"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2005L, "Agente IA Consultor para el apoyo de procesos de analisis de capacidades de Gobierno de Datos en las organizaciones", "Agente consultivo para diagnosticar capacidades de gobierno de datos y sugerir rutas de mejora.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, computing, felipe, leader, KR_CONSULTING_ASSETS, BigDecimal.valueOf(30), BigDecimal.valueOf(45), "2026-1", "2026-2", List.of("Gobierno de Datos"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2006L, "MVP para diagnostico de madurez IRL y generacion de plan de intervencion para iniciativas de innovacion digital", "MVP para diagnosticar madurez y proponer planes de intervencion en innovacion digital.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, design, carlos, leader, KR_APPLIED_PROTOTYPES, BigDecimal.valueOf(30), BigDecimal.valueOf(32), "2026-2", "2027-1", List.of("Laboratorio de Innovacion Digital"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2007L, "Plataforma web gamificada para la sistematizacion del ingreso y entrenamiento de estudiantes del Club de Programacion Competitiva de la Universidad Icesi", "Plataforma gamificada para registrar ingreso, entrenamiento y progreso de estudiantes del club.", ProjectType.GRADO, ProjectStatus.ACTIVO, sciences, laura, leader, KR_COMPETITIVE_PROGRAMMING, BigDecimal.valueOf(40), BigDecimal.valueOf(30), "2026-1", "2026-2", List.of("Club de Programacion Competitiva"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2008L, "Sistema de proyeccion academica para simular matricula en doble titulacion interna y externa para los estudiantes", "Simulador academico para analizar escenarios de matricula, homologaciones y doble titulacion.", ProjectType.GRADO, ProjectStatus.ACTIVO, computing, ana, leader, KR_INTERACTIVE_COURSES, BigDecimal.valueOf(25), BigDecimal.valueOf(35), "2026-2", "2027-1", List.of("Registro Academico"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2009L, "VISTA: VISualizador y Tutor Interactivo e Inteligente de Estructuras Discretas", "Tutor inteligente para apoyar visualizacion y aprendizaje interactivo de estructuras discretas.", ProjectType.GRADO, ProjectStatus.ACTIVO, sciences, laura, leader, KR_INTERACTIVE_COURSES, BigDecimal.valueOf(35), BigDecimal.valueOf(36), "2026-2", "2027-1", List.of("Cursos de Matematicas Discretas"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2010L, "Desarrollo de modelo predictivo de precios de energia en bolsa basado en variables hidrometeorologicas del IDEAM y operativas de XM para la gestion del riesgo financiero en Colombia", "Modelo predictivo para anticipar precios de energia y apoyar decisiones de gestion de riesgo.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, sciences, laura, leader, KR_APPLIED_CHALLENGES, BigDecimal.valueOf(30), BigDecimal.valueOf(28), "2026-2", "2027-1", List.of("Aliado sector energia"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2011L, "Herramienta Interactiva para el Analisis Historico y Predictivo de Estrategias de Oferta de Generadores Solares en el Mercado Mayorista de Energia", "Herramienta para explorar estrategias historicas y predictivas de oferta de generadores solares.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, sciences, laura, leader, KR_APPLIED_CHALLENGES, BigDecimal.valueOf(25), BigDecimal.valueOf(28), "2026-2", "2027-1", List.of("Mercado Mayorista de Energia"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2012L, "Diseno de un modelo de anonimizacion de datos clinicos para investigacion en entornos hospitalarios", "Modelo para anonimizar datos clinicos y habilitar investigacion responsable en salud.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, computing, ana, leader, KR_APPLIED_PROTOTYPES, BigDecimal.valueOf(30), BigDecimal.valueOf(32), "2026-2", "2027-1", List.of("Hospital Universitario"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2013L, "Red de mentoria y empleabilidad para egresados TDI", "Plataforma y proceso de mentoria entre egresados, estudiantes y aliados empleadores.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, design, felipe, leader, KR_EMPLOYABILITY_CONNECTIONS, BigDecimal.valueOf(45), BigDecimal.valueOf(25), "2026-2", "2027-1", List.of("Egresados TDI"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2014L, "Ruta de microcredenciales en IA aplicada para profesionales", "Diseno de microcredenciales modulares para actualizacion profesional en IA aplicada.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, design, mariana, leader, KR_MICROCREDENTIALS, BigDecimal.valueOf(40), BigDecimal.valueOf(22), "2026-2", "2027-1", List.of("Educacion Continua"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2015L, "Portal unificado de servicios y solicitudes de campus", "Portal para centralizar solicitudes, seguimiento y medicion de servicios a la comunidad.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, computing, valentina, leader, KR_DIGITAL_SERVICES, BigDecimal.valueOf(45), BigDecimal.valueOf(36), "2026-2", "2027-1", List.of("Servicios Universitarios"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2016L, "Programa de cultura del servicio y cuidado TDI", "Programa de medicion, formacion y acciones de mejora para cultura de servicio.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, design, valentina, leader, KR_SERVICE_CARE, BigDecimal.valueOf(40), BigDecimal.valueOf(20), "2027-1", "2027-1", List.of("Bienestar Universitario"));
    }

    private void createStrategicProject(
            ProjectRepository projectRepository,
            ProjectTeacherRepository projectTeacherRepository,
            ProjectProgressEntryRepository progressRepository,
            ProjectKeyResultLinkRepository linkRepository,
            KeyResultRepository keyResultRepository,
            Long externalProjectId,
            String name,
            String description,
            ProjectType type,
            ProjectStatus status,
            Department department,
            Professor leaderProfessor,
            Role leaderRole,
            String keyResultName,
            BigDecimal contributionWeight,
            BigDecimal globalProgress,
            String startPeriod,
            String endPeriod,
            List<String> tutors
    ) {
        KeyResult keyResult = findKeyResultByName(keyResultRepository, keyResultName);
        if (keyResult == null) {
            return;
        }
        Project project = seedProject(projectRepository, externalProjectId, name, description, type, status, department, keyResult, contributionWeight, globalProgress, startPeriod, endPeriod, tutors);
        createProjectTeacher(projectTeacherRepository, project, leaderProfessor, leaderRole);
        createProjectLink(linkRepository, project, keyResult, contributionWeight, ContributionType.DIRECTA);
        createProgressEntries(progressRepository, project, globalProgress);
    }

    private KeyResult findKeyResultByName(KeyResultRepository repository, String name) {
        return repository.findAll().stream()
                .filter(keyResult -> keyResult.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private Project seedProject(
            ProjectRepository repository,
            Long externalProjectId,
            String name,
            String description,
            ProjectType type,
            ProjectStatus status,
            Department department,
            KeyResult keyResult,
            BigDecimal contributionWeight,
            BigDecimal globalProgress,
            String startPeriod,
            String endPeriod,
            List<String> tutors
    ) {
        return repository.findByExternalSourceAndExternalProjectId("REAL_MTE", externalProjectId)
                .orElseGet(() -> {
                    Project project = new Project();
                    project.setExternalProjectId(externalProjectId);
                    project.setExternalSource("REAL_MTE");
                    project.setName(name);
                    project.setDescription(description);
                    project.setType(type);
                    project.setStatus(status);
                    project.setDepartment(department);
                    project.setDepartmentName(department.getName());
                    project.setKeyResult(keyResult);
                    project.setContributionWeight(contributionWeight);
                    project.setLinkStatus("ACTIVO");
                    project.setGlobalProgress(globalProgress);
                    project.setStartPeriod(startPeriod);
                    project.setEndPeriod(endPeriod);
                    project.setStartDate(LocalDate.of(2026, 1, 15));
                    project.setEndDate(LocalDate.of(2027, 6, 30));
                    if (status == ProjectStatus.FINALIZADO) {
                        project.setActualEndDate(LocalDate.of(2026, 12, 15));
                    }
                    project.setOrigin(ProjectOrigin.LOCAL);
                    project.setSyncStatus(ProjectSyncStatus.LOCAL_ONLY);
                    project.setTutors(tutors);
                    return repository.save(project);
                });
    }

    private void createProjectTeacher(
            ProjectTeacherRepository repository,
            Project project,
            Professor teacher,
            Role role
    ) {
        if (teacher == null || role == null) {
            return;
        }
        ProjectTeacherId id = new ProjectTeacherId(project.getId(), teacher.getId(), role.getId());
        if (repository.existsById(id)) {
            return;
        }
        ProjectTeacher projectTeacher = new ProjectTeacher();
        projectTeacher.setId(id);
        projectTeacher.setProject(project);
        projectTeacher.setTeacher(teacher);
        projectTeacher.setRole(role);
        projectTeacher.setJoinedAt(LocalDate.of(2026, 1, 20));
        repository.save(projectTeacher);
    }

    private void createProjectLink(
            ProjectKeyResultLinkRepository repository,
            Project project,
            KeyResult keyResult,
            BigDecimal contributionWeight,
            ContributionType contributionType
    ) {
        if (repository.existsByProjectIdAndKeyResultIdAndActiveTrue(project.getId(), keyResult.getId())) {
            return;
        }
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setProject(project);
        link.setKeyResult(keyResult);
        link.setContributionWeight(contributionWeight);
        link.setContributionType(contributionType);
        link.setActive(true);
        repository.save(link);
    }

    private void createProgressEntries(ProjectProgressEntryRepository repository, Project project, BigDecimal currentProgress) {
        if (!repository.findByProjectIdOrderByCreatedAtDesc(project.getId()).isEmpty()) {
            return;
        }
        BigDecimal firstProgress = currentProgress.divide(BigDecimal.valueOf(2));
        createProgressEntry(repository, project, firstProgress, "Primer avance registrado", "Planeacion, alcance y equipo definidos");
        createProgressEntry(repository, project, currentProgress, "Avance de seguimiento", "Entregables principales en ejecucion");
    }

    private void createProgressEntry(
            ProjectProgressEntryRepository repository,
            Project project,
            BigDecimal progress,
            String comment,
            String milestones
    ) {
        ProjectProgressEntry entry = new ProjectProgressEntry();
        entry.setProject(project);
        entry.setProgressPercent(progress);
        entry.setComment(comment);
        entry.setMilestones(milestones);
        entry.setCreatedByExternalUserId(1000L);
        repository.save(entry);
    }
}
