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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class SeedDataConfig {
    private static final Logger log = LoggerFactory.getLogger(SeedDataConfig.class);
    private static final String COMPUTING_DEPARTMENT = "Departamento de Computaci\u00f3n y Sistemas inteligentes.";
    private static final String DESIGN_DEPARTMENT = "Departamento de Dise\u00f1o e Innovaci\u00f3n";
    private static final String SCIENCES_DEPARTMENT = "Departamento de Ciencias F\u00edsicas y Exactas.";
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
    private static final String KR_UNLINKED_STUDENT_SUCCESS = "Reducir desercion temprana en 10 puntos porcentuales para el final de Q4.";
    private static final String KR_UNLINKED_PARTNER_PORTAL = "Publicar portal de aliados estrategicos con 30 organizaciones para el final de Q4.";

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
            log.info("Starting demo seed data refresh.");
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
                    linkRepository,
                    jdbcTemplate
            );
            seedExpandedPortfolio(
                    unitRepository,
                    periodRepository,
                    departmentRepository,
                    strategicBetRepository,
                    goalRepository,
                    objectiveRepository,
                    keyResultRepository,
                    professorRepository,
                    roleRepository,
                    projectRepository,
                    projectTeacherRepository,
                    progressRepository,
                    linkRepository
            );
            synchronizeSeedProgress(jdbcTemplate, keyResultRepository, objectiveRepository);
            log.info("Demo seed data refresh completed.");
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
        createDepartment(repository, "Dirección TDI", "Direccion estrategica de la Escuela TDI.", 4L, school);
        createDepartment(repository, "TI Institucional", "Administracion y soporte institucional de sistemas MTE.", 5L, school);
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
        createProfessor(repository, departmentRepository, "Roc\u00edo Segovia", "rocio.segovia@icesi.edu.co", COMPUTING_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Leonardo Bustamante", "leonardo.bustamante@icesi.edu.co", COMPUTING_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Profesor Demo", "demo.profesor@icesi.edu.co", COMPUTING_DEPARTMENT);
        createProfessor(repository, departmentRepository, "Hugo Arboleda", "hugo.arboleda@icesi.edu.co", "Dirección TDI");
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

        Objective admissionObjective = seedObjective(objectiveRepository, "Optimizar prospeccion, admision y seguimiento temprano", "Implementar capacidades analiticas y digitales para identificar, atraer y acompanar talento desde el primer contacto.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(55), 1, BigDecimal.valueOf(75), computing, period20261, talentGoal, talentBet);
        Objective competitiveObjective = seedObjective(objectiveRepository, "Fortalecer ingreso y entrenamiento de talento destacado", "Sistematizar rutas de ingreso, entrenamiento y acompanamiento para estudiantes con alto potencial en areas estrategicas.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(45), 2, BigDecimal.valueOf(42), sciences, period20261, talentGoal, talentBet);
        Objective postgraduateObjective = seedObjective(objectiveRepository, "Modernizar oferta posgradual y rutas flexibles", "Gestionar la oferta posgradual con informacion actualizada, trazabilidad comercial y mayor flexibilidad para el estudiante.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(50), 1, BigDecimal.valueOf(100), design, period20261, learningGoal, learningBet);
        Objective learningInnovationObjective = seedObjective(objectiveRepository, "Integrar experiencias inteligentes de aprendizaje", "Desarrollar tutores, simuladores y recursos interactivos que mejoren la experiencia de aprendizaje en cursos complejos.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(50), 2, BigDecimal.valueOf(100), design, period20262, learningGoal, learningBet);
        Objective facultyDevelopmentObjective = seedObjective(objectiveRepository, "Impulsar capacidades docentes en IA, datos e innovacion", "Crear entornos y herramientas para que profesores y colaboradores disenen, ejecuten y mejoren iniciativas de alto impacto.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(60), 1, BigDecimal.valueOf(100), computing, period20261, facultyGoal, facultyBet);
        Objective facultyAnalyticsObjective = seedObjective(objectiveRepository, "Conectar profesores con retos de frontera aplicada", "Articular profesores con proyectos de analitica, energia, salud e innovacion digital junto a organizaciones externas.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(40), 2, BigDecimal.valueOf(45), sciences, period20262, facultyGoal, facultyBet);
        Objective consultingObjective = seedObjective(objectiveRepository, "Consolidar portafolio de consultoria basada en datos", "Estandarizar activos analiticos y agentes consultores para apoyar procesos de mejora y gobierno de datos en organizaciones.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(55), 1, BigDecimal.valueOf(68), computing, period20261, alliancesGoal, alliancesBet);
        Objective appliedInnovationObjective = seedObjective(objectiveRepository, "Acelerar proyectos de innovacion aplicada con aliados", "Desarrollar prototipos y modelos para resolver retos reales de sectores como energia, salud y transformacion digital.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(45), 3, BigDecimal.valueOf(64), design, period20262, alliancesGoal, alliancesBet);
        Objective employabilityObjective = seedObjective(objectiveRepository, "Fortalecer conexiones con egresados y organizaciones", "Crear mecanismos de relacionamiento que conecten talento, oportunidades laborales y aprendizaje para toda la vida.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(50), 2, BigDecimal.valueOf(61), design, period20262, communityGoal, communityBet);
        Objective lifelongLearningObjective = seedObjective(objectiveRepository, "Expandir aprendizaje permanente para comunidad extendida", "Disenar experiencias cortas, modulares y actualizables para egresados, profesionales y aliados.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(50), 3, BigDecimal.ZERO, design, period20262, communityGoal, communityBet);
        Objective campusTechnologyObjective = seedObjective(objectiveRepository, "Elevar servicios digitales y tecnologia de campus", "Mejorar disponibilidad, facilidad de uso y trazabilidad de servicios digitales para estudiantes, profesores y colaboradores.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(60), 2, BigDecimal.valueOf(100), computing, period20262, campusGoal, campusBet);
        Objective careObjective = seedObjective(objectiveRepository, "Fortalecer cultura de servicio y cuidado", "Implementar mediciones y acciones de mejora para experiencias de servicio oportunas, empaticas y consistentes.", ObjectiveStatus.ACTIVO, BigDecimal.valueOf(40), 4, BigDecimal.ZERO, design, period20271, campusGoal, campusBet);

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
        seedKeyResult(keyResultRepository, KR_UNLINKED_STUDENT_SUCCESS, "KR intencional de demo sin proyectos activos vinculados.", "Reduccion de desercion", BigDecimal.ZERO, BigDecimal.valueOf(10), BigDecimal.ZERO, BigDecimal.ZERO, percentage, period20261, admissionObjective);
        seedKeyResult(keyResultRepository, KR_UNLINKED_PARTNER_PORTAL, "KR intencional de demo sin proyectos vinculados para consistencia.", "Organizaciones en portal", BigDecimal.ZERO, BigDecimal.valueOf(30), BigDecimal.ZERO, BigDecimal.ZERO, number, period20262, appliedInnovationObjective);
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

    private void synchronizeSeedProgress(
            JdbcTemplate jdbcTemplate,
            KeyResultRepository keyResultRepository,
            ObjectiveRepository objectiveRepository
    ) {
        Map<Long, BigDecimal> progressByKeyResultId = jdbcTemplate.query(
                """
                        SELECT kr.id,
                               COALESCE(SUM(l.contribution_weight * p.global_progress / 100), 0) AS progress
                        FROM key_result kr
                        LEFT JOIN project_key_result_link l
                            ON l.key_result_id = kr.id
                           AND l.active = TRUE
                        LEFT JOIN project p
                            ON p.id = l.project_id
                        GROUP BY kr.id
                        """,
                resultSet -> {
                    Map<Long, BigDecimal> progressById = new HashMap<>();
                    while (resultSet.next()) {
                        BigDecimal progress = resultSet.getBigDecimal("progress");
                        progressById.put(resultSet.getLong("id"), normalizeProgress(progress));
                    }
                    return progressById;
                }
        );
        progressByKeyResultId.forEach((keyResultId, progress) ->
                updateKeyResultProgress(jdbcTemplate, keyResultId, progress)
        );

        objectiveRepository.findAll().forEach(objective -> {
            List<BigDecimal> progressValues = keyResultRepository.findByObjectiveIdOrderByIdAsc(objective.getId())
                    .stream()
                    .map(keyResult -> progressByKeyResultId.getOrDefault(keyResult.getId(), BigDecimal.ZERO))
                    .toList();
            BigDecimal completion = progressValues.isEmpty()
                    ? BigDecimal.ZERO
                    : progressValues.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(progressValues.size()), 2, RoundingMode.HALF_UP);
            updateObjectiveCompletion(jdbcTemplate, objective.getId(), completion);
        });
    }

    private BigDecimal normalizeProgress(BigDecimal progress) {
        if (progress == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (progress.compareTo(BigDecimal.valueOf(100)) > 0) {
            progress = BigDecimal.valueOf(100);
        }
        return progress.setScale(2, RoundingMode.HALF_UP);
    }

    private void updateKeyResultProgress(JdbcTemplate jdbcTemplate, Long keyResultId, BigDecimal progressPercentage) {
        jdbcTemplate.update(
                "UPDATE key_result SET progress_percentage = ?, current_value = base_value + ((target_value - base_value) * ? / 100) WHERE id = ?",
                progressPercentage,
                progressPercentage,
                keyResultId
        );
    }

    private void updateObjectiveCompletion(JdbcTemplate jdbcTemplate, Long objectiveId, BigDecimal completionPercentage) {
        jdbcTemplate.update(
                "UPDATE objective SET completion_percentage = ? WHERE id = ?",
                completionPercentage,
                objectiveId
        );
    }

    private void seedExpandedPortfolio(
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            DepartmentRepository departmentRepository,
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
            ObjectiveRepository objectiveRepository,
            KeyResultRepository keyResultRepository,
            ProfessorRepository professorRepository,
            RoleRepository roleRepository,
            ProjectRepository projectRepository,
            ProjectTeacherRepository projectTeacherRepository,
            ProjectProgressEntryRepository progressRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        Role leader = roleRepository.findByNameIgnoreCase("Lider").orElse(null);
        long nextExternalProjectId = 3001L;
        for (ExpandedObjectiveSeed objectiveSeed : expandedObjectiveSeeds()) {
            Department department = departmentRepository.findByNameIgnoreCase(objectiveSeed.departmentName()).orElse(null);
            AcademicPeriod period = periodRepository.findByNameIgnoreCase(objectiveSeed.periodName()).orElse(null);
            InstitutionalGoal goal = findGoalByName(goalRepository, objectiveSeed.goalName());
            StrategicBet strategicBet = findStrategicBetByName(strategicBetRepository, objectiveSeed.strategicBetName());
            if (department == null || period == null || goal == null || strategicBet == null) {
                log.warn("Skipping expanded objective seed '{}'. Missing catalog data.", objectiveSeed.name());
                continue;
            }

            Objective objective = seedObjective(
                    objectiveRepository,
                    objectiveSeed.name(),
                    objectiveSeed.description(),
                    ObjectiveStatus.ACTIVO,
                    BigDecimal.valueOf(objectiveSeed.estimatedWeight()),
                    objectiveSeed.quarter(),
                    BigDecimal.ZERO,
                    department,
                    period,
                    goal,
                    strategicBet
            );

            for (ExpandedKeyResultSeed keyResultSeed : objectiveSeed.keyResults()) {
                MeasurementUnit unit = unitRepository.findByNameIgnoreCase(keyResultSeed.unitName()).orElse(null);
                AcademicPeriod keyResultPeriod = periodRepository.findByNameIgnoreCase(keyResultSeed.periodName()).orElse(period);
                if (unit == null) {
                    log.warn("Skipping expanded KR seed '{}'. Missing measurement unit '{}'.", keyResultSeed.name(), keyResultSeed.unitName());
                    continue;
                }
                seedKeyResult(
                        keyResultRepository,
                        keyResultSeed.name(),
                        keyResultSeed.description(),
                        keyResultSeed.metric(),
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(keyResultSeed.targetValue()),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        unit,
                        keyResultPeriod,
                        objective
                );
                nextExternalProjectId = seedExpandedProjects(
                        projectRepository,
                        projectTeacherRepository,
                        progressRepository,
                        linkRepository,
                        keyResultRepository,
                        professorRepository,
                        department,
                        leader,
                        keyResultSeed,
                        nextExternalProjectId,
                        objectiveSeed.periodName()
                );
            }
        }
    }

    private long seedExpandedProjects(
            ProjectRepository projectRepository,
            ProjectTeacherRepository projectTeacherRepository,
            ProjectProgressEntryRepository progressRepository,
            ProjectKeyResultLinkRepository linkRepository,
            KeyResultRepository keyResultRepository,
            ProfessorRepository professorRepository,
            Department department,
            Role leader,
            ExpandedKeyResultSeed keyResultSeed,
            long externalProjectId,
            String startPeriod
    ) {
        Professor leaderProfessor = professorRepository.findByEmailIgnoreCase(keyResultSeed.leaderEmail()).orElse(null);
        BigDecimal progress = BigDecimal.valueOf(keyResultSeed.progress());
        ProjectStatus status = statusForProgress(keyResultSeed.progress());
        ProjectType type = typeForDepartment(department.getName());
        if (keyResultSeed.twoProjects()) {
            createStrategicProject(
                    projectRepository,
                    projectTeacherRepository,
                    progressRepository,
                    linkRepository,
                    keyResultRepository,
                    externalProjectId++,
                    projectName("Piloto", keyResultSeed.projectTheme()),
                    projectDescription("Piloto", keyResultSeed.projectTheme(), department.getName()),
                    type,
                    status,
                    department,
                    leaderProfessor,
                    leader,
                    keyResultSeed.name(),
                    BigDecimal.valueOf(60),
                    progress,
                    startPeriod,
                    keyResultSeed.periodName(),
                    List.of("Comite de seguimiento " + department.getName())
            );
            createStrategicProject(
                    projectRepository,
                    projectTeacherRepository,
                    progressRepository,
                    linkRepository,
                    keyResultRepository,
                    externalProjectId++,
                    projectName("Escalamiento", keyResultSeed.projectTheme()),
                    projectDescription("Escalamiento", keyResultSeed.projectTheme(), department.getName()),
                    type,
                    status,
                    department,
                    leaderProfessor,
                    leader,
                    keyResultSeed.name(),
                    BigDecimal.valueOf(40),
                    progress,
                    startPeriod,
                    keyResultSeed.periodName(),
                    List.of("Equipo operativo " + department.getName())
            );
            return externalProjectId;
        }

        createStrategicProject(
                projectRepository,
                projectTeacherRepository,
                progressRepository,
                linkRepository,
                keyResultRepository,
                externalProjectId++,
                projectName("Implementacion", keyResultSeed.projectTheme()),
                projectDescription("Implementacion", keyResultSeed.projectTheme(), department.getName()),
                type,
                status,
                department,
                leaderProfessor,
                leader,
                keyResultSeed.name(),
                BigDecimal.valueOf(100),
                progress,
                startPeriod,
                keyResultSeed.periodName(),
                List.of("Mesa de trabajo " + department.getName())
        );
        return externalProjectId;
    }

    private InstitutionalGoal findGoalByName(InstitutionalGoalRepository repository, String name) {
        return repository.findAll().stream()
                .filter(goal -> goal.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private StrategicBet findStrategicBetByName(StrategicBetRepository repository, String name) {
        return repository.findAll().stream()
                .filter(strategicBet -> strategicBet.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private ProjectStatus statusForProgress(int progress) {
        if (progress >= 100) {
            return ProjectStatus.FINALIZADO;
        }
        return ProjectStatus.ACTIVO;
    }

    private ProjectType typeForDepartment(String departmentName) {
        if (departmentName.equalsIgnoreCase(SCIENCES_DEPARTMENT)) {
            return ProjectType.INVESTIGACION;
        }
        if (departmentName.equalsIgnoreCase(DESIGN_DEPARTMENT)) {
            return ProjectType.EXTENSION;
        }
        return ProjectType.GRADO;
    }

    private String projectName(String prefix, String theme) {
        return prefix + " de " + theme;
    }

    private String projectDescription(String prefix, String theme, String departmentName) {
        return prefix + " para ejecutar, medir y documentar " + theme + " con el equipo de " + departmentName + ".";
    }

    private List<ExpandedObjectiveSeed> expandedObjectiveSeeds() {
        String talentGoal = "Aumentar captacion, permanencia y exito de talento";
        String learningGoal = "Escalar trayectorias flexibles y experiencias memorables";
        String facultyGoal = "Fortalecer atraccion y desarrollo de profesores";
        String alliancesGoal = "Incrementar alianzas estrategicas activas";
        String communityGoal = "Activar comunidad extendida y empleabilidad";
        String campusGoal = "Mejorar satisfaccion con servicios y campus habilitador";
        String talentBet = "Atraer, acompanar y formar";
        String learningBet = "Ofrecer experiencias formativas memorables e innovadoras";
        String facultyBet = "Atraer a los mejores profesores y colaboradores";
        String alliancesBet = "Desarrollar alianzas estrategicas";
        String communityBet = "Adaptar y extender nuestra comunidad";
        String campusBet = "Ofrecer experiencias extraordinarias para nuestra comunidad";

        return List.of(
                objective("Fortalecer gobierno de datos academicos", "Estandarizar dominios, responsables y reglas de calidad para decisiones academicas trazables.", COMPUTING_DEPARTMENT, talentGoal, talentBet, "2026-1", 1, 18,
                        kr("Catalogar 12 dominios de datos academicos priorizados.", "Dominios catalogados", "Numero", "2026-1", 12, 100, "hugo.arboleda@icesi.edu.co", "catalogo de dominios de datos academicos", true),
                        kr("Publicar 20 reglas de calidad automatizadas.", "Reglas de calidad", "Numero", "2026-1", 20, 100, "felipe.arango@icesi.edu.co", "reglas de calidad de datos", false)),
                objective("Automatizar analitica de permanencia estudiantil", "Crear modelos y tableros tempranos para anticipar riesgos de permanencia en programas TDI.", COMPUTING_DEPARTMENT, talentGoal, talentBet, "2026-1", 2, 16,
                        kr("Desplegar 3 modelos de alerta temprana validados.", "Modelos desplegados", "Numero", "2026-1", 3, 82, "felipe.arango@icesi.edu.co", "modelos de alerta temprana", true),
                        kr("Cubrir 8 programas con tablero de riesgo academico.", "Programas cubiertos", "Numero", "2026-1", 8, 74, "hugo.arboleda@icesi.edu.co", "tablero de riesgo academico", false),
                        kr("Entrenar 25 usuarios en lectura de alertas.", "Usuarios entrenados", "Numero", "2026-1", 25, 68, "rocio.segovia@icesi.edu.co", "formacion en alertas academicas", true)),
                objective("Modernizar laboratorios de nube e IA", "Actualizar laboratorios y guias para practicas reproducibles en nube, datos e inteligencia artificial.", COMPUTING_DEPARTMENT, learningGoal, learningBet, "2026-2", 2, 15,
                        kr("Actualizar 6 laboratorios con ambientes reproducibles.", "Laboratorios actualizados", "Numero", "2026-2", 6, 68, "hugo.arboleda@icesi.edu.co", "laboratorios reproducibles de nube e IA", true),
                        kr("Publicar 10 guias tecnicas para practicas de IA.", "Guias publicadas", "Numero", "2026-2", 10, 60, "leonardo.bustamante@icesi.edu.co", "guias tecnicas de IA aplicada", false)),
                objective("Robustecer practicas de ciberseguridad aplicada", "Integrar ejercicios, politicas y evaluaciones de seguridad en proyectos academicos y servicios internos.", COMPUTING_DEPARTMENT, campusGoal, campusBet, "2026-2", 3, 14,
                        kr("Ejecutar 4 ejercicios de ciberseguridad aplicada.", "Ejercicios ejecutados", "Numero", "2026-2", 4, 45, "felipe.arango@icesi.edu.co", "ejercicios de ciberseguridad aplicada", true),
                        kr("Documentar 8 controles base para proyectos digitales.", "Controles documentados", "Numero", "2026-2", 8, 37, "hugo.arboleda@icesi.edu.co", "controles base de seguridad", false)),
                objective("Integrar observabilidad de servicios digitales academicos", "Medir disponibilidad, tiempos de respuesta y experiencia de uso de servicios digitales criticos.", COMPUTING_DEPARTMENT, campusGoal, campusBet, "2026-2", 4, 13,
                        kr("Instrumentar 5 servicios academicos con metricas operativas.", "Servicios instrumentados", "Numero", "2026-2", 5, 28, "hugo.arboleda@icesi.edu.co", "observabilidad de servicios academicos", true)),
                objective("Preparar repositorio de componentes reutilizables", "Definir arquitectura y gobierno para componentes reutilizables de software academico.", COMPUTING_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2", 4, 12,
                        kr("Definir backlog de 15 componentes reutilizables.", "Componentes definidos", "Numero", "2026-2", 15, 0, "leonardo.bustamante@icesi.edu.co", "backlog de componentes reutilizables", false)),

                objective("Consolidar sistema de experiencia de usuario TDI", "Unificar patrones, criterios y mediciones de experiencia para plataformas y servicios de la escuela.", DESIGN_DEPARTMENT, campusGoal, campusBet, "2026-1", 1, 18,
                        kr("Publicar 24 patrones de experiencia validados.", "Patrones publicados", "Numero", "2026-1", 24, 100, "carlos.mejia@icesi.edu.co", "sistema de patrones UX TDI", true),
                        kr("Aplicar pruebas de usabilidad a 6 servicios priorizados.", "Servicios evaluados", "Numero", "2026-1", 6, 100, "mariana.torres@icesi.edu.co", "pruebas de usabilidad de servicios", false)),
                objective("Escalar laboratorios de prototipado con aliados", "Aumentar la capacidad de prototipado rapido con organizaciones y retos de innovacion aplicada.", DESIGN_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2", 2, 17,
                        kr("Ejecutar 9 sprints de prototipado con aliados.", "Sprints ejecutados", "Numero", "2026-2", 9, 78, "carlos.mejia@icesi.edu.co", "sprints de prototipado con aliados", true),
                        kr("Validar 12 prototipos con usuarios reales.", "Prototipos validados", "Numero", "2026-2", 12, 70, "mariana.torres@icesi.edu.co", "validacion de prototipos con usuarios", false),
                        kr("Documentar 5 casos de aprendizaje transferible.", "Casos documentados", "Numero", "2026-2", 5, 64, "valentina.ruiz@icesi.edu.co", "casos de aprendizaje de prototipado", true)),
                objective("Mejorar identidad visual de rutas flexibles", "Actualizar lenguaje visual y piezas de comunicacion para rutas academicas flexibles y posgraduales.", DESIGN_DEPARTMENT, learningGoal, learningBet, "2026-2", 2, 16,
                        kr("Redisenar 10 piezas de comunicacion de rutas flexibles.", "Piezas redisenadas", "Numero", "2026-2", 10, 62, "mariana.torres@icesi.edu.co", "piezas de rutas flexibles", true),
                        kr("Probar 4 narrativas visuales con estudiantes prospecto.", "Narrativas probadas", "Numero", "2026-2", 4, 54, "carlos.mejia@icesi.edu.co", "narrativas visuales para prospectos", false)),
                objective("Implementar medicion continua de experiencia", "Instalar instrumentos y rutinas de lectura para mejorar experiencias academicas y administrativas.", DESIGN_DEPARTMENT, campusGoal, campusBet, "2027-1", 3, 14,
                        kr("Levantar 6 mapas de experiencia priorizados.", "Mapas levantados", "Numero", "2027-1", 6, 40, "valentina.ruiz@icesi.edu.co", "mapas de experiencia priorizados", true),
                        kr("Cerrar 12 hallazgos de mejora de servicio.", "Hallazgos cerrados", "Numero", "2027-1", 12, 34, "carlos.mejia@icesi.edu.co", "cierre de hallazgos de servicio", false)),
                objective("Pilotos de accesibilidad digital", "Mejorar accesibilidad de recursos y plataformas usados por estudiantes y profesores.", DESIGN_DEPARTMENT, communityGoal, communityBet, "2027-1", 4, 13,
                        kr("Auditar 8 recursos digitales con criterios WCAG.", "Recursos auditados", "Numero", "2027-1", 8, 22, "valentina.ruiz@icesi.edu.co", "auditoria de accesibilidad digital", true)),
                objective("Banco de servicios de diseno para aliados", "Preparar un catalogo de servicios de diseno e innovacion para proyectos con aliados externos.", DESIGN_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2", 4, 12,
                        kr("Definir 10 servicios de diseno listos para oferta.", "Servicios definidos", "Numero", "2026-2", 10, 0, "carlos.mejia@icesi.edu.co", "catalogo de servicios de diseno", false)),

                objective("Fortalecer ruta de matematica aplicada", "Alinear cursos, ejercicios y recursos de matematica aplicada con retos de datos e ingenieria.", SCIENCES_DEPARTMENT, learningGoal, learningBet, "2026-1", 1, 18,
                        kr("Actualizar 7 modulos de matematica aplicada.", "Modulos actualizados", "Numero", "2026-1", 7, 100, "laura.gomez@icesi.edu.co", "modulos de matematica aplicada", true),
                        kr("Crear 14 bancos de ejercicios contextualizados.", "Bancos de ejercicios", "Numero", "2026-1", 14, 100, "laura.gomez@icesi.edu.co", "bancos de ejercicios contextualizados", false)),
                objective("Escalar analitica de energia y sostenibilidad", "Expandir proyectos de modelacion, prediccion y visualizacion para retos de energia y sostenibilidad.", SCIENCES_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2", 2, 17,
                        kr("Validar 4 modelos predictivos para energia.", "Modelos validados", "Numero", "2026-2", 4, 80, "laura.gomez@icesi.edu.co", "modelos predictivos de energia", true),
                        kr("Publicar 3 tableros de sostenibilidad aplicada.", "Tableros publicados", "Numero", "2026-2", 3, 72, "laura.gomez@icesi.edu.co", "tableros de sostenibilidad aplicada", false),
                        kr("Vincular 5 aliados a casos de modelacion.", "Aliados vinculados", "Numero", "2026-2", 5, 64, "laura.gomez@icesi.edu.co", "casos de modelacion con aliados", true)),
                objective("Integrar simuladores para cursos base", "Desarrollar simuladores y visualizadores para apoyar cursos fundamentales de ciencias exactas.", SCIENCES_DEPARTMENT, learningGoal, learningBet, "2026-2", 2, 16,
                        kr("Implementar 6 simuladores interactivos en cursos base.", "Simuladores implementados", "Numero", "2026-2", 6, 66, "laura.gomez@icesi.edu.co", "simuladores interactivos de ciencias", true),
                        kr("Medir uso de simuladores en 10 grupos academicos.", "Grupos medidos", "Numero", "2026-2", 10, 58, "laura.gomez@icesi.edu.co", "medicion de uso de simuladores", false)),
                objective("Articular semilleros de ciencia de datos", "Fortalecer semilleros y rutas de participacion estudiantil en ciencia de datos aplicada.", SCIENCES_DEPARTMENT, facultyGoal, facultyBet, "2027-1", 3, 14,
                        kr("Acompanhar 5 semilleros con retos aplicados.", "Semilleros acompanados", "Numero", "2027-1", 5, 48, "laura.gomez@icesi.edu.co", "semilleros con retos aplicados", true),
                        kr("Registrar 30 estudiantes en actividades de semillero.", "Estudiantes registrados", "Numero", "2027-1", 30, 40, "laura.gomez@icesi.edu.co", "registro de estudiantes de semillero", false)),
                objective("Mejorar acompanamiento en ciencias basicas", "Probar estrategias de acompanamiento y seguimiento para estudiantes en cursos de alta dificultad.", SCIENCES_DEPARTMENT, talentGoal, talentBet, "2027-1", 4, 13,
                        kr("Ejecutar 10 sesiones de acompanamiento focalizado.", "Sesiones ejecutadas", "Numero", "2027-1", 10, 31, "laura.gomez@icesi.edu.co", "acompanamiento en ciencias basicas", true)),
                objective("Observatorio de datos experimentales", "Preparar capacidades para recopilar y compartir datos experimentales de cursos y proyectos.", SCIENCES_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2", 4, 12,
                        kr("Definir 6 protocolos de datos experimentales.", "Protocolos definidos", "Numero", "2026-2", 6, 0, "laura.gomez@icesi.edu.co", "protocolos de datos experimentales", false)),

                objective("Optimizar arquitectura de integraciones academicas", "Mejorar integraciones entre plataformas academicas, analitica y seguimiento de proyectos del departamento.", COMPUTING_DEPARTMENT, campusGoal, campusBet, "2026-1", 1, 18,
                        kr("Cerrar 10 integraciones academicas estabilizadas.", "Integraciones estabilizadas", "Numero", "2026-1", 10, 100, "hugo.arboleda@icesi.edu.co", "integraciones academicas estabilizadas", true),
                        kr("Reducir incidentes repetitivos de integracion en 80 por ciento.", "Reduccion de incidentes", "Porcentaje", "2026-1", 100, 100, "felipe.arango@icesi.edu.co", "reduccion de incidentes de integracion", false)),
                objective("Automatizar trazabilidad de proyectos de software", "Vincular objetivos, KRs, repositorios y evidencias para seguimiento de proyectos academicos de software.", COMPUTING_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2", 2, 17,
                        kr("Trazar 35 relaciones entre KRs, proyectos y repositorios.", "Relaciones trazadas", "Numero", "2026-2", 35, 82, "hugo.arboleda@icesi.edu.co", "trazabilidad de proyectos de software", true),
                        kr("Publicar 6 vistas de avance para equipos docentes.", "Vistas publicadas", "Numero", "2026-2", 6, 74, "leonardo.bustamante@icesi.edu.co", "vistas de avance de software", false),
                        kr("Socializar avances con 5 comites de programa.", "Comites socializados", "Numero", "2026-2", 5, 68, "rocio.segovia@icesi.edu.co", "socializacion de avances de software", true)),
                objective("Escalar soporte tecnico para laboratorios TDI", "Usar datos de solicitudes y satisfaccion para mejorar tiempos, priorizacion y calidad del soporte tecnico academico.", COMPUTING_DEPARTMENT, campusGoal, campusBet, "2026-2", 2, 16,
                        kr("Clasificar 1200 solicitudes con categorias normalizadas.", "Solicitudes clasificadas", "Numero", "2026-2", 1200, 84, "felipe.arango@icesi.edu.co", "clasificacion de solicitudes tecnicas", true),
                        kr("Automatizar 15 respuestas a solicitudes frecuentes.", "Respuestas automatizadas", "Numero", "2026-2", 15, 78, "hugo.arboleda@icesi.edu.co", "respuestas automatizadas de soporte", false),
                        kr("Elevar satisfaccion de soporte tecnico a 82 puntos.", "Satisfaccion soporte", "Indice", "2026-2", 100, 70, "leonardo.bustamante@icesi.edu.co", "medicion de satisfaccion de soporte", true)),
                objective("Preparar continuidad operativa de plataformas academicas", "Disenar practicas y evidencias para mantener plataformas academicas ante incidentes o cambios mayores.", COMPUTING_DEPARTMENT, campusGoal, campusBet, "2026-2", 4, 12,
                        kr("Documentar 5 procedimientos de continuidad operativa.", "Procedimientos documentados", "Numero", "2026-2", 5, 0, "hugo.arboleda@icesi.edu.co", "procedimientos de continuidad operativa academica", false)),

                objective("Gobernar portafolio de experiencias digitales", "Ordenar prioridades, patrones y rituales de decision para experiencias digitales de la escuela.", DESIGN_DEPARTMENT, campusGoal, campusBet, "2026-1", 1, 18,
                        kr("Aprobar 18 iniciativas priorizadas de experiencia digital.", "Iniciativas aprobadas", "Numero", "2026-1", 18, 100, "carlos.mejia@icesi.edu.co", "priorizacion de experiencias digitales", true),
                        kr("Cerrar 4 ciclos de revision de experiencia.", "Ciclos cerrados", "Numero", "2026-1", 4, 100, "mariana.torres@icesi.edu.co", "revision de experiencia digital", false)),
                objective("Medir valor de iniciativas de innovacion", "Preparar criterios y mediciones para valorar beneficios academicos, operativos y de experiencia.", DESIGN_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2", 2, 17,
                        kr("Definir 9 indicadores de beneficios por iniciativa.", "Indicadores definidos", "Numero", "2026-2", 9, 82, "carlos.mejia@icesi.edu.co", "indicadores de beneficios de innovacion", true),
                        kr("Publicar 6 tableros de beneficios para aliados.", "Tableros publicados", "Numero", "2026-2", 6, 76, "mariana.torres@icesi.edu.co", "tableros de beneficios de innovacion", false),
                        kr("Validar 5 casos de valor con usuarios y aliados.", "Casos validados", "Numero", "2026-2", 5, 68, "valentina.ruiz@icesi.edu.co", "casos de valor de innovacion", true)),
                objective("Alinear capacidades de diseno con retos estrategicos", "Identificar brechas y activar rutas de desarrollo para capacidades de diseno e innovacion aplicada.", DESIGN_DEPARTMENT, facultyGoal, facultyBet, "2026-2", 2, 16,
                        kr("Mapear 40 capacidades de diseno frente a apuestas.", "Capacidades mapeadas", "Numero", "2026-2", 40, 70, "mariana.torres@icesi.edu.co", "mapa de capacidades de diseno", true),
                        kr("Activar 6 rutas de desarrollo en innovacion.", "Rutas activadas", "Numero", "2026-2", 6, 62, "carlos.mejia@icesi.edu.co", "rutas de desarrollo en innovacion", false)),
                objective("Preparar catalogo de beneficios de experiencia", "Construir criterios y evidencias para comunicar beneficios de servicios de diseno a aliados.", DESIGN_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2", 4, 12,
                        kr("Definir 9 fichas de beneficios de experiencia.", "Fichas definidas", "Numero", "2026-2", 9, 0, "valentina.ruiz@icesi.edu.co", "catalogo de beneficios de experiencia", false)),

                objective("Consolidar portafolio de modelacion aplicada", "Ordenar prioridades y evidencias de proyectos de modelacion cuantitativa con impacto academico y externo.", SCIENCES_DEPARTMENT, alliancesGoal, alliancesBet, "2026-1", 1, 18,
                        kr("Aprobar 12 casos priorizados de modelacion aplicada.", "Casos aprobados", "Numero", "2026-1", 12, 100, "laura.gomez@icesi.edu.co", "priorizacion de modelacion aplicada", true),
                        kr("Cerrar 4 revisiones tecnicas del portafolio.", "Revisiones cerradas", "Numero", "2026-1", 4, 100, "laura.gomez@icesi.edu.co", "revision tecnica de modelacion", false)),
                objective("Mejorar trazabilidad de laboratorios cuantitativos", "Centralizar evidencias, responsables y resultados de laboratorios de ciencias exactas.", SCIENCES_DEPARTMENT, campusGoal, campusBet, "2026-2", 2, 17,
                        kr("Trazar 24 laboratorios con responsables y evidencias.", "Laboratorios trazados", "Numero", "2026-2", 24, 80, "laura.gomez@icesi.edu.co", "trazabilidad de laboratorios cuantitativos", true),
                        kr("Publicar 6 vistas de avance para cursos base.", "Vistas publicadas", "Numero", "2026-2", 6, 72, "laura.gomez@icesi.edu.co", "vistas de avance de laboratorios", false),
                        kr("Socializar avances con 5 equipos docentes.", "Equipos socializados", "Numero", "2026-2", 5, 64, "laura.gomez@icesi.edu.co", "socializacion de laboratorios cuantitativos", true)),
                objective("Implementar tablero de riesgos academicos en ciencias", "Crear alertas y responsables para cursos de alta dificultad y laboratorios criticos.", SCIENCES_DEPARTMENT, talentGoal, talentBet, "2026-2", 3, 14,
                        kr("Configurar 12 riesgos academicos con responsables.", "Riesgos configurados", "Numero", "2026-2", 12, 50, "laura.gomez@icesi.edu.co", "riesgos academicos en ciencias", true),
                        kr("Escalar 8 alertas a equipos de curso.", "Alertas escaladas", "Numero", "2026-2", 8, 40, "laura.gomez@icesi.edu.co", "alertas academicas en ciencias", false)),
                objective("Definir repositorio de indicadores experimentales", "Preparar inventario, protocolos y responsables para indicadores experimentales reutilizables.", SCIENCES_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2", 4, 12,
                        kr("Identificar 25 indicadores experimentales criticos.", "Indicadores identificados", "Numero", "2026-2", 25, 0, "laura.gomez@icesi.edu.co", "repositorio de indicadores experimentales", false))
        );
    }

    private ExpandedObjectiveSeed objective(
            String name,
            String description,
            String departmentName,
            String goalName,
            String strategicBetName,
            String periodName,
            int quarter,
            int estimatedWeight,
            ExpandedKeyResultSeed... keyResults
    ) {
        return new ExpandedObjectiveSeed(
                name,
                description,
                departmentName,
                goalName,
                strategicBetName,
                periodName,
                quarter,
                estimatedWeight,
                List.of(keyResults)
        );
    }

    private ExpandedKeyResultSeed kr(
            String name,
            String metric,
            String unitName,
            String periodName,
            int targetValue,
            int progress,
            String leaderEmail,
            String projectTheme,
            boolean twoProjects
    ) {
        return new ExpandedKeyResultSeed(
                name,
                "KR de ampliacion de demo para " + metric.toLowerCase() + ".",
                metric,
                unitName,
                periodName,
                targetValue,
                progress,
                leaderEmail,
                projectTheme,
                twoProjects
        );
    }

    private record ExpandedObjectiveSeed(
            String name,
            String description,
            String departmentName,
            String goalName,
            String strategicBetName,
            String periodName,
            int quarter,
            int estimatedWeight,
            List<ExpandedKeyResultSeed> keyResults
    ) {
    }

    private record ExpandedKeyResultSeed(
            String name,
            String description,
            String metric,
            String unitName,
            String periodName,
            int targetValue,
            int progress,
            String leaderEmail,
            String projectTheme,
            boolean twoProjects
    ) {
    }

    private void seedProjects(
            KeyResultRepository keyResultRepository,
            DepartmentRepository departmentRepository,
            ProfessorRepository professorRepository,
            RoleRepository roleRepository,
            ProjectRepository projectRepository,
            ProjectTeacherRepository projectTeacherRepository,
            ProjectProgressEntryRepository progressRepository,
            ProjectKeyResultLinkRepository linkRepository,
            JdbcTemplate jdbcTemplate
    ) {
        Department computing = departmentRepository.findByNameIgnoreCase(COMPUTING_DEPARTMENT).orElse(null);
        Department design = departmentRepository.findByNameIgnoreCase(DESIGN_DEPARTMENT).orElse(null);
        Department sciences = departmentRepository.findByNameIgnoreCase(SCIENCES_DEPARTMENT).orElse(null);
        Professor hugo = professorRepository.findByEmailIgnoreCase("hugo.arboleda@icesi.edu.co").orElse(null);
        Professor carlos = professorRepository.findByEmailIgnoreCase("carlos.mejia@icesi.edu.co").orElse(null);
        Professor laura = professorRepository.findByEmailIgnoreCase("laura.gomez@icesi.edu.co").orElse(null);
        Professor mariana = professorRepository.findByEmailIgnoreCase("mariana.torres@icesi.edu.co").orElse(null);
        Professor felipe = professorRepository.findByEmailIgnoreCase("felipe.arango@icesi.edu.co").orElse(null);
        Professor valentina = professorRepository.findByEmailIgnoreCase("valentina.ruiz@icesi.edu.co").orElse(null);
        Role leader = roleRepository.findByNameIgnoreCase("Lider").orElse(null);
        if (computing == null || design == null || sciences == null) {
            log.warn("Skipping project seed because one or more required departments are missing.");
            return;
        }
        if (leader == null) {
            log.warn("Project seed will continue without leader assignments because role 'Lider' is missing.");
        }

        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2001L, "Desarrollo de un sistema web para la configuracion, ejecucion y seguimiento de tareas distribuidas de entrenamiento de modelos de inteligencia artificial", "Plataforma para configurar, ejecutar y monitorear entrenamientos distribuidos de modelos de IA.", ProjectType.INVESTIGACION, ProjectStatus.FINALIZADO, computing, hugo, leader, KR_AI_FACULTY, BigDecimal.valueOf(100), BigDecimal.valueOf(100), "2026-1", "2026-2", List.of("Laboratorio de IA Aplicada"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2002L, "Plataforma de gestion de la oferta de formacion posgradual de la Universidad Icesi", "Sistema para administrar, publicar y mantener actualizada la oferta posgradual.", ProjectType.EXTENSION, ProjectStatus.FINALIZADO, design, mariana, leader, KR_POSTGRADUATE_OFFER, BigDecimal.valueOf(100), BigDecimal.valueOf(100), "2026-1", "2026-2", List.of("Educacion Continua", "Mercadeo Institucional"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2003L, "Diseno de una Arquitectura Centralizada de Datos y Modelos Analiticos para la Optimizacion de la Prospeccion Comercial en los Servicios de Consultoria de la Universidad Icesi", "Arquitectura de datos y modelos para priorizar prospectos y optimizar procesos comerciales de consultoria.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, computing, felipe, leader, KR_PROSPECTS, BigDecimal.valueOf(100), BigDecimal.valueOf(75), "2026-1", "2026-2", List.of("Centro de Consultoria"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2004L, "Prototipo de Agente IA Consultor para la ejecucion y seguimiento de iniciativas de mejora continua de procesos LEAN", "Agente de IA para apoyar seguimiento, recomendaciones y trazabilidad de iniciativas Lean.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, computing, hugo, leader, KR_CONSULTING_ASSETS, BigDecimal.valueOf(60), BigDecimal.valueOf(70), "2026-1", "2026-2", List.of("Consultoria Lean"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2005L, "Agente IA Consultor para el apoyo de procesos de analisis de capacidades de Gobierno de Datos en las organizaciones", "Agente consultivo para diagnosticar capacidades de gobierno de datos y sugerir rutas de mejora.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, computing, felipe, leader, KR_CONSULTING_ASSETS, BigDecimal.valueOf(40), BigDecimal.valueOf(50), "2026-1", "2026-2", List.of("Gobierno de Datos"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2006L, "MVP para diagnostico de madurez IRL y generacion de plan de intervencion para iniciativas de innovacion digital", "MVP para diagnosticar madurez y proponer planes de intervencion en innovacion digital.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, design, carlos, leader, KR_APPLIED_PROTOTYPES, BigDecimal.valueOf(50), BigDecimal.valueOf(64), "2026-2", "2027-1", List.of("Laboratorio de Innovacion Digital"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2007L, "Plataforma web gamificada para la sistematizacion del ingreso y entrenamiento de estudiantes del Club de Programacion Competitiva de la Universidad Icesi", "Plataforma gamificada para registrar ingreso, entrenamiento y progreso de estudiantes del club.", ProjectType.GRADO, ProjectStatus.ACTIVO, sciences, laura, leader, KR_COMPETITIVE_PROGRAMMING, BigDecimal.valueOf(100), BigDecimal.valueOf(42), "2026-1", "2026-2", List.of("Club de Programacion Competitiva"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2008L, "Sistema de proyeccion academica para simular matricula en doble titulacion interna y externa para los estudiantes", "Simulador academico para analizar escenarios de matricula, homologaciones y doble titulacion.", ProjectType.GRADO, ProjectStatus.FINALIZADO, computing, hugo, leader, KR_INTERACTIVE_COURSES, BigDecimal.valueOf(60), BigDecimal.valueOf(100), "2026-2", "2027-1", List.of("Registro Academico"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2009L, "VISTA: VISualizador y Tutor Interactivo e Inteligente de Estructuras Discretas", "Tutor inteligente para apoyar visualizacion y aprendizaje interactivo de estructuras discretas.", ProjectType.GRADO, ProjectStatus.FINALIZADO, sciences, laura, leader, KR_INTERACTIVE_COURSES, BigDecimal.valueOf(40), BigDecimal.valueOf(100), "2026-2", "2027-1", List.of("Cursos de Matematicas Discretas"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2010L, "Desarrollo de modelo predictivo de precios de energia en bolsa basado en variables hidrometeorologicas del IDEAM y operativas de XM para la gestion del riesgo financiero en Colombia", "Modelo predictivo para anticipar precios de energia y apoyar decisiones de gestion de riesgo.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, sciences, laura, leader, KR_APPLIED_CHALLENGES, BigDecimal.valueOf(50), BigDecimal.valueOf(45), "2026-2", "2027-1", List.of("Aliado sector energia"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2011L, "Herramienta Interactiva para el Analisis Historico y Predictivo de Estrategias de Oferta de Generadores Solares en el Mercado Mayorista de Energia", "Herramienta para explorar estrategias historicas y predictivas de oferta de generadores solares.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, sciences, laura, leader, KR_APPLIED_CHALLENGES, BigDecimal.valueOf(50), BigDecimal.valueOf(45), "2026-2", "2027-1", List.of("Mercado Mayorista de Energia"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2012L, "Diseno de un modelo de anonimizacion de datos clinicos para investigacion en entornos hospitalarios", "Modelo para anonimizar datos clinicos y habilitar investigacion responsable en salud.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, computing, hugo, leader, KR_APPLIED_PROTOTYPES, BigDecimal.valueOf(50), BigDecimal.valueOf(64), "2026-2", "2027-1", List.of("Hospital Universitario"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2013L, "Red de mentoria y empleabilidad para egresados TDI", "Plataforma y proceso de mentoria entre egresados, estudiantes y aliados empleadores.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, design, felipe, leader, KR_EMPLOYABILITY_CONNECTIONS, BigDecimal.valueOf(100), BigDecimal.valueOf(61), "2026-2", "2027-1", List.of("Egresados TDI"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2014L, "Ruta de microcredenciales en IA aplicada para profesionales", "Diseno de microcredenciales modulares para actualizacion profesional en IA aplicada.", ProjectType.EXTENSION, ProjectStatus.BORRADOR, design, mariana, leader, KR_MICROCREDENTIALS, BigDecimal.valueOf(100), BigDecimal.ZERO, "2026-2", "2027-1", List.of("Educacion Continua"), true, false);
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2015L, "Portal unificado de servicios y solicitudes de campus", "Portal para centralizar solicitudes, seguimiento y medicion de servicios a la comunidad.", ProjectType.EXTENSION, ProjectStatus.FINALIZADO, computing, valentina, leader, KR_DIGITAL_SERVICES, BigDecimal.valueOf(100), BigDecimal.valueOf(100), "2026-2", "2027-1", List.of("Servicios Universitarios"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2016L, "Programa de cultura del servicio y cuidado TDI", "Programa de medicion, formacion y acciones de mejora para cultura de servicio.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, design, valentina, leader, KR_SERVICE_CARE, BigDecimal.valueOf(40), BigDecimal.ZERO, "2027-1", "2027-1", List.of("Bienestar Universitario"), false, false);

        ageProgressEntries(jdbcTemplate, 2003L, 45);
        ageProgressEntries(jdbcTemplate, 2004L, 25);
        ageProgressEntries(jdbcTemplate, 2013L, 60);
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
        createStrategicProject(
                projectRepository,
                projectTeacherRepository,
                progressRepository,
                linkRepository,
                keyResultRepository,
                externalProjectId,
                name,
                description,
                type,
                status,
                department,
                leaderProfessor,
                leaderRole,
                keyResultName,
                contributionWeight,
                globalProgress,
                startPeriod,
                endPeriod,
                tutors,
                true,
                true
        );
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
            List<String> tutors,
            boolean createLink,
            boolean createProgress
    ) {
        KeyResult keyResult = findKeyResultByName(keyResultRepository, keyResultName);
        if (keyResult == null) {
            return;
        }
        Project project = seedProject(projectRepository, externalProjectId, name, description, type, status, department, keyResult, contributionWeight, globalProgress, startPeriod, endPeriod, tutors);
        createProjectTeacher(projectTeacherRepository, project, leaderProfessor, leaderRole);
        if (createLink) {
            createProjectLink(linkRepository, project, keyResult, contributionWeight, ContributionType.DIRECTA);
        }
        if (createProgress) {
            createProgressEntries(progressRepository, project, globalProgress);
        }
    }

    private void ageProgressEntries(JdbcTemplate jdbcTemplate, Long externalProjectId, int daysAgo) {
        jdbcTemplate.update(
                """
                        update project_progress_entry
                        set created_at = ?
                        where project_id in (
                            select id
                            from project
                            where external_source = 'REAL_MTE'
                              and external_project_id = ?
                        )
                        """,
                Timestamp.from(Instant.now().minusSeconds(daysAgo * 24L * 60L * 60L)),
                externalProjectId
        );
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
