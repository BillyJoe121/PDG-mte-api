package co.edu.icesi.pdg.mte.config;

import co.edu.icesi.pdg.mte.catalog.*;
import co.edu.icesi.pdg.mte.strategy.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos de demo de la jerarquia estrategica: apuestas, metas y KRs base.
 */
final class SeedStrategicHierarchyData {
    static final String KR_PROSPECTS = "Priorizar 1000 prospectos con modelos analiticos para el final de Q3.";
    static final String KR_COMPETITIVE_PROGRAMMING = "Entrenar 120 estudiantes en programacion competitiva para el final de Q3.";
    static final String KR_POSTGRADUATE_OFFER = "Actualizar el 90% de la oferta posgradual en plataforma para el final de Q3.";
    static final String KR_INTERACTIVE_COURSES = "Intervenir 12 cursos con recursos interactivos inteligentes para el final de Q3.";
    static final String KR_AI_FACULTY = "Habilitar 60 profesores en entornos de IA y mejora continua para el final de Q3.";
    static final String KR_APPLIED_CHALLENGES = "Aplicar 18 retos con participacion docente para el final de Q3.";
    static final String KR_CONSULTING_ASSETS = "Crear 10 activos consultivos reutilizables para el final de Q3.";
    static final String KR_APPLIED_PROTOTYPES = "Validar 8 prototipos de innovacion aplicada con aliados para el final de Q3.";
    static final String KR_EMPLOYABILITY_CONNECTIONS = "Activar 300 conexiones de empleabilidad para el final de Q3.";
    static final String KR_MICROCREDENTIALS = "Lanzar 15 microcredenciales para aprendizaje permanente para el final de Q3.";
    static final String KR_DIGITAL_SERVICES = "Alcanzar un indice de satisfaccion digital de 90 en servicios de campus para el final de Q3.";
    static final String KR_SERVICE_CARE = "Implementar 20 acciones de servicio y cuidado para el final de Q3.";
    static final String KR_UNLINKED_STUDENT_SUCCESS = "Reducir desercion temprana en 10 puntos porcentuales para el final de Q4.";
    static final String KR_UNLINKED_PARTNER_PORTAL = "Publicar portal de aliados estrategicos con 30 organizaciones para el final de Q4.";

    private SeedStrategicHierarchyData() {
    }

    static void seedStrategicHierarchy(
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            WorldRepository worldRepository,
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
            KeyResultRepository keyResultRepository
    ) {
        World world = worldRepository.findByNameIgnoreCase("Mundo Institucional").orElse(null);
        MeasurementUnit percentage = unitRepository.findByNameIgnoreCase("Porcentaje").orElse(null);
        MeasurementUnit number = unitRepository.findByNameIgnoreCase("Numero").orElse(null);
        MeasurementUnit index = unitRepository.findByNameIgnoreCase("Indice").orElse(null);
        AcademicPeriod period20261 = periodRepository.findByNameIgnoreCase("2026-1").orElse(null);
        AcademicPeriod period20262 = periodRepository.findByNameIgnoreCase("2026-2").orElse(null);
        AcademicPeriod period20271 = periodRepository.findByNameIgnoreCase("2027-1").orElse(null);
        if (world == null || percentage == null || number == null || index == null
                || period20261 == null || period20262 == null || period20271 == null) {
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

        seedKeyResult(keyResultRepository, KR_PROSPECTS, "Aumentar la proporcion de prospectos priorizados con modelos de analitica comercial.", "Prospectos priorizados", BigDecimal.ZERO, BigDecimal.valueOf(1000), BigDecimal.valueOf(380), BigDecimal.valueOf(38), number, period20261, talentGoal, talentBet);
        seedKeyResult(keyResultRepository, KR_COMPETITIVE_PROGRAMMING, "Incrementar estudiantes que completan entrenamiento inicial del club de programacion competitiva.", "Estudiantes entrenados", BigDecimal.ZERO, BigDecimal.valueOf(120), BigDecimal.valueOf(36), BigDecimal.valueOf(30), number, period20261, talentGoal, talentBet);
        seedKeyResult(keyResultRepository, KR_POSTGRADUATE_OFFER, "Publicar programas y rutas posgraduales con informacion completa, vigente y trazable.", "Oferta actualizada", BigDecimal.ZERO, BigDecimal.valueOf(90), BigDecimal.valueOf(38), BigDecimal.valueOf(42), percentage, period20261, learningGoal, learningBet);
        seedKeyResult(keyResultRepository, KR_INTERACTIVE_COURSES, "Implementar recursos inteligentes en asignaturas priorizadas.", "Cursos intervenidos", BigDecimal.ZERO, BigDecimal.valueOf(12), BigDecimal.valueOf(4), BigDecimal.valueOf(35), number, period20262, learningGoal, learningBet);
        seedKeyResult(keyResultRepository, KR_AI_FACULTY, "Habilitar profesores y colaboradores en herramientas de IA aplicada, automatizacion y mejora continua.", "Profesores habilitados", BigDecimal.ZERO, BigDecimal.valueOf(60), BigDecimal.valueOf(24), BigDecimal.valueOf(40), number, period20261, facultyGoal, facultyBet);
        seedKeyResult(keyResultRepository, KR_APPLIED_CHALLENGES, "Vincular profesores a retos con organizaciones y dominios de frontera.", "Retos aplicados", BigDecimal.ZERO, BigDecimal.valueOf(18), BigDecimal.valueOf(5), BigDecimal.valueOf(28), number, period20262, facultyGoal, facultyBet);
        seedKeyResult(keyResultRepository, KR_CONSULTING_ASSETS, "Crear agentes, diagnosticos y modelos reutilizables para servicios de consultoria.", "Activos consultivos", BigDecimal.ZERO, BigDecimal.valueOf(10), BigDecimal.valueOf(5), BigDecimal.valueOf(45), number, period20261, alliancesGoal, alliancesBet);
        seedKeyResult(keyResultRepository, KR_APPLIED_PROTOTYPES, "Desarrollar prototipos validados con organizaciones externas.", "Prototipos validados", BigDecimal.ZERO, BigDecimal.valueOf(8), BigDecimal.valueOf(3), BigDecimal.valueOf(32), number, period20262, alliancesGoal, alliancesBet);
        seedKeyResult(keyResultRepository, KR_EMPLOYABILITY_CONNECTIONS, "Activar conexiones entre comunidad extendida, aliados y oportunidades profesionales.", "Conexiones activas", BigDecimal.ZERO, BigDecimal.valueOf(300), BigDecimal.valueOf(75), BigDecimal.valueOf(25), number, period20262, communityGoal, communityBet);
        seedKeyResult(keyResultRepository, KR_MICROCREDENTIALS, "Lanzar rutas cortas actualizables para egresados, profesionales y aliados.", "Microcredenciales lanzadas", BigDecimal.ZERO, BigDecimal.valueOf(15), BigDecimal.valueOf(3), BigDecimal.valueOf(22), number, period20262, communityGoal, communityBet);
        seedKeyResult(keyResultRepository, KR_DIGITAL_SERVICES, "Mejorar servicios digitales criticos y aumentar su satisfaccion de uso.", "Indice de satisfaccion digital", BigDecimal.ZERO, BigDecimal.valueOf(90), BigDecimal.valueOf(36), BigDecimal.valueOf(36), index, period20262, campusGoal, campusBet);
        seedKeyResult(keyResultRepository, KR_SERVICE_CARE, "Implementar acciones de mejora derivadas de mediciones de experiencia.", "Acciones implementadas", BigDecimal.ZERO, BigDecimal.valueOf(20), BigDecimal.valueOf(4), BigDecimal.valueOf(20), number, period20271, campusGoal, campusBet);
        seedKeyResult(keyResultRepository, KR_UNLINKED_STUDENT_SUCCESS, "KR intencional de demo sin proyectos activos vinculados.", "Reduccion de desercion", BigDecimal.ZERO, BigDecimal.valueOf(10), BigDecimal.ZERO, BigDecimal.ZERO, percentage, period20261, talentGoal, talentBet);
        seedKeyResult(keyResultRepository, KR_UNLINKED_PARTNER_PORTAL, "KR intencional de demo sin proyectos vinculados para consistencia.", "Organizaciones en portal", BigDecimal.ZERO, BigDecimal.valueOf(30), BigDecimal.ZERO, BigDecimal.ZERO, number, period20262, alliancesGoal, alliancesBet);
    }

    private static StrategicBet seedStrategicBet(StrategicBetRepository repository, String name, String description, World world) {
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

    private static InstitutionalGoal seedGoal(
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

    static KeyResult seedKeyResult(
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
            InstitutionalGoal goal,
            StrategicBet strategicBet
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
                    keyResult.getGoals().add(goal);
                    keyResult.getStrategicBets().add(strategicBet);
                    return repository.save(keyResult);
                });
    }
}
