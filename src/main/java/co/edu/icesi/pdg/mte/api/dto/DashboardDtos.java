package co.edu.icesi.pdg.mte.api.dto;

import java.math.BigDecimal;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record DashboardSummaryResponse(
            String period,
            long activeProjects,
            long completedProjects,
            long draftProjects,
            long suspendedProjects,
            long archivedProjects,
            long objectivesInFollowUp,
            long lowCompletionObjectives,
            long completedKeyResults,
            long inProgressKeyResults,
            BigDecimal averageObjectiveCoverage,
            BigDecimal averageKeyResultCoverage
    ) {
    }

    public record CountByStatusResponse(
            String status,
            long count
    ) {
    }

    public record ProgressBucketResponse(
            String bucket,
            long count
    ) {
    }

    public record DepartmentExecutionResponse(
            Long departmentId,
            String departmentName,
            long activeProjects,
            long completedProjects,
            long objectives,
            long completedKeyResults,
            long inProgressKeyResults,
            BigDecimal averageObjectiveCoverage
    ) {
    }

    public record StrategicBetExecutionResponse(
            Long strategicBetId,
            String strategicBetName,
            long objectives,
            long keyResults,
            long completedProjects,
            long inProgressProjects,
            BigDecimal averageObjectiveCoverage
    ) {
    }
}
