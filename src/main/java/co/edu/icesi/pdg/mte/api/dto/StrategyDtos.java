package co.edu.icesi.pdg.mte.api.dto;

import co.edu.icesi.pdg.mte.strategy.KeyResultStatus;
import co.edu.icesi.pdg.mte.strategy.StrategicStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
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

    public record StrategyLinkResponse(Long id, String name) {
    }

    public record KeyResultRequest(
            @NotBlank String name,
            @NotBlank String description,
            @NotBlank String metric,
            @NotNull BigDecimal baseValue,
            @NotNull BigDecimal targetValue,
            @NotNull Long measurementUnitId,
            @NotNull Long academicPeriodId,
            List<@NotNull Long> goalIds,
            List<@NotNull Long> strategicBetIds,
            String plannerUrl
    ) {
    }

    public record KeyResultProgressRequest(
            @NotNull BigDecimal currentValue,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercentage
    ) {
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
            List<StrategyLinkResponse> goals,
            List<StrategyLinkResponse> strategicBets,
            KeyResultStatus status,
            Instant createdAt,
            @JsonInclude(JsonInclude.Include.ALWAYS) String plannerUrl,
            boolean archived,
            @JsonInclude(JsonInclude.Include.ALWAYS) Instant archivedAt
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record KeyResultProgressRecordResponse(
            Long id,
            Long keyResultId,
            BigDecimal previousValue,
            BigDecimal currentValue,
            BigDecimal previousPercentage,
            BigDecimal progressPercentage,
            String authorName,
            String authorEmail,
            Instant recordedAt
    ) {
    }

    public record KeyResultStatusRequest(@NotNull KeyResultStatus status) {
    }

    public record ActiveStateRequest(
            boolean active
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
            int completedKeyResults,
            int inProgressKeyResults,
            int completedProjects,
            int inProgressProjects,
            List<PeriodExecutionSummaryResponse> periods
    ) {
    }

    public record PeriodExecutionSummaryResponse(
            String period,
            int completedKeyResults,
            int inProgressKeyResults,
            int completedProjects,
            int inProgressProjects
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
