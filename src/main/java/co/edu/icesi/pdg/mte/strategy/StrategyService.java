package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.*;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class StrategyService {
    private final StrategicBetRepository strategicBetRepository;
    private final InstitutionalGoalRepository goalRepository;
    private final ObjectiveRepository objectiveRepository;
    private final KeyResultRepository keyResultRepository;
    private final WorldRepository worldRepository;
    private final ProfessorRepository professorRepository;
    private final MeasurementUnitRepository unitRepository;
    private final AcademicPeriodRepository periodRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectKeyResultLinkRepository linkRepository;
    private final AuditService auditService;
    private final StrategyExecutionSummaryService executionSummaryService;
    private final StrategicHierarchyTreeService hierarchyTreeService;
    private final ObjectiveCoverageTrendService coverageTrendService;

    public StrategyService(
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
            ObjectiveRepository objectiveRepository,
            KeyResultRepository keyResultRepository,
            WorldRepository worldRepository,
            ProfessorRepository professorRepository,
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            DepartmentRepository departmentRepository,
            ProjectKeyResultLinkRepository linkRepository,
            AuditService auditService,
            StrategyExecutionSummaryService executionSummaryService,
            StrategicHierarchyTreeService hierarchyTreeService,
            ObjectiveCoverageTrendService coverageTrendService
    ) {
        this.strategicBetRepository = strategicBetRepository;
        this.goalRepository = goalRepository;
        this.objectiveRepository = objectiveRepository;
        this.keyResultRepository = keyResultRepository;
        this.worldRepository = worldRepository;
        this.professorRepository = professorRepository;
        this.unitRepository = unitRepository;
        this.periodRepository = periodRepository;
        this.departmentRepository = departmentRepository;
        this.linkRepository = linkRepository;
        this.auditService = auditService;
        this.executionSummaryService = executionSummaryService;
        this.hierarchyTreeService = hierarchyTreeService;
        this.coverageTrendService = coverageTrendService;
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.WorldResponse> listWorlds() {
        return worldRepository.findAll().stream().map(Mapper::toResponse).toList();
    }

    public StrategyDtos.WorldResponse createWorld(StrategyDtos.WorldRequest request) {
        String name = request.name().trim();
        if (worldRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe un mundo con ese nombre.");
        }
        World world = new World();
        world.setName(name);
        world.setDescription(request.description());
        StrategyDtos.WorldResponse response = Mapper.toResponse(worldRepository.save(world));
        auditService.record(AuditAction.CREATE, "WORLD", response.id(), "Mundo creado: " + response.name(), null, response);
        return response;
    }

    public StrategyDtos.WorldResponse updateWorld(Long id, StrategyDtos.WorldRequest request) {
        World world = findWorld(id);
        StrategyDtos.WorldResponse before = Mapper.toResponse(world);
        var existing = worldRepository.findByNameIgnoreCase(request.name().trim());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe un mundo con ese nombre.");
        }
        world.setName(request.name().trim());
        world.setDescription(request.description());
        StrategyDtos.WorldResponse response = Mapper.toResponse(worldRepository.save(world));
        auditService.record(AuditAction.UPDATE, "WORLD", response.id(), "Mundo actualizado: " + response.name(), before, response);
        return response;
    }

    public void deleteWorld(Long id) {
        World world = findWorld(id);
        if (strategicBetRepository.existsByWorldId(id) || goalRepository.existsByWorldId(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "No se puede eliminar un mundo en uso.");
        }
        worldRepository.delete(world);
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.StrategicBetResponse> listStrategicBets(String period) {
        List<StrategicBet> bets = strategicBetRepository.findAll();
        var summaries = executionSummaryService.forBets(
                bets.stream().map(StrategicBet::getId).toList(),
                period
        );
        return bets
                .stream()
                .map(bet -> Mapper.toResponse(bet, summaries.getOrDefault(bet.getId(), executionSummaryService.empty())))
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
        bet.setStartDate(request.startDate() == null ? java.time.LocalDate.now() : request.startDate());
        bet.setEndDate(request.endDate() == null ? bet.getStartDate().plusYears(1) : request.endDate());
        bet.setWorld(resolveWorld(request.worldId()));
        StrategyDtos.StrategicBetResponse response = Mapper.toResponse(strategicBetRepository.save(bet), executionSummaryService.empty());
        auditService.record(AuditAction.CREATE, "STRATEGIC_BET", response.id(), "Apuesta estrategica creada: " + response.name(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public StrategyDtos.StrategicBetResponse getStrategicBet(Long id, String period) {
        StrategicBet bet = findStrategicBet(id);
        return Mapper.toResponse(bet, executionSummaryService.forBet(id, period));
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.GoalResponse> listGoals(String period) {
        List<InstitutionalGoal> goals = goalRepository.findAll();
        var summaries = executionSummaryService.forGoals(
                goals.stream().map(InstitutionalGoal::getId).toList(),
                period
        );
        return goals.stream()
                .map(goal -> Mapper.toResponse(goal, summaries.getOrDefault(goal.getId(), executionSummaryService.empty())))
                .toList();
    }

    public StrategyDtos.GoalResponse createGoal(StrategyDtos.GoalRequest request) {
        validateDateRange(request.startDate(), request.endDate());
        InstitutionalGoal goal = new InstitutionalGoal();
        goal.setName(request.name().trim());
        goal.setDescription(request.description().trim());
        goal.setReferenceIndicator(request.referenceIndicator());
        goal.setExpectedValue(request.expectedValue());
        goal.setStartDate(request.startDate() == null ? java.time.LocalDate.now() : request.startDate());
        goal.setEndDate(request.endDate() == null ? goal.getStartDate().plusYears(1) : request.endDate());
        goal.setWorld(request.worldId() == null ? null : findWorld(request.worldId()));
        goal.setMeasurementUnit(findUnit(request.measurementUnitId()));
        StrategyDtos.GoalResponse response = Mapper.toResponse(goalRepository.save(goal));
        auditService.record(AuditAction.CREATE, "GOAL", response.id(), "Meta institucional creada: " + response.name(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public StrategyDtos.GoalResponse getGoal(Long id, String period) {
        return Mapper.toResponse(findGoal(id), executionSummaryService.forGoal(id, period));
    }

    public StrategyDtos.GoalResponse attachGoalPeriod(Long goalId, Long periodId) {
        InstitutionalGoal goal = findGoal(goalId);
        StrategyDtos.GoalResponse before = Mapper.toResponse(goal);
        goal.getPeriods().add(findPeriod(periodId));
        StrategyDtos.GoalResponse response = Mapper.toResponse(goalRepository.save(goal));
        auditService.record(AuditAction.UPDATE, "GOAL", response.id(), "Periodo academico asociado a meta.", before, response);
        return response;
    }

    public StrategyDtos.GoalResponse detachGoalPeriod(Long goalId, Long periodId) {
        InstitutionalGoal goal = findGoal(goalId);
        StrategyDtos.GoalResponse before = Mapper.toResponse(goal);
        goal.getPeriods().removeIf(period -> period.getId().equals(periodId));
        StrategyDtos.GoalResponse response = Mapper.toResponse(goalRepository.save(goal));
        auditService.record(AuditAction.UPDATE, "GOAL", response.id(), "Periodo academico desasociado de meta.", before, response);
        return response;
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
        objective.setEstimatedWeight(request.estimatedWeight() == null ? BigDecimal.ZERO : request.estimatedWeight());
        objective.setQuarter(request.quarter() == null ? 1 : request.quarter());
        if (request.createdByProfessorId() != null) {
            objective.setCreatedBy(findProfessor(request.createdByProfessorId()));
        }
        objective.setCreatedByExternalUserId(currentExternalUserId());

        for (StrategyDtos.KeyResultRequest keyResultRequest : request.keyResults()) {
            objective.addKeyResult(buildKeyResult(keyResultRequest));
        }
        StrategyDtos.ObjectiveResponse response = Mapper.toResponse(objectiveRepository.save(objective));
        auditService.record(AuditAction.CREATE, "OBJECTIVE", response.id(), "Objetivo creado: " + response.name(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public StrategyDtos.ObjectiveResponse getObjective(Long id) {
        return Mapper.toResponse(findObjective(id));
    }

    @Transactional(readOnly = true)
    public StrategyDtos.ObjectiveDetailResponse getObjectiveDetail(Long id) {
        return new StrategyDtos.ObjectiveDetailResponse(getObjective(id), coverageTrend(id));
    }

    public StrategyDtos.ObjectiveResponse updateObjective(Long id, StrategyDtos.ObjectiveUpdateRequest request) {
        Objective objective = findObjective(id);
        StrategyDtos.ObjectiveResponse before = Mapper.toResponse(objective);
        objective.setName(request.name().trim());
        objective.setDescription(request.description().trim());
        StrategyDtos.ObjectiveResponse response = Mapper.toResponse(objectiveRepository.save(objective));
        auditService.record(AuditAction.UPDATE, "OBJECTIVE", response.id(), "Objetivo actualizado: " + response.name(), before, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.StrategicHierarchyNodeResponse> getStrategicHierarchyTree(String period) {
        return hierarchyTreeService.tree(period);
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
        StrategyDtos.KeyResultResponse response = Mapper.toResponse(keyResultRepository.save(keyResult));
        auditService.record(AuditAction.CREATE, "KEY_RESULT", response.id(), "Key Result creado: " + response.name(), null, response);
        return response;
    }

    public StrategyDtos.KeyResultResponse updateKeyResult(Long keyResultId, StrategyDtos.KeyResultRequest request) {
        KeyResult keyResult = findKeyResult(keyResultId);
        StrategyDtos.KeyResultResponse before = Mapper.toResponse(keyResult);
        keyResult.setName(request.name().trim());
        keyResult.setDescription(request.description().trim());
        keyResult.setMetric(request.metric().trim());
        keyResult.setBaseValue(request.baseValue());
        keyResult.setTargetValue(request.targetValue());
        keyResult.setMeasurementUnit(findUnit(request.measurementUnitId()));
        keyResult.setAcademicPeriod(request.academicPeriodId() == null ? null : findPeriod(request.academicPeriodId()));
        keyResult.recalculateProgress();
        StrategyDtos.KeyResultResponse response = Mapper.toResponse(keyResultRepository.save(keyResult));
        auditService.record(AuditAction.UPDATE, "KEY_RESULT", response.id(), "Key Result actualizado: " + response.name(), before, response);
        return response;
    }

    public void deleteKeyResult(Long keyResultId) {
        KeyResult keyResult = findKeyResult(keyResultId);
        long activeLinks = linkRepository.countByKeyResultIdAndActiveTrue(keyResultId);
        if (activeLinks > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "No se puede eliminar un KR con proyectos vinculados.");
        }
        keyResultRepository.delete(keyResult);
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.CoverageTrendPointResponse> coverageTrend(Long objectiveId) {
        return coverageTrendService.coverageTrend(objectiveId);
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

    private KeyResult buildKeyResult(StrategyDtos.KeyResultRequest request) {
        KeyResult keyResult = new KeyResult();
        keyResult.setName(request.name().trim());
        keyResult.setDescription(request.description().trim());
        keyResult.setMetric(request.metric().trim());
        keyResult.setBaseValue(request.baseValue());
        keyResult.setTargetValue(request.targetValue());
        keyResult.setMeasurementUnit(findUnit(request.measurementUnitId()));
        keyResult.setAcademicPeriod(request.academicPeriodId() == null ? null : findPeriod(request.academicPeriodId()));
        keyResult.recalculateProgress();
        return keyResult;
    }

    private void validateDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "La fecha de cierre debe ser posterior a la fecha de inicio.");
        }
    }

    private MeasurementUnit findUnit(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Unidad de medida no encontrada."));
    }

    private AcademicPeriod findPeriod(Long id) {
        return periodRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Periodo academico no encontrado."));
    }

    private World findWorld(Long id) {
        return worldRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Mundo no encontrado."));
    }

    private World resolveWorld(Long id) {
        if (id != null) {
            return findWorld(id);
        }
        return worldRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Debe existir un mundo para crear apuestas estrategicas."));
    }

    private Professor findProfessor(Long id) {
        return professorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Profesor no encontrado."));
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

}
