package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriodRepository;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnit;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnitRepository;
import co.edu.icesi.pdg.mte.catalog.MeasurementValuePolicy;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.common.PlannerLinks;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class KeyResultService {
    private static final BigDecimal MAX_PROGRESS_PERCENTAGE = BigDecimal.valueOf(100);

    private final KeyResultRepository keyResultRepository;
    private final InstitutionalGoalRepository goalRepository;
    private final StrategicBetRepository strategicBetRepository;
    private final MeasurementUnitRepository unitRepository;
    private final AcademicPeriodRepository periodRepository;
    private final ProjectKeyResultLinkRepository linkRepository;
    private final AuditService auditService;
    private final KeyResultProgressRecordRepository progressRecordRepository;
    private final ProjectRepository projectRepository;

    public KeyResultService(
            KeyResultRepository keyResultRepository,
            InstitutionalGoalRepository goalRepository,
            StrategicBetRepository strategicBetRepository,
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            ProjectKeyResultLinkRepository linkRepository,
            AuditService auditService,
            KeyResultProgressRecordRepository progressRecordRepository,
            ProjectRepository projectRepository
    ) {
        this.projectRepository = projectRepository;
        this.progressRecordRepository = progressRecordRepository;
        this.keyResultRepository = keyResultRepository;
        this.goalRepository = goalRepository;
        this.strategicBetRepository = strategicBetRepository;
        this.unitRepository = unitRepository;
        this.periodRepository = periodRepository;
        this.linkRepository = linkRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.KeyResultResponse> list(
            Long goalId,
            Long strategicBetId,
            Long periodId,
            boolean includeArchived
    ) {
        return keyResultRepository.findAll()
                .stream()
                .filter(keyResult -> includeArchived || !keyResult.isArchived())
                .filter(keyResult -> goalId == null
                        || keyResult.getGoals().stream().anyMatch(goal -> goal.getId().equals(goalId)))
                .filter(keyResult -> strategicBetId == null
                        || keyResult.getStrategicBets().stream().anyMatch(bet -> bet.getId().equals(strategicBetId)))
                .filter(keyResult -> periodId == null
                        || keyResult.getAcademicPeriod() != null && keyResult.getAcademicPeriod().getId().equals(periodId))
                .sorted(Comparator.comparing(KeyResult::getId))
                .map(Mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StrategyDtos.KeyResultResponse get(Long keyResultId) {
        return Mapper.toResponse(findKeyResult(keyResultId));
    }

    public StrategyDtos.KeyResultResponse create(StrategyDtos.KeyResultRequest request) {
        KeyResult keyResult = new KeyResult();
        apply(keyResult, request);
        // A new KR starts at its base value with no recorded progress; later edits keep the recorded values.
        keyResult.setCurrentValue(request.baseValue());
        keyResult.setProgressPercentage(BigDecimal.ZERO);
        StrategyDtos.KeyResultResponse response = Mapper.toResponse(keyResultRepository.save(keyResult));
        auditService.record(AuditAction.CREATE, "KEY_RESULT", response.id(), "Key Result creado: " + response.name(), null, response);
        return response;
    }

    public StrategyDtos.KeyResultResponse update(Long keyResultId, StrategyDtos.KeyResultRequest request) {
        KeyResult keyResult = findKeyResult(keyResultId);
        requireMutable(keyResult);
        StrategyDtos.KeyResultResponse before = Mapper.toResponse(keyResult);
        apply(keyResult, request);
        StrategyDtos.KeyResultResponse response = Mapper.toResponse(keyResultRepository.save(keyResult));
        auditService.record(AuditAction.UPDATE, "KEY_RESULT", response.id(), "Key Result actualizado: " + response.name(), before, response);
        return response;
    }

    public StrategyDtos.KeyResultResponse updateProgress(Long keyResultId, StrategyDtos.KeyResultProgressRequest request) {
        KeyResult keyResult = findKeyResult(keyResultId);
        requireMutable(keyResult);
        BigDecimal currentValue = request.currentValue();
        BigDecimal progressPercentage = request.progressPercentage();
        MeasurementValuePolicy.validateKeyResultCurrentValue(currentValue, keyResult.getMeasurementUnit());
        validateProgressPercentage(progressPercentage);
        StrategyDtos.KeyResultResponse before = Mapper.toResponse(keyResult);
        keyResult.setCurrentValue(currentValue);
        keyResult.setProgressPercentage(progressPercentage);
        StrategyDtos.KeyResultResponse response = Mapper.toResponse(keyResultRepository.save(keyResult));
        progressRecordRepository.save(progressRecord(keyResultId, before, currentValue, progressPercentage));
        auditService.record(AuditAction.PROGRESS_REGISTERED, "KEY_RESULT", keyResultId,
                "Avance de Key Result registrado: valor actual " + currentValue.toPlainString()
                        + ", avance " + progressPercentage.toPlainString() + "%.", before, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<StrategyDtos.KeyResultProgressRecordResponse> progressRecords(Long keyResultId) {
        findKeyResult(keyResultId);
        return progressRecordRepository.findByKeyResultIdOrderByRecordedAtDescIdDesc(keyResultId).stream()
                .map(record -> new StrategyDtos.KeyResultProgressRecordResponse(
                        record.getId(), record.getKeyResultId(), record.getPreviousValue(), record.getCurrentValue(),
                        record.getPreviousPercentage(), record.getProgressPercentage(),
                        record.getAuthorName(), record.getAuthorEmail(), record.getRecordedAt()))
                .toList();
    }

    private KeyResultProgressRecord progressRecord(
            Long keyResultId,
            StrategyDtos.KeyResultResponse before,
            BigDecimal currentValue,
            BigDecimal progressPercentage
    ) {
        KeyResultProgressRecord record = new KeyResultProgressRecord();
        record.setKeyResultId(keyResultId);
        record.setPreviousValue(before.currentValue());
        record.setCurrentValue(currentValue);
        record.setPreviousPercentage(before.progressPercentage());
        record.setProgressPercentage(progressPercentage);
        record.setRecordedAt(Instant.now());
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof ExternalUserContext author) {
            record.setAuthorName(author.professorName() != null && !author.professorName().isBlank()
                    ? author.professorName() : author.username());
            record.setAuthorEmail(author.email());
        }
        return record;
    }

    public StrategyDtos.KeyResultResponse setStatus(Long keyResultId, KeyResultStatus targetStatus) {
        KeyResult keyResult = findKeyResult(keyResultId);
        if (keyResult.getStatus() == targetStatus) {
            return Mapper.toResponse(keyResult);
        }
        boolean allowed = keyResult.getStatus() == KeyResultStatus.BORRADOR && targetStatus == KeyResultStatus.ACTIVO
                || keyResult.getStatus() == KeyResultStatus.ACTIVO && targetStatus == KeyResultStatus.CERRADO;
        if (!allowed) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Transición de KR no permitida: " + keyResult.getStatus() + " -> " + targetStatus + ".");
        }

        StrategyDtos.KeyResultResponse before = Mapper.toResponse(keyResult);
        keyResult.setStatus(targetStatus);
        StrategyDtos.KeyResultResponse response = Mapper.toResponse(keyResultRepository.save(keyResult));
        auditService.record(AuditAction.STATUS_CHANGE, "KEY_RESULT", keyResultId,
                "Estado de Key Result actualizado: " + targetStatus + ".", before, response);
        return response;
    }

    public StrategyDtos.KeyResultResponse setActive(Long keyResultId, boolean active) {
        KeyResult keyResult = findKeyResult(keyResultId);
        if (keyResult.isArchived() == !active) {
            return Mapper.toResponse(keyResult);
        }
        if (!active) {
            requireNotClosed(keyResult);
            // A project's main KR is a direct reference, not a link row, so it is checked separately.
            if (linkRepository.countByKeyResultIdAndActiveTrue(keyResultId) > 0 || projectRepository.existsByKeyResultId(keyResultId)) {
                throw new BusinessException(HttpStatus.CONFLICT, "No se puede archivar un KR con proyectos vinculados.");
            }
        }
        StrategyDtos.KeyResultResponse before = Mapper.toResponse(keyResult);
        keyResult.setArchived(!active);
        keyResult.setArchivedAt(active ? null : Instant.now());
        StrategyDtos.KeyResultResponse response = Mapper.toResponse(keyResultRepository.save(keyResult));
        auditService.record(AuditAction.STATUS_CHANGE, "KEY_RESULT", keyResultId,
                active ? "Key Result restaurado." : "Key Result archivado.", before, response);
        return response;
    }

    private void apply(KeyResult keyResult, StrategyDtos.KeyResultRequest request) {
        String plannerUrl = PlannerLinks.normalize(request.plannerUrl());
        Set<InstitutionalGoal> goals = resolveGoals(request.goalIds());
        Set<StrategicBet> strategicBets = resolveStrategicBets(request.strategicBetIds());
        if (goals.isEmpty() && strategicBets.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El KR debe vincularse al menos a una Meta o a una Apuesta.");
        }
        MeasurementUnit measurementUnit = findUnit(request.measurementUnitId());
        MeasurementValuePolicy.requireActive(measurementUnit);
        MeasurementValuePolicy.validateKeyResultValues(request.baseValue(), request.targetValue(), measurementUnit);
        keyResult.setName(request.name().trim());
        keyResult.setDescription(request.description().trim());
        keyResult.setMetric(request.metric().trim());
        keyResult.setPlannerUrl(plannerUrl);
        keyResult.setBaseValue(request.baseValue());
        keyResult.setTargetValue(request.targetValue());
        keyResult.setMeasurementUnit(measurementUnit);
        keyResult.setAcademicPeriod(findPeriod(request.academicPeriodId()));
        keyResult.getGoals().clear();
        keyResult.getGoals().addAll(goals);
        keyResult.getStrategicBets().clear();
        keyResult.getStrategicBets().addAll(strategicBets);
    }

    private Set<InstitutionalGoal> resolveGoals(List<Long> ids) {
        Set<InstitutionalGoal> goals = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().distinct().forEach(id -> goals.add(goalRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Meta institucional no encontrada."))));
        }
        return goals;
    }

    private Set<StrategicBet> resolveStrategicBets(List<Long> ids) {
        Set<StrategicBet> bets = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().distinct().forEach(id -> bets.add(strategicBetRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Apuesta estrategica no encontrada."))));
        }
        return bets;
    }

    private void requireMutable(KeyResult keyResult) {
        KeyResultArchivePolicy.requireNotArchived(keyResult);
        requireNotClosed(keyResult);
    }

    private void requireNotClosed(KeyResult keyResult) {
        if (keyResult.getStatus() == KeyResultStatus.CERRADO) {
            throw new BusinessException(HttpStatus.CONFLICT, "El KR está cerrado y no admite cambios ordinarios.");
        }
    }

    private static void validateProgressPercentage(BigDecimal progressPercentage) {
        if (progressPercentage == null
                || progressPercentage.signum() < 0
                || progressPercentage.compareTo(MAX_PROGRESS_PERCENTAGE) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El porcentaje de avance debe estar entre 0 y 100.");
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

    private KeyResult findKeyResult(Long id) {
        return keyResultRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Key Result no encontrado."));
    }
}
