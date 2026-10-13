package co.edu.icesi.pdg.mte.config;

import co.edu.icesi.pdg.mte.catalog.*;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.people.*;
import co.edu.icesi.pdg.mte.project.*;
import co.edu.icesi.pdg.mte.strategy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

import static co.edu.icesi.pdg.mte.config.SeedCatalogData.COMPUTING_DEPARTMENT;
import static co.edu.icesi.pdg.mte.config.SeedCatalogData.DESIGN_DEPARTMENT;
import static co.edu.icesi.pdg.mte.config.SeedCatalogData.SCIENCES_DEPARTMENT;
import static co.edu.icesi.pdg.mte.config.SeedProjectData.createStrategicProject;
import static co.edu.icesi.pdg.mte.config.SeedStrategicHierarchyData.seedKeyResult;

/**
 * Datos de demo ampliados: iniciativas con KRs y proyectos generados por departamento.
 */
final class SeedExpandedPortfolioData {
    private static final Logger log = LoggerFactory.getLogger(SeedDataConfig.class);

    private SeedExpandedPortfolioData() {
    }

    static void seedExpandedPortfolio(
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            DepartmentRepository departmentRepository,
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
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
        for (ExpandedInitiativeSeed initiativeSeed : expandedInitiativeSeeds()) {
            Department department = departmentRepository.findByNameIgnoreCase(initiativeSeed.departmentName()).orElse(null);
            AcademicPeriod period = periodRepository.findByNameIgnoreCase(initiativeSeed.periodName()).orElse(null);
            InstitutionalGoal goal = findGoalByName(goalRepository, initiativeSeed.goalName());
            StrategicBet strategicBet = findStrategicBetByName(strategicBetRepository, initiativeSeed.strategicBetName());
            if (department == null || period == null || goal == null || strategicBet == null) {
                log.warn("Skipping expanded initiative seed '{}'. Missing catalog data.", initiativeSeed.name());
                continue;
            }

            for (ExpandedKeyResultSeed keyResultSeed : initiativeSeed.keyResults()) {
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
                        goal,
                        strategicBet
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
                        initiativeSeed.periodName()
                );
            }
        }
    }

    private static long seedExpandedProjects(
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

    private static InstitutionalGoal findGoalByName(InstitutionalGoalRepository repository, String name) {
        return repository.findAll().stream()
                .filter(goal -> goal.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private static StrategicBet findStrategicBetByName(StrategicBetRepository repository, String name) {
        return repository.findAll().stream()
                .filter(strategicBet -> strategicBet.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private static ProjectStatus statusForProgress(int progress) {
        if (progress >= 100) {
            return ProjectStatus.FINALIZADO;
        }
        return ProjectStatus.ACTIVO;
    }

    private static ProjectType typeForDepartment(String departmentName) {
        if (departmentName.equalsIgnoreCase(SCIENCES_DEPARTMENT)) {
            return ProjectType.INVESTIGACION;
        }
        if (departmentName.equalsIgnoreCase(DESIGN_DEPARTMENT)) {
            return ProjectType.EXTENSION;
        }
        return ProjectType.GRADO;
    }

    private static String projectName(String prefix, String theme) {
        return prefix + " de " + theme;
    }

    private static String projectDescription(String prefix, String theme, String departmentName) {
        return prefix + " para ejecutar, medir y documentar " + theme + " con el equipo de " + departmentName + ".";
    }

    private static List<ExpandedInitiativeSeed> expandedInitiativeSeeds() {
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
                initiative("Fortalecer gobierno de datos academicos", "Estandarizar dominios, responsables y reglas de calidad para decisiones academicas trazables.", COMPUTING_DEPARTMENT, talentGoal, talentBet, "2026-1",
                        kr("Catalogar 12 dominios de datos academicos priorizados.", "Dominios catalogados", "Numero", "2026-1", 12, 100, "hugo.arboleda@icesi.edu.co", "catalogo de dominios de datos academicos", true),
                        kr("Publicar 20 reglas de calidad automatizadas.", "Reglas de calidad", "Numero", "2026-1", 20, 100, "felipe.arango@icesi.edu.co", "reglas de calidad de datos", false)),
                initiative("Automatizar analitica de permanencia estudiantil", "Crear modelos y tableros tempranos para anticipar riesgos de permanencia en programas TDI.", COMPUTING_DEPARTMENT, talentGoal, talentBet, "2026-1",
                        kr("Desplegar 3 modelos de alerta temprana validados.", "Modelos desplegados", "Numero", "2026-1", 3, 82, "felipe.arango@icesi.edu.co", "modelos de alerta temprana", true),
                        kr("Cubrir 8 programas con tablero de riesgo academico.", "Programas cubiertos", "Numero", "2026-1", 8, 74, "hugo.arboleda@icesi.edu.co", "tablero de riesgo academico", false),
                        kr("Entrenar 25 usuarios en lectura de alertas.", "Usuarios entrenados", "Numero", "2026-1", 25, 68, "rocio.segovia@icesi.edu.co", "formacion en alertas academicas", true)),
                initiative("Modernizar laboratorios de nube e IA", "Actualizar laboratorios y guias para practicas reproducibles en nube, datos e inteligencia artificial.", COMPUTING_DEPARTMENT, learningGoal, learningBet, "2026-2",
                        kr("Actualizar 6 laboratorios con ambientes reproducibles.", "Laboratorios actualizados", "Numero", "2026-2", 6, 68, "hugo.arboleda@icesi.edu.co", "laboratorios reproducibles de nube e IA", true),
                        kr("Publicar 10 guias tecnicas para practicas de IA.", "Guias publicadas", "Numero", "2026-2", 10, 60, "leonardo.bustamante@icesi.edu.co", "guias tecnicas de IA aplicada", false)),
                initiative("Robustecer practicas de ciberseguridad aplicada", "Integrar ejercicios, politicas y evaluaciones de seguridad en proyectos academicos y servicios internos.", COMPUTING_DEPARTMENT, campusGoal, campusBet, "2026-2",
                        kr("Ejecutar 4 ejercicios de ciberseguridad aplicada.", "Ejercicios ejecutados", "Numero", "2026-2", 4, 45, "felipe.arango@icesi.edu.co", "ejercicios de ciberseguridad aplicada", true),
                        kr("Documentar 8 controles base para proyectos digitales.", "Controles documentados", "Numero", "2026-2", 8, 37, "hugo.arboleda@icesi.edu.co", "controles base de seguridad", false)),
                initiative("Integrar observabilidad de servicios digitales academicos", "Medir disponibilidad, tiempos de respuesta y experiencia de uso de servicios digitales criticos.", COMPUTING_DEPARTMENT, campusGoal, campusBet, "2026-2",
                        kr("Instrumentar 5 servicios academicos con metricas operativas.", "Servicios instrumentados", "Numero", "2026-2", 5, 28, "hugo.arboleda@icesi.edu.co", "observabilidad de servicios academicos", true)),
                initiative("Preparar repositorio de componentes reutilizables", "Definir arquitectura y gobierno para componentes reutilizables de software academico.", COMPUTING_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2",
                        kr("Definir backlog de 15 componentes reutilizables.", "Componentes definidos", "Numero", "2026-2", 15, 0, "leonardo.bustamante@icesi.edu.co", "backlog de componentes reutilizables", false)),

                initiative("Consolidar sistema de experiencia de usuario TDI", "Unificar patrones, criterios y mediciones de experiencia para plataformas y servicios de la escuela.", DESIGN_DEPARTMENT, campusGoal, campusBet, "2026-1",
                        kr("Publicar 24 patrones de experiencia validados.", "Patrones publicados", "Numero", "2026-1", 24, 100, "carlos.mejia@icesi.edu.co", "sistema de patrones UX TDI", true),
                        kr("Aplicar pruebas de usabilidad a 6 servicios priorizados.", "Servicios evaluados", "Numero", "2026-1", 6, 100, "mariana.torres@icesi.edu.co", "pruebas de usabilidad de servicios", false)),
                initiative("Escalar laboratorios de prototipado con aliados", "Aumentar la capacidad de prototipado rapido con organizaciones y retos de innovacion aplicada.", DESIGN_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2",
                        kr("Ejecutar 9 sprints de prototipado con aliados.", "Sprints ejecutados", "Numero", "2026-2", 9, 78, "carlos.mejia@icesi.edu.co", "sprints de prototipado con aliados", true),
                        kr("Validar 12 prototipos con usuarios reales.", "Prototipos validados", "Numero", "2026-2", 12, 70, "mariana.torres@icesi.edu.co", "validacion de prototipos con usuarios", false),
                        kr("Documentar 5 casos de aprendizaje transferible.", "Casos documentados", "Numero", "2026-2", 5, 64, "valentina.ruiz@icesi.edu.co", "casos de aprendizaje de prototipado", true)),
                initiative("Mejorar identidad visual de rutas flexibles", "Actualizar lenguaje visual y piezas de comunicacion para rutas academicas flexibles y posgraduales.", DESIGN_DEPARTMENT, learningGoal, learningBet, "2026-2",
                        kr("Redisenar 10 piezas de comunicacion de rutas flexibles.", "Piezas redisenadas", "Numero", "2026-2", 10, 62, "mariana.torres@icesi.edu.co", "piezas de rutas flexibles", true),
                        kr("Probar 4 narrativas visuales con estudiantes prospecto.", "Narrativas probadas", "Numero", "2026-2", 4, 54, "carlos.mejia@icesi.edu.co", "narrativas visuales para prospectos", false)),
                initiative("Implementar medicion continua de experiencia", "Instalar instrumentos y rutinas de lectura para mejorar experiencias academicas y administrativas.", DESIGN_DEPARTMENT, campusGoal, campusBet, "2027-1",
                        kr("Levantar 6 mapas de experiencia priorizados.", "Mapas levantados", "Numero", "2027-1", 6, 40, "valentina.ruiz@icesi.edu.co", "mapas de experiencia priorizados", true),
                        kr("Cerrar 12 hallazgos de mejora de servicio.", "Hallazgos cerrados", "Numero", "2027-1", 12, 34, "carlos.mejia@icesi.edu.co", "cierre de hallazgos de servicio", false)),
                initiative("Pilotos de accesibilidad digital", "Mejorar accesibilidad de recursos y plataformas usados por estudiantes y profesores.", DESIGN_DEPARTMENT, communityGoal, communityBet, "2027-1",
                        kr("Auditar 8 recursos digitales con criterios WCAG.", "Recursos auditados", "Numero", "2027-1", 8, 22, "valentina.ruiz@icesi.edu.co", "auditoria de accesibilidad digital", true)),
                initiative("Banco de servicios de diseno para aliados", "Preparar un catalogo de servicios de diseno e innovacion para proyectos con aliados externos.", DESIGN_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2",
                        kr("Definir 10 servicios de diseno listos para oferta.", "Servicios definidos", "Numero", "2026-2", 10, 0, "carlos.mejia@icesi.edu.co", "catalogo de servicios de diseno", false)),

                initiative("Fortalecer ruta de matematica aplicada", "Alinear cursos, ejercicios y recursos de matematica aplicada con retos de datos e ingenieria.", SCIENCES_DEPARTMENT, learningGoal, learningBet, "2026-1",
                        kr("Actualizar 7 modulos de matematica aplicada.", "Modulos actualizados", "Numero", "2026-1", 7, 100, "laura.gomez@icesi.edu.co", "modulos de matematica aplicada", true),
                        kr("Crear 14 bancos de ejercicios contextualizados.", "Bancos de ejercicios", "Numero", "2026-1", 14, 100, "laura.gomez@icesi.edu.co", "bancos de ejercicios contextualizados", false)),
                initiative("Escalar analitica de energia y sostenibilidad", "Expandir proyectos de modelacion, prediccion y visualizacion para retos de energia y sostenibilidad.", SCIENCES_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2",
                        kr("Validar 4 modelos predictivos para energia.", "Modelos validados", "Numero", "2026-2", 4, 80, "laura.gomez@icesi.edu.co", "modelos predictivos de energia", true),
                        kr("Publicar 3 tableros de sostenibilidad aplicada.", "Tableros publicados", "Numero", "2026-2", 3, 72, "laura.gomez@icesi.edu.co", "tableros de sostenibilidad aplicada", false),
                        kr("Vincular 5 aliados a casos de modelacion.", "Aliados vinculados", "Numero", "2026-2", 5, 64, "laura.gomez@icesi.edu.co", "casos de modelacion con aliados", true)),
                initiative("Integrar simuladores para cursos base", "Desarrollar simuladores y visualizadores para apoyar cursos fundamentales de ciencias exactas.", SCIENCES_DEPARTMENT, learningGoal, learningBet, "2026-2",
                        kr("Implementar 6 simuladores interactivos en cursos base.", "Simuladores implementados", "Numero", "2026-2", 6, 66, "laura.gomez@icesi.edu.co", "simuladores interactivos de ciencias", true),
                        kr("Medir uso de simuladores en 10 grupos academicos.", "Grupos medidos", "Numero", "2026-2", 10, 58, "laura.gomez@icesi.edu.co", "medicion de uso de simuladores", false)),
                initiative("Articular semilleros de ciencia de datos", "Fortalecer semilleros y rutas de participacion estudiantil en ciencia de datos aplicada.", SCIENCES_DEPARTMENT, facultyGoal, facultyBet, "2027-1",
                        kr("Acompanhar 5 semilleros con retos aplicados.", "Semilleros acompanados", "Numero", "2027-1", 5, 48, "laura.gomez@icesi.edu.co", "semilleros con retos aplicados", true),
                        kr("Registrar 30 estudiantes en actividades de semillero.", "Estudiantes registrados", "Numero", "2027-1", 30, 40, "laura.gomez@icesi.edu.co", "registro de estudiantes de semillero", false)),
                initiative("Mejorar acompanamiento en ciencias basicas", "Probar estrategias de acompanamiento y seguimiento para estudiantes en cursos de alta dificultad.", SCIENCES_DEPARTMENT, talentGoal, talentBet, "2027-1",
                        kr("Ejecutar 10 sesiones de acompanamiento focalizado.", "Sesiones ejecutadas", "Numero", "2027-1", 10, 31, "laura.gomez@icesi.edu.co", "acompanamiento en ciencias basicas", true)),
                initiative("Observatorio de datos experimentales", "Preparar capacidades para recopilar y compartir datos experimentales de cursos y proyectos.", SCIENCES_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2",
                        kr("Definir 6 protocolos de datos experimentales.", "Protocolos definidos", "Numero", "2026-2", 6, 0, "laura.gomez@icesi.edu.co", "protocolos de datos experimentales", false)),

                initiative("Optimizar arquitectura de integraciones academicas", "Mejorar integraciones entre plataformas academicas, analitica y seguimiento de proyectos del departamento.", COMPUTING_DEPARTMENT, campusGoal, campusBet, "2026-1",
                        kr("Cerrar 10 integraciones academicas estabilizadas.", "Integraciones estabilizadas", "Numero", "2026-1", 10, 100, "hugo.arboleda@icesi.edu.co", "integraciones academicas estabilizadas", true),
                        kr("Reducir incidentes repetitivos de integracion en 80 por ciento.", "Reduccion de incidentes", "Porcentaje", "2026-1", 100, 100, "felipe.arango@icesi.edu.co", "reduccion de incidentes de integracion", false)),
                initiative("Automatizar trazabilidad de proyectos de software", "Vincular KRs, proyectos, repositorios y evidencias para seguimiento de proyectos academicos de software.", COMPUTING_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2",
                        kr("Trazar 35 relaciones entre KRs, proyectos y repositorios.", "Relaciones trazadas", "Numero", "2026-2", 35, 82, "hugo.arboleda@icesi.edu.co", "trazabilidad de proyectos de software", true),
                        kr("Publicar 6 vistas de avance para equipos docentes.", "Vistas publicadas", "Numero", "2026-2", 6, 74, "leonardo.bustamante@icesi.edu.co", "vistas de avance de software", false),
                        kr("Socializar avances con 5 comites de programa.", "Comites socializados", "Numero", "2026-2", 5, 68, "rocio.segovia@icesi.edu.co", "socializacion de avances de software", true)),
                initiative("Escalar soporte tecnico para laboratorios TDI", "Usar datos de solicitudes y satisfaccion para mejorar tiempos, priorizacion y calidad del soporte tecnico academico.", COMPUTING_DEPARTMENT, campusGoal, campusBet, "2026-2",
                        kr("Clasificar 1200 solicitudes con categorias normalizadas.", "Solicitudes clasificadas", "Numero", "2026-2", 1200, 84, "felipe.arango@icesi.edu.co", "clasificacion de solicitudes tecnicas", true),
                        kr("Automatizar 15 respuestas a solicitudes frecuentes.", "Respuestas automatizadas", "Numero", "2026-2", 15, 78, "hugo.arboleda@icesi.edu.co", "respuestas automatizadas de soporte", false),
                        kr("Elevar satisfaccion de soporte tecnico a 82 puntos.", "Satisfaccion soporte", "Indice", "2026-2", 100, 70, "leonardo.bustamante@icesi.edu.co", "medicion de satisfaccion de soporte", true)),
                initiative("Preparar continuidad operativa de plataformas academicas", "Disenar practicas y evidencias para mantener plataformas academicas ante incidentes o cambios mayores.", COMPUTING_DEPARTMENT, campusGoal, campusBet, "2026-2",
                        kr("Documentar 5 procedimientos de continuidad operativa.", "Procedimientos documentados", "Numero", "2026-2", 5, 0, "hugo.arboleda@icesi.edu.co", "procedimientos de continuidad operativa academica", false)),

                initiative("Gobernar portafolio de experiencias digitales", "Ordenar prioridades, patrones y rituales de decision para experiencias digitales de la escuela.", DESIGN_DEPARTMENT, campusGoal, campusBet, "2026-1",
                        kr("Aprobar 18 iniciativas priorizadas de experiencia digital.", "Iniciativas aprobadas", "Numero", "2026-1", 18, 100, "carlos.mejia@icesi.edu.co", "priorizacion de experiencias digitales", true),
                        kr("Cerrar 4 ciclos de revision de experiencia.", "Ciclos cerrados", "Numero", "2026-1", 4, 100, "mariana.torres@icesi.edu.co", "revision de experiencia digital", false)),
                initiative("Medir valor de iniciativas de innovacion", "Preparar criterios y mediciones para valorar beneficios academicos, operativos y de experiencia.", DESIGN_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2",
                        kr("Definir 9 indicadores de beneficios por iniciativa.", "Indicadores definidos", "Numero", "2026-2", 9, 82, "carlos.mejia@icesi.edu.co", "indicadores de beneficios de innovacion", true),
                        kr("Publicar 6 tableros de beneficios para aliados.", "Tableros publicados", "Numero", "2026-2", 6, 76, "mariana.torres@icesi.edu.co", "tableros de beneficios de innovacion", false),
                        kr("Validar 5 casos de valor con usuarios y aliados.", "Casos validados", "Numero", "2026-2", 5, 68, "valentina.ruiz@icesi.edu.co", "casos de valor de innovacion", true)),
                initiative("Alinear capacidades de diseno con retos estrategicos", "Identificar brechas y activar rutas de desarrollo para capacidades de diseno e innovacion aplicada.", DESIGN_DEPARTMENT, facultyGoal, facultyBet, "2026-2",
                        kr("Mapear 40 capacidades de diseno frente a apuestas.", "Capacidades mapeadas", "Numero", "2026-2", 40, 70, "mariana.torres@icesi.edu.co", "mapa de capacidades de diseno", true),
                        kr("Activar 6 rutas de desarrollo en innovacion.", "Rutas activadas", "Numero", "2026-2", 6, 62, "carlos.mejia@icesi.edu.co", "rutas de desarrollo en innovacion", false)),
                initiative("Preparar catalogo de beneficios de experiencia", "Construir criterios y evidencias para comunicar beneficios de servicios de diseno a aliados.", DESIGN_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2",
                        kr("Definir 9 fichas de beneficios de experiencia.", "Fichas definidas", "Numero", "2026-2", 9, 0, "valentina.ruiz@icesi.edu.co", "catalogo de beneficios de experiencia", false)),

                initiative("Consolidar portafolio de modelacion aplicada", "Ordenar prioridades y evidencias de proyectos de modelacion cuantitativa con impacto academico y externo.", SCIENCES_DEPARTMENT, alliancesGoal, alliancesBet, "2026-1",
                        kr("Aprobar 12 casos priorizados de modelacion aplicada.", "Casos aprobados", "Numero", "2026-1", 12, 100, "laura.gomez@icesi.edu.co", "priorizacion de modelacion aplicada", true),
                        kr("Cerrar 4 revisiones tecnicas del portafolio.", "Revisiones cerradas", "Numero", "2026-1", 4, 100, "laura.gomez@icesi.edu.co", "revision tecnica de modelacion", false)),
                initiative("Mejorar trazabilidad de laboratorios cuantitativos", "Centralizar evidencias, responsables y resultados de laboratorios de ciencias exactas.", SCIENCES_DEPARTMENT, campusGoal, campusBet, "2026-2",
                        kr("Trazar 24 laboratorios con responsables y evidencias.", "Laboratorios trazados", "Numero", "2026-2", 24, 80, "laura.gomez@icesi.edu.co", "trazabilidad de laboratorios cuantitativos", true),
                        kr("Publicar 6 vistas de avance para cursos base.", "Vistas publicadas", "Numero", "2026-2", 6, 72, "laura.gomez@icesi.edu.co", "vistas de avance de laboratorios", false),
                        kr("Socializar avances con 5 equipos docentes.", "Equipos socializados", "Numero", "2026-2", 5, 64, "laura.gomez@icesi.edu.co", "socializacion de laboratorios cuantitativos", true)),
                initiative("Implementar tablero de riesgos academicos en ciencias", "Crear alertas y responsables para cursos de alta dificultad y laboratorios criticos.", SCIENCES_DEPARTMENT, talentGoal, talentBet, "2026-2",
                        kr("Configurar 12 riesgos academicos con responsables.", "Riesgos configurados", "Numero", "2026-2", 12, 50, "laura.gomez@icesi.edu.co", "riesgos academicos en ciencias", true),
                        kr("Escalar 8 alertas a equipos de curso.", "Alertas escaladas", "Numero", "2026-2", 8, 40, "laura.gomez@icesi.edu.co", "alertas academicas en ciencias", false)),
                initiative("Definir repositorio de indicadores experimentales", "Preparar inventario, protocolos y responsables para indicadores experimentales reutilizables.", SCIENCES_DEPARTMENT, alliancesGoal, alliancesBet, "2026-2",
                        kr("Identificar 25 indicadores experimentales criticos.", "Indicadores identificados", "Numero", "2026-2", 25, 0, "laura.gomez@icesi.edu.co", "repositorio de indicadores experimentales", false))
        );
    }

    private static ExpandedInitiativeSeed initiative(
            String name,
            String description,
            String departmentName,
            String goalName,
            String strategicBetName,
            String periodName,
            ExpandedKeyResultSeed... keyResults
    ) {
        return new ExpandedInitiativeSeed(name, description, departmentName, goalName, strategicBetName, periodName, List.of(keyResults));
    }

    private static ExpandedKeyResultSeed kr(
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

    /**
     * Agrupa KRs de demo que comparten departamento, Meta y Apuesta; no es una entidad persistida.
     */
    private record ExpandedInitiativeSeed(
            String name,
            String description,
            String departmentName,
            String goalName,
            String strategicBetName,
            String periodName,
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
}
