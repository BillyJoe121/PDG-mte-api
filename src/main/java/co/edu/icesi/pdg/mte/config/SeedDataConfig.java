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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Configuration
public class SeedDataConfig {

    @Bean
    @ConditionalOnProperty(prefix = "mte.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner seedData(
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
        };
    }

    private void seedUnits(MeasurementUnitRepository repository) {
        createUnit(repository, "Porcentaje", MeasurementUnitType.PORCENTAJE, "Avance o cumplimiento expresado de 0 a 100.");
        createUnit(repository, "Numero", MeasurementUnitType.NUMERICA, "Conteo numerico simple.");
        createUnit(repository, "Si/No", MeasurementUnitType.BOOLEANA, "Indicador booleano de cumplimiento.");
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
            school.setDescription("Escuela base para demo local.");
            return repository.save(school);
        });
    }

    private void seedDepartments(DepartmentRepository repository, School school) {
        createDepartment(repository, "Departamento de TIC", "Departamento base para demo local.", 1L, school);
        createDepartment(repository, "Departamento de Diseno", "Departamento base para demo local.", 2L, school);
        createDepartment(repository, "Departamento de Ciencias Basicas", "Departamento base para demo local.", 3L, school);
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
        world.setDescription("Agrupacion estrategica base para demo local.");
        repository.save(world);
    }

    private void seedRoles(RoleRepository repository) {
        createRole(repository, "Lider", "Responsable principal de un proyecto.");
        createRole(repository, "Colaborador", "Participante del proyecto.");
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
        createProfessor(repository, departmentRepository, "Profesor Demo", "demo.profesor@icesi.edu.co", "Departamento de TIC");
        createProfessor(repository, departmentRepository, "Ana Maria Rojas", "ana.rojas@icesi.edu.co", "Departamento de TIC");
        createProfessor(repository, departmentRepository, "Carlos Mejia", "carlos.mejia@icesi.edu.co", "Departamento de Diseno");
        createProfessor(repository, departmentRepository, "Laura Gomez", "laura.gomez@icesi.edu.co", "Departamento de Ciencias Basicas");
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
        if (professorPosition == null) {
            return;
        }
        professorRepository.findAll().forEach(professor -> createTeacherPosition(repository, professorPosition, professor, true));
        professorRepository.findByEmailIgnoreCase("ana.rojas@icesi.edu.co")
                .filter(professor -> directorPosition != null)
                .ifPresent(professor -> createTeacherPosition(repository, directorPosition, professor, true));
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
        AcademicPeriod period20261 = periodRepository.findByNameIgnoreCase("2026-1").orElse(null);
        AcademicPeriod period20262 = periodRepository.findByNameIgnoreCase("2026-2").orElse(null);
        Department tic = departmentRepository.findByNameIgnoreCase("Departamento de TIC").orElse(null);
        Department design = departmentRepository.findByNameIgnoreCase("Departamento de Diseno").orElse(null);
        if (world == null || percentage == null || number == null || period20261 == null || period20262 == null || tic == null || design == null) {
            return;
        }

        StrategicBet digitalBet = seedStrategicBet(
                strategicBetRepository,
                "Transformacion digital academica",
                "Impulsar servicios, analitica y experiencias academicas soportadas por tecnologia.",
                world,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );
        StrategicBet studentBet = seedStrategicBet(
                strategicBetRepository,
                "Experiencia integral del estudiante",
                "Fortalecer la permanencia, acompanamiento y satisfaccion de los estudiantes.",
                world,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );

        InstitutionalGoal digitalGoal = seedGoal(
                goalRepository,
                "Incrementar adopcion de plataformas institucionales",
                "Aumentar el uso efectivo de plataformas institucionales en procesos academicos y administrativos.",
                "Uso mensual activo",
                BigDecimal.valueOf(85),
                percentage,
                world,
                period20261,
                period20262
        );
        InstitutionalGoal retentionGoal = seedGoal(
                goalRepository,
                "Mejorar acompanamiento estudiantil",
                "Consolidar acciones tempranas de acompanamiento para mejorar la permanencia estudiantil.",
                "Estudiantes acompanados",
                BigDecimal.valueOf(1200),
                number,
                world,
                period20261,
                period20262
        );

        Objective platformsObjective = seedObjective(
                objectiveRepository,
                "Optimizar servicios digitales academicos",
                "Modernizar flujos clave de servicio academico y habilitar trazabilidad de uso.",
                ObjectiveStatus.ACTIVO,
                BigDecimal.valueOf(60),
                1,
                BigDecimal.valueOf(45),
                tic,
                period20261,
                digitalGoal,
                digitalBet
        );
        Objective experienceObjective = seedObjective(
                objectiveRepository,
                "Fortalecer rutas de acompanamiento estudiantil",
                "Disenar e implementar rutas de atencion temprana para estudiantes con riesgo academico.",
                ObjectiveStatus.ACTIVO,
                BigDecimal.valueOf(40),
                2,
                BigDecimal.valueOf(25),
                design,
                period20261,
                retentionGoal,
                studentBet
        );

        seedKeyResult(
                keyResultRepository,
                "Aumentar adopcion LMS",
                "Incrementar la adopcion activa del LMS en cursos de pregrado.",
                "Porcentaje de cursos activos",
                BigDecimal.valueOf(40),
                BigDecimal.valueOf(85),
                BigDecimal.valueOf(62),
                BigDecimal.valueOf(35),
                percentage,
                period20261,
                platformsObjective
        );
        seedKeyResult(
                keyResultRepository,
                "Automatizar reportes de seguimiento",
                "Publicar tableros ejecutivos para seguimiento de indicadores academicos.",
                "Reportes automatizados",
                BigDecimal.ZERO,
                BigDecimal.valueOf(8),
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(20),
                number,
                period20261,
                platformsObjective
        );
        seedKeyResult(
                keyResultRepository,
                "Activar rutas de acompanamiento",
                "Implementar rutas de acompanamiento para estudiantes priorizados.",
                "Rutas activas",
                BigDecimal.ZERO,
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(25),
                number,
                period20261,
                experienceObjective
        );
    }

    private StrategicBet seedStrategicBet(
            StrategicBetRepository repository,
            String name,
            String description,
            World world,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return repository.findAll().stream()
                .filter(bet -> bet.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    StrategicBet bet = new StrategicBet();
                    bet.setName(name);
                    bet.setDescription(description);
                    bet.setStartDate(startDate);
                    bet.setEndDate(endDate);
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
            AcademicPeriod secondPeriod
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
                    goal.setEndDate(secondPeriod.getEndDate());
                    goal.setStatus(StrategicStatus.ACTIVA);
                    goal.setMeasurementUnit(unit);
                    goal.setWorld(world);
                    goal.getPeriods().add(firstPeriod);
                    goal.getPeriods().add(secondPeriod);
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
        Department tic = departmentRepository.findByNameIgnoreCase("Departamento de TIC").orElse(null);
        Department design = departmentRepository.findByNameIgnoreCase("Departamento de Diseno").orElse(null);
        Professor ana = professorRepository.findByEmailIgnoreCase("ana.rojas@icesi.edu.co").orElse(null);
        Professor carlos = professorRepository.findByEmailIgnoreCase("carlos.mejia@icesi.edu.co").orElse(null);
        Role leader = roleRepository.findByNameIgnoreCase("Lider").orElse(null);
        KeyResult lmsKr = findKeyResultByName(keyResultRepository, "Aumentar adopcion LMS");
        KeyResult reportsKr = findKeyResultByName(keyResultRepository, "Automatizar reportes de seguimiento");
        KeyResult routesKr = findKeyResultByName(keyResultRepository, "Activar rutas de acompanamiento");
        if (tic == null || design == null || ana == null || carlos == null || leader == null
                || lmsKr == null || reportsKr == null || routesKr == null) {
            return;
        }

        Project portalProject = seedProject(
                projectRepository,
                1001L,
                "Portal de servicios academicos MTE",
                "Implementacion de flujos digitales para seguimiento academico y autoservicio docente.",
                ProjectType.EXTENSION,
                ProjectStatus.ACTIVO,
                tic,
                lmsKr,
                BigDecimal.valueOf(35),
                BigDecimal.valueOf(55),
                "2026-1",
                "2026-2",
                List.of("Comite MTE", "Oficina de Tecnologia")
        );
        Project dashboardProject = seedProject(
                projectRepository,
                1002L,
                "Tablero ejecutivo de indicadores",
                "Construccion de tableros para seguimiento de objetivos, resultados clave y proyectos asociados.",
                ProjectType.INVESTIGACION,
                ProjectStatus.FINALIZADO,
                tic,
                reportsKr,
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(100),
                "2026-1",
                "2026-1",
                List.of("Analitica Institucional")
        );
        Project mentoringProject = seedProject(
                projectRepository,
                1003L,
                "Rutas de acompanamiento estudiantil",
                "Piloto de rutas de acompanamiento para estudiantes con alertas academicas tempranas.",
                ProjectType.GRADO,
                ProjectStatus.ACTIVO,
                design,
                routesKr,
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(35),
                "2026-1",
                "2026-2",
                List.of("Bienestar Universitario")
        );

        createProjectTeacher(projectTeacherRepository, portalProject, ana, leader);
        createProjectTeacher(projectTeacherRepository, dashboardProject, ana, leader);
        createProjectTeacher(projectTeacherRepository, mentoringProject, carlos, leader);

        createProjectLink(linkRepository, portalProject, lmsKr, BigDecimal.valueOf(35), ContributionType.DIRECTA);
        createProjectLink(linkRepository, dashboardProject, reportsKr, BigDecimal.valueOf(20), ContributionType.DIRECTA);
        createProjectLink(linkRepository, mentoringProject, routesKr, BigDecimal.valueOf(25), ContributionType.DIRECTA);

        createProgressEntries(progressRepository, portalProject, BigDecimal.valueOf(25), BigDecimal.valueOf(55));
        createProgressEntries(progressRepository, dashboardProject, BigDecimal.valueOf(75), BigDecimal.valueOf(100));
        createProgressEntries(progressRepository, mentoringProject, BigDecimal.valueOf(15), BigDecimal.valueOf(35));
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
        return repository.findByExternalSourceAndExternalProjectId("LOCAL_MSP", externalProjectId)
                .orElseGet(() -> {
                    Project project = new Project();
                    project.setExternalProjectId(externalProjectId);
                    project.setExternalSource("LOCAL_MSP");
                    project.setName(name);
                    project.setDescription(description);
                    project.setType(type);
                    project.setStatus(status);
                    project.setDepartment(department);
                    project.setDepartmentName(department.getName());
                    project.setKeyResult(keyResult);
                    project.setContributionWeight(contributionWeight);
                    project.setGlobalProgress(globalProgress);
                    project.setStartPeriod(startPeriod);
                    project.setEndPeriod(endPeriod);
                    project.setStartDate(LocalDate.of(2026, 1, 15));
                    project.setEndDate(LocalDate.of(2026, 11, 30));
                    if (status == ProjectStatus.FINALIZADO) {
                        project.setActualEndDate(LocalDate.of(2026, 6, 20));
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

    private void createProgressEntries(
            ProjectProgressEntryRepository repository,
            Project project,
            BigDecimal firstProgress,
            BigDecimal secondProgress
    ) {
        if (!repository.findByProjectIdOrderByCreatedAtDesc(project.getId()).isEmpty()) {
            return;
        }
        createProgressEntry(repository, project, firstProgress, "Primer avance registrado", "Planeacion y levantamiento completados");
        createProgressEntry(repository, project, secondProgress, "Avance de seguimiento", "Entregables principales en ejecucion");
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
