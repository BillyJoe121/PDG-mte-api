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
            long keyResultsInFollowUp,
            long lowCompletionKeyResults,
            long completedKeyResults,
            long keyResultsAbove50,
            long keyResultsBetween0And50,
            long keyResultsAtZero,
            long inProgressKeyResults,
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
            long keyResults,
            long completedKeyResults,
            long keyResultsAbove50,
            long keyResultsBetween0And50,
            long keyResultsAtZero,
            long inProgressKeyResults
    ) {
    }

    public record StrategicBetExecutionResponse(
            Long strategicBetId,
            String strategicBetName,
            long keyResults,
            long completedKeyResults,
            long keyResultsAbove50,
            long keyResultsBetween0And50,
            long keyResultsAtZero,
            long completedProjects,
            long inProgressProjects
    ) {
    }

    public record GoalExecutionResponse(
            Long goalId,
            String goalName,
            long keyResults,
            long completedKeyResults,
            long keyResultsAbove50,
            long keyResultsBetween0And50,
            long keyResultsAtZero,
            long completedProjects,
            long inProgressProjects
    ) {
    }
}
