package co.edu.icesi.pdg.mte.api.dto;

import co.edu.icesi.pdg.mte.strategy.ObjectiveStatus;
import co.edu.icesi.pdg.mte.strategy.StrategicStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class StrategyDtos {
    private StrategyDtos() {
    }

    public record StrategicBetRequest(
            @NotBlank String name,
            @NotBlank String description,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    public record StrategicBetResponse(
            Long id,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            StrategicStatus status,
            Instant createdAt,
            ExecutionSummaryResponse executionSummary
    ) {
    }

    public record GoalRequest(
            @NotBlank String name,
            @NotBlank String description,
            String referenceIndicator,
            @NotNull @DecimalMin("0.00") BigDecimal expectedValue,
            @NotNull Long measurementUnitId,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    public record GoalResponse(
            Long id,
            String name,
            String description,
            String referenceIndicator,
            BigDecimal expectedValue,
            Long measurementUnitId,
            String measurementUnitName,
            LocalDate startDate,
            LocalDate endDate,
            StrategicStatus status,
            Instant createdAt,
            List<CatalogDtos.AcademicPeriodResponse> periods,
            ExecutionSummaryResponse executionSummary
    ) {
    }

    public record KeyResultRequest(
            @NotBlank String description,
            @NotBlank String metric,
            @NotNull BigDecimal baseValue,
            @NotNull BigDecimal targetValue,
            BigDecimal currentValue,
            @NotNull Long measurementUnitId
    ) {
    }

    public record KeyResultResponse(
            Long id,
            String description,
            String metric,
            BigDecimal baseValue,
            BigDecimal targetValue,
            BigDecimal currentValue,
            BigDecimal progressPercentage,
            Long measurementUnitId,
            String measurementUnitName,
            Instant createdAt
    ) {
    }

    public record KeyResultCurrentValueRequest(
            @NotNull BigDecimal currentValue
    ) {
    }

    public record ObjectiveRequest(
            @NotBlank String name,
            @NotBlank String description,
            @NotNull Long departmentId,
            @NotNull Long academicPeriodId,
            @NotNull Long goalId,
            @NotNull Long strategicBetId,
            @NotEmpty List<@Valid KeyResultRequest> keyResults
    ) {
    }

    public record ObjectiveResponse(
            Long id,
            String name,
            String description,
            ObjectiveStatus status,
            Instant createdAt,
            Long departmentId,
            String departmentName,
            Long academicPeriodId,
            String academicPeriodName,
            Long goalId,
            String goalName,
            Long strategicBetId,
            String strategicBetName,
            BigDecimal completionPercentage,
            List<KeyResultResponse> keyResults
    ) {
    }

    public record ObjectiveUpdateRequest(
            @NotBlank String name,
            @NotBlank String description
    ) {
    }

    public record ObjectiveCardResponse(
            Long id,
            String name,
            String description,
            Long strategicBetId,
            String strategicBetName,
            Long goalId,
            String goalName,
            Long departmentId,
            String departmentName,
            Long academicPeriodId,
            String academicPeriodName,
            BigDecimal completionPercentage,
            boolean lowCompletionAlert,
            List<KeyResultResponse> keyResults
    ) {
    }

    public record StrategicHierarchyNodeResponse(
            String nodeType,
            String id,
            String label,
            String description,
            BigDecimal progressPercentage,
            ExecutionSummaryResponse executionSummary,
            String badge,
            List<StrategicHierarchyNodeResponse> children
    ) {
    }

    public record ExecutionSummaryResponse(
            String summaryText,
            int completedObjectives,
            int inProgressObjectives,
            int completedKeyResults,
            int inProgressKeyResults,
            int completedProjects,
            int inProgressProjects,
            List<PeriodExecutionSummaryResponse> periods
    ) {
    }

    public record PeriodExecutionSummaryResponse(
            String period,
            int completedObjectives,
            int inProgressObjectives,
            int completedKeyResults,
            int inProgressKeyResults,
            int completedProjects,
            int inProgressProjects
    ) {
    }
}
