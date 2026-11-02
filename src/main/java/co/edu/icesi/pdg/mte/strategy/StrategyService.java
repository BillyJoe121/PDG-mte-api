package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.*;
import co.edu.icesi.pdg.mte.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class StrategyService {
    private final StrategicBetRepository strategicBetRepository;
    private final InstitutionalGoalRepository goalRepository;
    private final WorldRepository worldRepository;
    private final MeasurementUnitRepository unitRepository;
    private final AcademicPeriodRepository periodRepository;
    private final AuditService auditService;
    private final StrategyExecutionSummaryService executionSummaryService;
    private final StrategicHierarchyTreeService hierarchyTreeService;

    public StrategyService(
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
            WorldRepository worldRepository,
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            AuditService auditService,
            StrategyExecutionSummaryService executionSummaryService,
            StrategicHierarchyTreeService hierarchyTreeService
    ) {
        this.strategicBetRepository = strategicBetRepository;
        this.goalRepository = goalRepository;
        this.worldRepository = worldRepository;
        this.unitRepository = unitRepository;
        this.periodRepository = periodRepository;
        this.auditService = auditService;
        this.executionSummaryService = executionSummaryService;
        this.hierarchyTreeService = hierarchyTreeService;
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

    /** Totales del portafolio: cada KR y cada proyecto se cuentan una sola vez, sin importar a cuantas apuestas u objetivos esten ligados. */
    @Transactional(readOnly = true)
    public StrategyDtos.ExecutionSummaryResponse portfolioExecutionSummary(String period) {
        return executionSummaryService.forPortfolio(period);
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.StrategicBetResponse> listStrategicBets(String period) {
        return listStrategicBets(period, false);
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.StrategicBetResponse> listStrategicBets(String period, boolean includeInactive) {
        AcademicPeriod selectedPeriod = StrategyPeriodScope.resolve(period, periodRepository);
        List<StrategicBet> bets = strategicBetRepository.findAll().stream()
                .filter(bet -> includeInactive || bet.getStatus() == StrategicStatus.ACTIVA)
                .filter(bet -> StrategyPeriodScope.matches(bet, selectedPeriod))
                .toList();
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

    public StrategyDtos.StrategicBetResponse updateStrategicBet(Long id, StrategyDtos.StrategicBetRequest request) {
        StrategicBet bet = findStrategicBet(id);
        StrategyDtos.StrategicBetResponse before = Mapper.toResponse(bet, executionSummaryService.forBet(id, null));
        java.time.LocalDate startDate = request.startDate() == null ? bet.getStartDate() : request.startDate();
        java.time.LocalDate endDate = request.endDate() == null ? bet.getEndDate() : request.endDate();
        validateDateRange(startDate, endDate);
        var existing = strategicBetRepository.findByNameIgnoreCase(request.name().trim());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe una apuesta estrategica con ese nombre.");
        }
        bet.setName(request.name().trim());
        bet.setDescription(request.description().trim());
        bet.setStartDate(startDate);
        bet.setEndDate(endDate);
        bet.setWorld(request.worldId() == null ? bet.getWorld() : findWorld(request.worldId()));
        StrategyDtos.StrategicBetResponse response = Mapper.toResponse(
                strategicBetRepository.save(bet),
                executionSummaryService.forBet(id, null)
        );
        auditService.record(AuditAction.UPDATE, "STRATEGIC_BET", response.id(), "Apuesta estrategica actualizada: " + response.name(), before, response);
        return response;
    }

    public StrategyDtos.StrategicBetResponse setStrategicBetActive(Long id, boolean active) {
        StrategicBet bet = findStrategicBet(id);
        StrategyDtos.StrategicBetResponse before = Mapper.toResponse(bet, executionSummaryService.forBet(id, null));
        bet.setStatus(active ? StrategicStatus.ACTIVA : StrategicStatus.INACTIVA);
        StrategyDtos.StrategicBetResponse response = Mapper.toResponse(
                strategicBetRepository.save(bet),
                executionSummaryService.forBet(id, null)
        );
        auditService.record(AuditAction.STATUS_CHANGE, "STRATEGIC_BET", id,
                active ? "Apuesta estrategica restaurada." : "Apuesta estrategica archivada.", before, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.GoalResponse> listGoals(String period) {
        return listGoals(period, false);
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.GoalResponse> listGoals(String period, boolean includeInactive) {
        AcademicPeriod selectedPeriod = StrategyPeriodScope.resolve(period, periodRepository);
        List<InstitutionalGoal> goals = goalRepository.findAll().stream()
                .filter(goal -> includeInactive || goal.getStatus() == StrategicStatus.ACTIVA)
                .filter(goal -> StrategyPeriodScope.matches(goal, selectedPeriod))
                .toList();
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
        MeasurementUnit measurementUnit = findUnit(request.measurementUnitId());
        MeasurementValuePolicy.requireActive(measurementUnit);
        MeasurementValuePolicy.validateGoalValue(request.expectedValue(), measurementUnit);
        InstitutionalGoal goal = new InstitutionalGoal();
        goal.setName(request.name().trim());
        goal.setDescription(request.description().trim());
        goal.setReferenceIndicator(normalizeOptionalText(request.referenceIndicator()));
        goal.setExpectedValue(request.expectedValue());
        goal.setStartDate(request.startDate() == null ? java.time.LocalDate.now() : request.startDate());
        goal.setEndDate(request.endDate() == null ? goal.getStartDate().plusYears(1) : request.endDate());
        goal.setWorld(request.worldId() == null ? goal.getWorld() : findWorld(request.worldId()));
        goal.setMeasurementUnit(measurementUnit);
        StrategyDtos.GoalResponse response = Mapper.toResponse(goalRepository.save(goal));
        auditService.record(AuditAction.CREATE, "GOAL", response.id(), "Meta institucional creada: " + response.name(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public StrategyDtos.GoalResponse getGoal(Long id, String period) {
        return Mapper.toResponse(findGoal(id), executionSummaryService.forGoal(id, period));
    }

    public StrategyDtos.GoalResponse updateGoal(Long id, StrategyDtos.GoalRequest request) {
        InstitutionalGoal goal = findGoal(id);
        StrategyDtos.GoalResponse before = Mapper.toResponse(goal, executionSummaryService.forGoal(id, null));
        java.time.LocalDate startDate = request.startDate() == null ? goal.getStartDate() : request.startDate();
        java.time.LocalDate endDate = request.endDate() == null ? goal.getEndDate() : request.endDate();
        validateDateRange(startDate, endDate);
        MeasurementUnit measurementUnit = findUnit(request.measurementUnitId());
        MeasurementValuePolicy.requireActive(measurementUnit);
        MeasurementValuePolicy.validateGoalValue(request.expectedValue(), measurementUnit);
        goal.setName(request.name().trim());
        goal.setDescription(request.description().trim());
        goal.setReferenceIndicator(normalizeOptionalText(request.referenceIndicator()));
        goal.setExpectedValue(request.expectedValue());
        goal.setStartDate(startDate);
        goal.setEndDate(endDate);
        goal.setWorld(request.worldId() == null ? goal.getWorld() : findWorld(request.worldId()));
        goal.setMeasurementUnit(measurementUnit);
        StrategyDtos.GoalResponse response = Mapper.toResponse(
                goalRepository.save(goal),
                executionSummaryService.forGoal(id, null)
        );
        auditService.record(AuditAction.UPDATE, "GOAL", response.id(), "Meta institucional actualizada: " + response.name(), before, response);
        return response;
    }

    public StrategyDtos.GoalResponse setGoalActive(Long id, boolean active) {
        InstitutionalGoal goal = findGoal(id);
        StrategyDtos.GoalResponse before = Mapper.toResponse(goal, executionSummaryService.forGoal(id, null));
        goal.setStatus(active ? StrategicStatus.ACTIVA : StrategicStatus.INACTIVA);
        StrategyDtos.GoalResponse response = Mapper.toResponse(
                goalRepository.save(goal),
                executionSummaryService.forGoal(id, null)
        );
        auditService.record(AuditAction.STATUS_CHANGE, "GOAL", id,
                active ? "Meta institucional restaurada." : "Meta institucional archivada.", before, response);
        return response;
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
    public List<StrategyDtos.StrategicHierarchyNodeResponse> getStrategicHierarchyTree(String period) {
        return hierarchyTreeService.tree(period);
    }

    private void validateDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "La fecha de cierre debe ser posterior a la fecha de inicio.");
        }
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private StrategicBet findStrategicBet(Long id) {
        return strategicBetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Apuesta estrategica no encontrada."));
    }

    private InstitutionalGoal findGoal(Long id) {
        return goalRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Meta institucional no encontrada."));
    }
}
