package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.catalog.*;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class StrategyService {

    private final StrategicBetRepository strategicBetRepository;
    private final InstitutionalGoalRepository goalRepository;
    private final ObjectiveRepository objectiveRepository;
    private final KeyResultRepository keyResultRepository;
    private final MeasurementUnitRepository unitRepository;
    private final AcademicPeriodRepository periodRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectKeyResultLinkRepository linkRepository;

    public StrategyService(
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
            ObjectiveRepository objectiveRepository,
            KeyResultRepository keyResultRepository,
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            DepartmentRepository departmentRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        this.strategicBetRepository = strategicBetRepository;
        this.goalRepository = goalRepository;
        this.objectiveRepository = objectiveRepository;
        this.keyResultRepository = keyResultRepository;
        this.unitRepository = unitRepository;
        this.periodRepository = periodRepository;
        this.departmentRepository = departmentRepository;
        this.linkRepository = linkRepository;
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.StrategicBetResponse> listStrategicBets(String period) {
        return strategicBetRepository.findAll()
                .stream()
                .map(bet -> Mapper.toResponse(bet, executionSummaryForBet(bet.getId(), period)))
                .toList();
    }

    public StrategyDtos.StrategicBetResponse createStrategicBet(StrategyDtos.StrategicBetRequest request) {
        validateDateRange(request.startDate(), request.endDate());
        if (strategicBetRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe una apuesta estrategica con ese nombre.");
        }
        StrategicBet bet = new StrategicBet();
        bet.setName(request.name().trim());
        bet.setDescription(request.description().trim());
        bet.setStartDate(request.startDate());
        bet.setEndDate(request.endDate());
        return Mapper.toResponse(strategicBetRepository.save(bet), emptyExecutionSummary());
    }

    @Transactional(readOnly = true)
    public StrategyDtos.StrategicBetResponse getStrategicBet(Long id, String period) {
        StrategicBet bet = findStrategicBet(id);
        return Mapper.toResponse(bet, executionSummaryForBet(id, period));
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.GoalResponse> listGoals(String period) {
        return goalRepository.findAll().stream()
                .map(goal -> Mapper.toResponse(goal, executionSummaryForGoal(goal.getId(), period)))
                .toList();
    }

    public StrategyDtos.GoalResponse createGoal(StrategyDtos.GoalRequest request) {
        validateDateRange(request.startDate(), request.endDate());
        InstitutionalGoal goal = new InstitutionalGoal();
        goal.setName(request.name().trim());
        goal.setDescription(request.description().trim());
        goal.setReferenceIndicator(request.referenceIndicator());
        goal.setExpectedValue(request.expectedValue());
        goal.setStartDate(request.startDate());
        goal.setEndDate(request.endDate());
        goal.setMeasurementUnit(findUnit(request.measurementUnitId()));
        return Mapper.toResponse(goalRepository.save(goal));
    }

    @Transactional(readOnly = true)
    public StrategyDtos.GoalResponse getGoal(Long id, String period) {
        return Mapper.toResponse(findGoal(id), executionSummaryForGoal(id, period));
    }

    public StrategyDtos.GoalResponse attachGoalPeriod(Long goalId, Long periodId) {
        InstitutionalGoal goal = findGoal(goalId);
        goal.getPeriods().add(findPeriod(periodId));
        return Mapper.toResponse(goalRepository.save(goal));
    }

    public StrategyDtos.GoalResponse detachGoalPeriod(Long goalId, Long periodId) {
        InstitutionalGoal goal = findGoal(goalId);
        goal.getPeriods().removeIf(period -> period.getId().equals(periodId));
        return Mapper.toResponse(goalRepository.save(goal));
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.ObjectiveResponse> listObjectives(Long strategicBetId, Long goalId, Long departmentId, Long periodId) {
        return findObjectives(strategicBetId, goalId, departmentId, periodId).stream().map(Mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.ObjectiveCardResponse> listObjectiveCards(Long strategicBetId, Long goalId, Long departmentId, Long periodId) {
        return findObjectives(strategicBetId, goalId, departmentId, periodId)
                .stream()
                .map(this::toObjectiveCard)
                .toList();
    }

    private List<Objective> findObjectives(Long strategicBetId, Long goalId, Long departmentId, Long periodId) {
        Specification<Objective> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (strategicBetId != null) {
                predicates.add(builder.equal(root.get("strategicBet").get("id"), strategicBetId));
            }
            if (goalId != null) {
                predicates.add(builder.equal(root.get("goal").get("id"), goalId));
            }
            if (departmentId != null) {
                predicates.add(builder.equal(root.get("department").get("id"), departmentId));
            }
            if (periodId != null) {
                predicates.add(builder.equal(root.get("academicPeriod").get("id"), periodId));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return objectiveRepository.findAll(specification);
    }

    public StrategyDtos.ObjectiveResponse createObjective(StrategyDtos.ObjectiveRequest request) {
        Objective objective = new Objective();
        objective.setName(request.name().trim());
        objective.setDescription(request.description().trim());
        objective.setDepartment(findDepartment(request.departmentId()));
        objective.setAcademicPeriod(findPeriod(request.academicPeriodId()));
        objective.setGoal(findGoal(request.goalId()));
        objective.setStrategicBet(findStrategicBet(request.strategicBetId()));
        objective.setCreatedByExternalUserId(currentExternalUserId());

        for (StrategyDtos.KeyResultRequest keyResultRequest : request.keyResults()) {
            objective.addKeyResult(buildKeyResult(keyResultRequest));
        }
        return Mapper.toResponse(objectiveRepository.save(objective));
    }

    @Transactional(readOnly = true)
    public StrategyDtos.ObjectiveResponse getObjective(Long id) {
        return Mapper.toResponse(findObjective(id));
    }

    public StrategyDtos.ObjectiveResponse updateObjective(Long id, StrategyDtos.ObjectiveUpdateRequest request) {
        Objective objective = findObjective(id);
        objective.setName(request.name().trim());
        objective.setDescription(request.description().trim());
        return Mapper.toResponse(objectiveRepository.save(objective));
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.StrategicHierarchyNodeResponse> getStrategicHierarchyTree(String period) {
        List<Objective> objectives = objectiveRepository.findAll();
        List<StrategyDtos.StrategicHierarchyNodeResponse> betRoots = strategicBetRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(StrategicBet::getName))
                .map(bet -> strategicBetNode(bet, objectives, period))
                .toList();
        List<StrategyDtos.StrategicHierarchyNodeResponse> goalRoots = goalRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(InstitutionalGoal::getName))
                .map(goal -> goalNode(goal, objectives, period))
                .toList();

        List<StrategyDtos.StrategicHierarchyNodeResponse> roots = new ArrayList<>();
        roots.addAll(betRoots);
        roots.addAll(goalRoots);
        return roots;
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.KeyResultResponse> listObjectiveKeyResults(Long objectiveId) {
        findObjective(objectiveId);
        return keyResultRepository.findByObjectiveIdOrderByIdAsc(objectiveId)
                .stream()
                .map(Mapper::toResponse)
                .toList();
    }

    public StrategyDtos.KeyResultResponse addKeyResult(Long objectiveId, StrategyDtos.KeyResultRequest request) {
        Objective objective = findObjective(objectiveId);
        KeyResult keyResult = buildKeyResult(request);
        keyResult.setObjective(objective);
        return Mapper.toResponse(keyResultRepository.save(keyResult));
    }

    public StrategyDtos.KeyResultResponse updateKeyResult(Long keyResultId, StrategyDtos.KeyResultRequest request) {
        KeyResult keyResult = findKeyResult(keyResultId);
        keyResult.setDescription(request.description().trim());
        keyResult.setMetric(request.metric().trim());
        keyResult.setBaseValue(request.baseValue());
        keyResult.setTargetValue(request.targetValue());
        keyResult.setCurrentValue(request.currentValue());
        keyResult.setMeasurementUnit(findUnit(request.measurementUnitId()));
        keyResult.recalculateProgress();
        return Mapper.toResponse(keyResultRepository.save(keyResult));
    }

    public StrategyDtos.KeyResultResponse updateCurrentValue(Long keyResultId, StrategyDtos.KeyResultCurrentValueRequest request) {
        KeyResult keyResult = findKeyResult(keyResultId);
        keyResult.setCurrentValue(request.currentValue());
        keyResult.recalculateProgress();
        return Mapper.toResponse(keyResultRepository.save(keyResult));
    }

    public void deleteKeyResult(Long keyResultId) {
        KeyResult keyResult = findKeyResult(keyResultId);
        long activeLinks = linkRepository.countByKeyResultIdAndActiveTrue(keyResultId);
        if (activeLinks > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "No se puede eliminar un KR con proyectos vinculados.");
        }
        keyResultRepository.delete(keyResult);
    }

    private StrategyDtos.ObjectiveCardResponse toObjectiveCard(Objective objective) {
        BigDecimal completion = objective.completionPercentage();
        return new StrategyDtos.ObjectiveCardResponse(
                objective.getId(),
                objective.getName(),
                objective.getDescription(),
                objective.getStrategicBet().getId(),
                objective.getStrategicBet().getName(),
                objective.getGoal().getId(),
                objective.getGoal().getName(),
                objective.getDepartment().getId(),
                objective.getDepartment().getName(),
                objective.getAcademicPeriod().getId(),
                objective.getAcademicPeriod().getName(),
                completion,
                completion.compareTo(BigDecimal.valueOf(30)) < 0,
                objective.getKeyResults().stream().map(Mapper::toResponse).toList()
        );
    }

    private StrategyDtos.StrategicHierarchyNodeResponse strategicBetNode(StrategicBet bet, List<Objective> objectives, String period) {
        List<StrategyDtos.StrategicHierarchyNodeResponse> children = objectives.stream()
                .filter(objective -> objective.getStrategicBet().getId().equals(bet.getId()))
                .sorted(Comparator.comparing(Objective::getName))
                .map(objective -> objectiveNode(objective, "Meta: " + objective.getGoal().getName()))
                .toList();
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "STRATEGIC_BET",
                bet.getId().toString(),
                bet.getName(),
                bet.getDescription(),
                null,
                executionSummaryForBet(bet.getId(), period),
                null,
                children
        );
    }

    private StrategyDtos.StrategicHierarchyNodeResponse goalNode(InstitutionalGoal goal, List<Objective> objectives, String period) {
        List<StrategyDtos.StrategicHierarchyNodeResponse> children = objectives.stream()
                .filter(objective -> objective.getGoal().getId().equals(goal.getId()))
                .sorted(Comparator.comparing(Objective::getName))
                .map(objective -> objectiveNode(objective, "Apuesta: " + objective.getStrategicBet().getName()))
                .toList();
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "GOAL",
                goal.getId().toString(),
                goal.getName(),
                goal.getDescription(),
                null,
                executionSummaryForGoal(goal.getId(), period),
                goal.getMeasurementUnit().getName(),
                children
        );
    }

    private StrategyDtos.StrategicHierarchyNodeResponse objectiveNode(Objective objective, String badge) {
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "OBJECTIVE",
                objective.getId().toString(),
                objective.getName(),
                objective.getDescription(),
                objective.completionPercentage(),
                null,
                badge,
                objective.getKeyResults()
                        .stream()
                        .sorted(Comparator.comparing(KeyResult::getId))
                        .map(this::keyResultNode)
                        .toList()
        );
    }

    private StrategyDtos.StrategicHierarchyNodeResponse keyResultNode(KeyResult keyResult) {
        List<StrategyDtos.StrategicHierarchyNodeResponse> projectChildren = linkRepository
                .findByKeyResultIdAndActiveTrueOrderByIdAsc(keyResult.getId())
                .stream()
                .map(this::projectNode)
                .toList();
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "KEY_RESULT",
                keyResult.getId().toString(),
                keyResult.getDescription(),
                keyResult.getMetric(),
                keyResult.getProgressPercentage(),
                null,
                keyResult.getMeasurementUnit().getName(),
                projectChildren
        );
    }

    private StrategyDtos.StrategicHierarchyNodeResponse projectNode(ProjectKeyResultLink link) {
        if (link.getProject() != null) {
            var project = link.getProject();
            return new StrategyDtos.StrategicHierarchyNodeResponse(
                    "PROJECT",
                    project.getId().toString(),
                    project.getName(),
                    project.getDescription(),
                    project.getStatus() == ProjectStatus.FINALIZADO ? BigDecimal.valueOf(100) : BigDecimal.ZERO,
                    null,
                    "Peso: " + link.getContributionWeight() + "%",
                    List.of()
            );
        }
        String externalProjectId = link.getExternalProjectId() == null ? "sin-id" : link.getExternalProjectId().toString();
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "PROJECT",
                externalProjectId,
                "Proyecto externo " + externalProjectId,
                link.getExternalProjectSource(),
                null,
                null,
                "Peso: " + link.getContributionWeight() + "%",
                List.of()
        );
    }

    private KeyResult buildKeyResult(StrategyDtos.KeyResultRequest request) {
        KeyResult keyResult = new KeyResult();
        keyResult.setDescription(request.description().trim());
        keyResult.setMetric(request.metric().trim());
        keyResult.setBaseValue(request.baseValue());
        keyResult.setTargetValue(request.targetValue());
        keyResult.setCurrentValue(request.currentValue());
        keyResult.setMeasurementUnit(findUnit(request.measurementUnitId()));
        keyResult.recalculateProgress();
        return keyResult;
    }

    private void validateDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "La fecha de cierre debe ser posterior a la fecha de inicio.");
        }
    }

    private StrategyDtos.ExecutionSummaryResponse executionSummaryForBet(Long betId, String periodFilter) {
        List<Objective> objectives = objectiveRepository.findAll()
                .stream()
                .filter(objective -> objective.getStrategicBet().getId().equals(betId))
                .toList();
        return buildExecutionSummary(objectives, periodFilter);
    }

    private StrategyDtos.ExecutionSummaryResponse executionSummaryForGoal(Long goalId, String periodFilter) {
        List<Objective> objectives = objectiveRepository.findAll()
                .stream()
                .filter(objective -> objective.getGoal().getId().equals(goalId))
                .toList();
        return buildExecutionSummary(objectives, periodFilter);
    }

    private StrategyDtos.ExecutionSummaryResponse emptyExecutionSummary() {
        return new StrategyDtos.ExecutionSummaryResponse(
                "0 objetivos completos, 0 en desarrollo. 0 KPIs completos, 0 en desarrollo. 0 proyectos completados, 0 en desarrollo.",
                0,
                0,
                0,
                0,
                0,
                0,
                List.of()
        );
    }

    private StrategyDtos.ExecutionSummaryResponse buildExecutionSummary(List<Objective> objectives, String periodFilter) {
        Map<String, ExecutionAccumulator> byPeriod = new LinkedHashMap<>();
        ExecutionAccumulator total = new ExecutionAccumulator();

        for (Objective objective : objectives) {
            Set<String> objectivePeriods = new LinkedHashSet<>();
            for (KeyResult keyResult : objective.getKeyResults()) {
                List<ProjectKeyResultLink> links = linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(keyResult.getId());
                if (links.isEmpty()) {
                    String period = objective.getAcademicPeriod().getName();
                    if (periodMatches(period, periodFilter)) {
                        objectivePeriods.add(period);
                        byPeriod.computeIfAbsent(period, ExecutionAccumulator::new).addKeyResult(keyResult);
                    }
                    continue;
                }
                for (ProjectKeyResultLink link : links) {
                    Project project = link.getProject();
                    String period = periodOf(project, objective);
                    if (!periodMatches(period, periodFilter)) {
                        continue;
                    }
                    objectivePeriods.add(period);
                    byPeriod.computeIfAbsent(period, ExecutionAccumulator::new).addKeyResult(keyResult);
                    byPeriod.get(period).addProject(project);
                }
            }
            for (String period : objectivePeriods) {
                byPeriod.computeIfAbsent(period, ExecutionAccumulator::new).addObjective(objective);
            }
        }

        byPeriod.values().forEach(total::merge);
        List<StrategyDtos.PeriodExecutionSummaryResponse> periodResponses = byPeriod.values()
                .stream()
                .sorted(Comparator.comparing(ExecutionAccumulator::period))
                .map(ExecutionAccumulator::toPeriodResponse)
                .toList();
        return total.toResponse(periodResponses);
    }

    private boolean periodMatches(String period, String periodFilter) {
        return periodFilter == null || periodFilter.isBlank() || period.equals(periodFilter.trim());
    }

    private String periodOf(Project project, Objective fallbackObjective) {
        if (project == null) {
            return fallbackObjective.getAcademicPeriod().getName();
        }
        if (project.getEndPeriod() != null && !project.getEndPeriod().isBlank()) {
            return project.getEndPeriod();
        }
        if (project.getStartPeriod() != null && !project.getStartPeriod().isBlank()) {
            return project.getStartPeriod();
        }
        return fallbackObjective.getAcademicPeriod().getName();
    }

    private MeasurementUnit findUnit(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Unidad de medida no encontrada."));
    }

    private AcademicPeriod findPeriod(Long id) {
        return periodRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Periodo academico no encontrado."));
    }

    private Department findDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Departamento no encontrado."));
    }

    private StrategicBet findStrategicBet(Long id) {
        return strategicBetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Apuesta estrategica no encontrada."));
    }

    private InstitutionalGoal findGoal(Long id) {
        return goalRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Meta institucional no encontrada."));
    }

    private Objective findObjective(Long id) {
        return objectiveRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Objetivo no encontrado."));
    }

    private KeyResult findKeyResult(Long id) {
        return keyResultRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Key Result no encontrado."));
    }

    private Long currentExternalUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof ExternalUserContext context) {
            return context.externalUserId();
        }
        return null;
    }

    private static final class ExecutionAccumulator {
        private final String period;
        private final Set<Long> completedObjectives = new LinkedHashSet<>();
        private final Set<Long> inProgressObjectives = new LinkedHashSet<>();
        private final Set<Long> completedKeyResults = new LinkedHashSet<>();
        private final Set<Long> inProgressKeyResults = new LinkedHashSet<>();
        private final Set<Long> completedProjects = new LinkedHashSet<>();
        private final Set<Long> inProgressProjects = new LinkedHashSet<>();

        private ExecutionAccumulator() {
            this.period = "TOTAL";
        }

        private ExecutionAccumulator(String period) {
            this.period = period;
        }

        private String period() {
            return period;
        }

        private void addObjective(Objective objective) {
            if (objective.completionPercentage().compareTo(BigDecimal.valueOf(100)) >= 0) {
                completedObjectives.add(objective.getId());
                inProgressObjectives.remove(objective.getId());
                return;
            }
            if (!completedObjectives.contains(objective.getId())) {
                inProgressObjectives.add(objective.getId());
            }
        }

        private void addKeyResult(KeyResult keyResult) {
            BigDecimal progress = keyResult.getProgressPercentage() == null ? BigDecimal.ZERO : keyResult.getProgressPercentage();
            if (progress.compareTo(BigDecimal.valueOf(100)) >= 0) {
                completedKeyResults.add(keyResult.getId());
                inProgressKeyResults.remove(keyResult.getId());
                return;
            }
            if (!completedKeyResults.contains(keyResult.getId())) {
                inProgressKeyResults.add(keyResult.getId());
            }
        }

        private void addProject(Project project) {
            if (project == null || project.getId() == null) {
                return;
            }
            if (project.getStatus() == ProjectStatus.FINALIZADO) {
                completedProjects.add(project.getId());
                inProgressProjects.remove(project.getId());
                return;
            }
            if (!completedProjects.contains(project.getId())) {
                inProgressProjects.add(project.getId());
            }
        }

        private void merge(ExecutionAccumulator other) {
            completedObjectives.addAll(other.completedObjectives);
            inProgressObjectives.addAll(other.inProgressObjectives);
            inProgressObjectives.removeAll(completedObjectives);
            completedKeyResults.addAll(other.completedKeyResults);
            inProgressKeyResults.addAll(other.inProgressKeyResults);
            inProgressKeyResults.removeAll(completedKeyResults);
            completedProjects.addAll(other.completedProjects);
            inProgressProjects.addAll(other.inProgressProjects);
            inProgressProjects.removeAll(completedProjects);
        }

        private StrategyDtos.PeriodExecutionSummaryResponse toPeriodResponse() {
            return new StrategyDtos.PeriodExecutionSummaryResponse(
                    period,
                    completedObjectives.size(),
                    inProgressObjectives.size(),
                    completedKeyResults.size(),
                    inProgressKeyResults.size(),
                    completedProjects.size(),
                    inProgressProjects.size()
            );
        }

        private StrategyDtos.ExecutionSummaryResponse toResponse(List<StrategyDtos.PeriodExecutionSummaryResponse> periods) {
            return new StrategyDtos.ExecutionSummaryResponse(
                    "%d objetivos completos, %d en desarrollo. %d KPIs completos, %d en desarrollo. %d proyectos completados, %d en desarrollo."
                            .formatted(
                                    completedObjectives.size(),
                                    inProgressObjectives.size(),
                                    completedKeyResults.size(),
                                    inProgressKeyResults.size(),
                                    completedProjects.size(),
                                    inProgressProjects.size()
                            ),
                    completedObjectives.size(),
                    inProgressObjectives.size(),
                    completedKeyResults.size(),
                    inProgressKeyResults.size(),
                    completedProjects.size(),
                    inProgressProjects.size(),
                    periods
            );
        }
    }
}
