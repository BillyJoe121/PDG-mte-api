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
            Long worldId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        public StrategicBetRequest(String name, String description, LocalDate startDate, LocalDate endDate) {
            this(name, description, null, startDate, endDate);
        }
    }

    public record StrategicBetResponse(
            Long id,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            StrategicStatus status,
            Instant createdAt,
            Long worldId,
            String worldName,
            ExecutionSummaryResponse executionSummary
    ) {
        public StrategicBetResponse(
                Long id,
                String name,
                String description,
                LocalDate startDate,
                LocalDate endDate,
                StrategicStatus status,
                Instant createdAt,
                ExecutionSummaryResponse executionSummary
        ) {
            this(id, name, description, startDate, endDate, status, createdAt, null, null, executionSummary);
        }
    }

    public record GoalRequest(
            @NotBlank String name,
            @NotBlank String description,
            String referenceIndicator,
            @NotNull @DecimalMin("0.00") BigDecimal expectedValue,
            @NotNull Long measurementUnitId,
            Long worldId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        public GoalRequest(
                String name,
                String description,
                String referenceIndicator,
                BigDecimal expectedValue,
                Long measurementUnitId,
                LocalDate startDate,
                LocalDate endDate
        ) {
            this(name, description, referenceIndicator, expectedValue, measurementUnitId, null, startDate, endDate);
        }
    }

    public record GoalResponse(
            Long id,
            String name,
            String description,
            String referenceIndicator,
            BigDecimal expectedValue,
            Long measurementUnitId,
            String measurementUnitName,
            Long worldId,
            String worldName,
            LocalDate startDate,
            LocalDate endDate,
            StrategicStatus status,
            Instant createdAt,
            List<CatalogDtos.AcademicPeriodResponse> periods,
            ExecutionSummaryResponse executionSummary
    ) {
    }

    public record KeyResultRequest(
            @NotBlank String name,
            @NotBlank String description,
            @NotBlank String metric,
            @NotNull BigDecimal baseValue,
            @NotNull BigDecimal targetValue,
            @NotNull Long measurementUnitId,
            Long academicPeriodId
    ) {
        public KeyResultRequest(
                String name,
                String description,
                String metric,
                BigDecimal baseValue,
                BigDecimal targetValue,
                Long measurementUnitId
        ) {
            this(name, description, metric, baseValue, targetValue, measurementUnitId, null);
        }
    }

    public record KeyResultResponse(
            Long id,
            String name,
            String description,
            String metric,
            BigDecimal baseValue,
            BigDecimal targetValue,
            BigDecimal currentValue,
            BigDecimal progressPercentage,
            Long measurementUnitId,
            String measurementUnitName,
            Long academicPeriodId,
            String academicPeriodName,
            Instant createdAt
    ) {
    }

    public record ObjectiveRequest(
            @NotBlank String name,
            @NotBlank String description,
            @NotNull Long departmentId,
            @NotNull Long academicPeriodId,
            @NotNull Long goalId,
            @NotNull Long strategicBetId,
            @DecimalMin("0.00") BigDecimal estimatedWeight,
            @Min(1) @Max(4) Integer quarter,
            Long createdByProfessorId,
            @NotEmpty List<@Valid KeyResultRequest> keyResults
    ) {
        public ObjectiveRequest(
                String name,
                String description,
                Long departmentId,
                Long academicPeriodId,
                Long goalId,
                Long strategicBetId,
                List<KeyResultRequest> keyResults
        ) {
            this(name, description, departmentId, academicPeriodId, goalId, strategicBetId, null, null, null, keyResults);
        }
    }

    public record ObjectiveResponse(
            Long id,
            String name,
            String description,
            ObjectiveStatus status,
            Instant createdAt,
            BigDecimal estimatedWeight,
            Long createdByProfessorId,
            String createdByProfessorName,
            Integer quarter,
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

    public record ObjectiveDetailResponse(
            ObjectiveResponse objective,
            List<CoverageTrendPointResponse> coverageTrend
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

    public record ObjectiveScreenDataResponse(
            List<ObjectiveCardResponse> objectiveCards,
            List<StrategicBetResponse> strategicBets,
            List<GoalResponse> goals,
            List<CatalogDtos.AcademicPeriodResponse> academicPeriods,
            List<CatalogDtos.MeasurementUnitResponse> measurementUnits,
            List<CatalogDtos.DepartmentResponse> departments
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

    public record CoverageTrendPointResponse(
            java.time.Instant timestamp,
            BigDecimal coveragePercentage,
            String source
    ) {
    }

    public record WorldRequest(
            @NotBlank String name,
            String description
    ) {
    }

    public record WorldResponse(
            Long id,
            String name,
            String description
    ) {
    }
}
