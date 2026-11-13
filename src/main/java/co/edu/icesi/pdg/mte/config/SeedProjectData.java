package co.edu.icesi.pdg.mte.config;

import co.edu.icesi.pdg.mte.catalog.*;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.people.*;
import co.edu.icesi.pdg.mte.project.*;
import co.edu.icesi.pdg.mte.strategy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static co.edu.icesi.pdg.mte.config.SeedCatalogData.COMPUTING_DEPARTMENT;
import static co.edu.icesi.pdg.mte.config.SeedCatalogData.DESIGN_DEPARTMENT;
import static co.edu.icesi.pdg.mte.config.SeedCatalogData.SCIENCES_DEPARTMENT;
import static co.edu.icesi.pdg.mte.config.SeedStrategicHierarchyData.*;

/**
 * Datos de demo de proyectos reales: equipo lider, vinculos a KRs y entradas de avance.
 */
final class SeedProjectData {
    private static final Logger log = LoggerFactory.getLogger(SeedDataConfig.class);

    private SeedProjectData() {
    }

    static void seedProjects(
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

        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2001L, "Desarrollo de un sistema web para la configuracion, ejecucion y seguimiento de tareas distribuidas de entrenamiento de modelos de inteligencia artificial", "Plataforma para configurar, ejecutar y monitorear entrenamientos distribuidos de modelos de IA.", ProjectType.INVESTIGACION, ProjectStatus.FINALIZADO, computing, hugo, leader, KR_AI_FACULTY, BigDecimal.valueOf(100), "2026-1", "2026-2", List.of("Laboratorio de IA Aplicada"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2002L, "Plataforma de gestion de la oferta de formacion posgradual de la Universidad Icesi", "Sistema para administrar, publicar y mantener actualizada la oferta posgradual.", ProjectType.EXTENSION, ProjectStatus.FINALIZADO, design, mariana, leader, KR_POSTGRADUATE_OFFER, BigDecimal.valueOf(100), "2026-1", "2026-2", List.of("Educacion Continua", "Mercadeo Institucional"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2003L, "Diseno de una Arquitectura Centralizada de Datos y Modelos Analiticos para la Optimizacion de la Prospeccion Comercial en los Servicios de Consultoria de la Universidad Icesi", "Arquitectura de datos y modelos para priorizar prospectos y optimizar procesos comerciales de consultoria.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, computing, felipe, leader, KR_PROSPECTS, BigDecimal.valueOf(75), "2026-1", "2026-2", List.of("Centro de Consultoria"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2004L, "Prototipo de Agente IA Consultor para la ejecucion y seguimiento de iniciativas de mejora continua de procesos LEAN", "Agente de IA para apoyar seguimiento, recomendaciones y trazabilidad de iniciativas Lean.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, computing, hugo, leader, KR_CONSULTING_ASSETS, BigDecimal.valueOf(70), "2026-1", "2026-2", List.of("Consultoria Lean"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2005L, "Agente IA Consultor para el apoyo de procesos de analisis de capacidades de Gobierno de Datos en las organizaciones", "Agente consultivo para diagnosticar capacidades de gobierno de datos y sugerir rutas de mejora.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, computing, felipe, leader, KR_CONSULTING_ASSETS, BigDecimal.valueOf(50), "2026-1", "2026-2", List.of("Gobierno de Datos"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2006L, "MVP para diagnostico de madurez IRL y generacion de plan de intervencion para iniciativas de innovacion digital", "MVP para diagnosticar madurez y proponer planes de intervencion en innovacion digital.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, design, carlos, leader, KR_APPLIED_PROTOTYPES, BigDecimal.valueOf(64), "2026-2", "2027-1", List.of("Laboratorio de Innovacion Digital"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2007L, "Plataforma web gamificada para la sistematizacion del ingreso y entrenamiento de estudiantes del Club de Programacion Competitiva de la Universidad Icesi", "Plataforma gamificada para registrar ingreso, entrenamiento y progreso de estudiantes del club.", ProjectType.GRADO, ProjectStatus.ACTIVO, sciences, laura, leader, KR_COMPETITIVE_PROGRAMMING, BigDecimal.valueOf(42), "2026-1", "2026-2", List.of("Club de Programacion Competitiva"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2008L, "Sistema de proyeccion academica para simular matricula en doble titulacion interna y externa para los estudiantes", "Simulador academico para analizar escenarios de matricula, homologaciones y doble titulacion.", ProjectType.GRADO, ProjectStatus.FINALIZADO, computing, hugo, leader, KR_INTERACTIVE_COURSES, BigDecimal.valueOf(100), "2026-2", "2027-1", List.of("Registro Academico"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2009L, "VISTA: VISualizador y Tutor Interactivo e Inteligente de Estructuras Discretas", "Tutor inteligente para apoyar visualizacion y aprendizaje interactivo de estructuras discretas.", ProjectType.GRADO, ProjectStatus.FINALIZADO, sciences, laura, leader, KR_INTERACTIVE_COURSES, BigDecimal.valueOf(100), "2026-2", "2027-1", List.of("Cursos de Matematicas Discretas"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2010L, "Desarrollo de modelo predictivo de precios de energia en bolsa basado en variables hidrometeorologicas del IDEAM y operativas de XM para la gestion del riesgo financiero en Colombia", "Modelo predictivo para anticipar precios de energia y apoyar decisiones de gestion de riesgo.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, sciences, laura, leader, KR_APPLIED_CHALLENGES, BigDecimal.valueOf(45), "2026-2", "2027-1", List.of("Aliado sector energia"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2011L, "Herramienta Interactiva para el Analisis Historico y Predictivo de Estrategias de Oferta de Generadores Solares en el Mercado Mayorista de Energia", "Herramienta para explorar estrategias historicas y predictivas de oferta de generadores solares.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, sciences, laura, leader, KR_APPLIED_CHALLENGES, BigDecimal.valueOf(45), "2026-2", "2027-1", List.of("Mercado Mayorista de Energia"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2012L, "Diseno de un modelo de anonimizacion de datos clinicos para investigacion en entornos hospitalarios", "Modelo para anonimizar datos clinicos y habilitar investigacion responsable en salud.", ProjectType.INVESTIGACION, ProjectStatus.ACTIVO, computing, hugo, leader, KR_APPLIED_PROTOTYPES, BigDecimal.valueOf(64), "2026-2", "2027-1", List.of("Hospital Universitario"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2013L, "Red de mentoria y empleabilidad para egresados TDI", "Plataforma y proceso de mentoria entre egresados, estudiantes y aliados empleadores.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, design, felipe, leader, KR_EMPLOYABILITY_CONNECTIONS, BigDecimal.valueOf(61), "2026-2", "2027-1", List.of("Egresados TDI"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2014L, "Ruta de microcredenciales en IA aplicada para profesionales", "Diseno de microcredenciales modulares para actualizacion profesional en IA aplicada.", ProjectType.EXTENSION, ProjectStatus.BORRADOR, design, mariana, leader, KR_MICROCREDENTIALS, BigDecimal.ZERO, "2026-2", "2027-1", List.of("Educacion Continua"), true, false);
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2015L, "Portal unificado de servicios y solicitudes de campus", "Portal para centralizar solicitudes, seguimiento y medicion de servicios a la comunidad.", ProjectType.EXTENSION, ProjectStatus.FINALIZADO, computing, valentina, leader, KR_DIGITAL_SERVICES, BigDecimal.valueOf(100), "2026-2", "2027-1", List.of("Servicios Universitarios"));
        createStrategicProject(projectRepository, projectTeacherRepository, progressRepository, linkRepository, keyResultRepository, 2016L, "Programa de cultura del servicio y cuidado TDI", "Programa de medicion, formacion y acciones de mejora para cultura de servicio.", ProjectType.EXTENSION, ProjectStatus.ACTIVO, design, valentina, leader, KR_SERVICE_CARE, BigDecimal.ZERO, "2027-1", "2027-1", List.of("Bienestar Universitario"), false, false);

        ageProgressEntries(jdbcTemplate, 2003L, 45);
        ageProgressEntries(jdbcTemplate, 2004L, 25);
        ageProgressEntries(jdbcTemplate, 2013L, 60);
    }

    static void createStrategicProject(
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
                globalProgress,
                startPeriod,
                endPeriod,
                tutors,
                true,
                true
        );
    }

    private static void createStrategicProject(
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
        Project project = seedProject(projectRepository, externalProjectId, name, description, type, status, department, keyResult, globalProgress, startPeriod, endPeriod, tutors);
        createProjectTeacher(projectTeacherRepository, project, leaderProfessor, leaderRole);
        if (createLink) {
            createProjectLink(linkRepository, project, keyResult);
        }
        if (createProgress) {
            createProgressEntries(progressRepository, project, globalProgress);
        }
    }

    private static void ageProgressEntries(JdbcTemplate jdbcTemplate, Long externalProjectId, int daysAgo) {
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

    private static KeyResult findKeyResultByName(KeyResultRepository repository, String name) {
        return repository.findAll().stream()
                .filter(keyResult -> keyResult.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private static Project seedProject(
            ProjectRepository repository,
            Long externalProjectId,
            String name,
            String description,
            ProjectType type,
            ProjectStatus status,
            Department department,
            KeyResult keyResult,
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

    private static void createProjectTeacher(
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

    private static void createProjectLink(
            ProjectKeyResultLinkRepository repository,
            Project project,
            KeyResult keyResult
    ) {
        if (repository.existsByProjectIdAndKeyResultIdAndActiveTrue(project.getId(), keyResult.getId())) {
            return;
        }
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setProject(project);
        link.setKeyResult(keyResult);
        link.setActive(true);
        repository.save(link);
    }

    private static void createProgressEntries(ProjectProgressEntryRepository repository, Project project, BigDecimal currentProgress) {
        if (!repository.findByProjectIdOrderByCreatedAtDesc(project.getId()).isEmpty()) {
            return;
        }
        BigDecimal firstProgress = currentProgress.divide(BigDecimal.valueOf(2));
        createProgressEntry(repository, project, firstProgress, "Primer avance registrado", "Planeacion, alcance y equipo definidos");
        createProgressEntry(repository, project, currentProgress, "Avance de seguimiento", "Entregables principales en ejecucion");
    }

    private static void createProgressEntry(
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
