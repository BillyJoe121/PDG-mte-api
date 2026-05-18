package co.edu.icesi.pdg.mte.api;

import co.edu.icesi.pdg.mte.api.dto.CatalogDtos;
import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnit;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectProgressEntry;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoal;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.Objective;
import co.edu.icesi.pdg.mte.strategy.StrategicBet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

public final class Mapper {
    private Mapper() {
    }

    public static CatalogDtos.MeasurementUnitResponse toResponse(MeasurementUnit unit) {
        return new CatalogDtos.MeasurementUnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getType(),
                unit.getDescription(),
                unit.isActive()
        );
    }

    public static CatalogDtos.AcademicPeriodResponse toResponse(AcademicPeriod period) {
        return new CatalogDtos.AcademicPeriodResponse(
                period.getId(),
                period.getName(),
                period.getStartDate(),
                period.getEndDate(),
                period.getStatus()
        );
    }

    public static CatalogDtos.DepartmentResponse toResponse(Department department) {
        return new CatalogDtos.DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getDescription(),
                department.getExternalDepartmentId()
        );
    }

    public static StrategyDtos.StrategicBetResponse toResponse(
            StrategicBet bet,
            StrategyDtos.ExecutionSummaryResponse executionSummary
    ) {
        return new StrategyDtos.StrategicBetResponse(
                bet.getId(),
                bet.getName(),
                bet.getDescription(),
                bet.getStartDate(),
                bet.getEndDate(),
                bet.getStatus(),
                bet.getCreatedAt(),
                executionSummary
        );
    }

    public static StrategyDtos.GoalResponse toResponse(InstitutionalGoal goal) {
        return toResponse(goal, null);
    }

    public static StrategyDtos.GoalResponse toResponse(
            InstitutionalGoal goal,
            StrategyDtos.ExecutionSummaryResponse executionSummary
    ) {
        List<CatalogDtos.AcademicPeriodResponse> periods = goal.getPeriods()
                .stream()
                .sorted(Comparator.comparing(AcademicPeriod::getStartDate))
                .map(Mapper::toResponse)
                .toList();
        return new StrategyDtos.GoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getDescription(),
                goal.getReferenceIndicator(),
                goal.getExpectedValue(),
                goal.getMeasurementUnit().getId(),
                goal.getMeasurementUnit().getName(),
                goal.getStartDate(),
                goal.getEndDate(),
                goal.getStatus(),
                goal.getCreatedAt(),
                periods,
                executionSummary
        );
    }

    public static StrategyDtos.KeyResultResponse toResponse(KeyResult keyResult) {
        return new StrategyDtos.KeyResultResponse(
                keyResult.getId(),
                keyResult.getName(),
                keyResult.getDescription(),
                keyResult.getMetric(),
                keyResult.getBaseValue(),
                keyResult.getTargetValue(),
                calculatedCurrentValue(keyResult),
                keyResult.getProgressPercentage(),
                keyResult.getMeasurementUnit().getId(),
                keyResult.getMeasurementUnit().getName(),
                keyResult.getCreatedAt()
        );
    }

    public static StrategyDtos.ObjectiveResponse toResponse(Objective objective) {
        return new StrategyDtos.ObjectiveResponse(
                objective.getId(),
                objective.getName(),
                objective.getDescription(),
                objective.getStatus(),
                objective.getCreatedAt(),
                objective.getDepartment().getId(),
                objective.getDepartment().getName(),
                objective.getAcademicPeriod().getId(),
                objective.getAcademicPeriod().getName(),
                objective.getGoal().getId(),
                objective.getGoal().getName(),
                objective.getStrategicBet().getId(),
                objective.getStrategicBet().getName(),
                objective.completionPercentage(),
                objective.getKeyResults().stream().map(Mapper::toResponse).toList()
        );
    }

    public static ProjectDtos.ProjectResponse toResponse(Project project) {
        Long departmentId = project.getDepartment() == null ? null : project.getDepartment().getId();
        String departmentName = project.getDepartment() == null ? project.getDepartmentName() : project.getDepartment().getName();
        return new ProjectDtos.ProjectResponse(
                project.getId(),
                project.getExternalProjectId(),
                project.getExternalSource(),
                project.getName(),
                project.getDescription(),
                project.getType(),
                departmentId,
                departmentName,
                project.getStatus(),
                project.getStartPeriod(),
                project.getEndPeriod(),
                project.getStartDate(),
                project.getEndDate(),
                project.getActualEndDate(),
                project.getGlobalProgress(),
                project.getTutors().stream().toList(),
                List.of(),
                project.getOrigin(),
                project.getSyncStatus(),
                project.getLastSyncedAt(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private static BigDecimal calculatedCurrentValue(KeyResult keyResult) {
        BigDecimal baseValue = keyResult.getBaseValue() == null ? BigDecimal.ZERO : keyResult.getBaseValue();
        BigDecimal targetValue = keyResult.getTargetValue() == null ? baseValue : keyResult.getTargetValue();
        BigDecimal progress = keyResult.getProgressPercentage() == null ? BigDecimal.ZERO : keyResult.getProgressPercentage();
        return targetValue.subtract(baseValue)
                .multiply(progress)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .add(baseValue)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static ProjectDtos.ProjectProgressResponse toResponse(ProjectProgressEntry entry) {
        return new ProjectDtos.ProjectProgressResponse(
                entry.getId(),
                entry.getProject().getId(),
                entry.getProgressPercent(),
                entry.getComment(),
                entry.getMilestones(),
                entry.getCreatedByExternalUserId(),
                entry.getCreatedAt()
        );
    }
}
