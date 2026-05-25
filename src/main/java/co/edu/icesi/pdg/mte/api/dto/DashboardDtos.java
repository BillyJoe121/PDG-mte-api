package co.edu.icesi.pdg.mte.api.dto;

import java.math.BigDecimal;
import java.util.List;

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
            long completedObjectives,
            long objectivesAbove50,
            long objectivesBetween0And50,
            long objectivesAtZero,
            long completedKeyResults,
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
            long objectives,
            long completedObjectives,
            long objectivesAbove50,
            long objectivesBetween0And50,
            long objectivesAtZero,
            long completedKeyResults,
            long inProgressKeyResults
    ) {
    }

    public record StrategicBetExecutionResponse(
            Long strategicBetId,
            String strategicBetName,
            long objectives,
            long completedObjectives,
            long objectivesAbove50,
            long objectivesBetween0And50,
            long objectivesAtZero,
            long keyResults,
            long completedProjects,
            long inProgressProjects
    ) {
    }

    public record GoalExecutionResponse(
            Long goalId,
            String goalName,
            long objectives,
            long completedObjectives,
            long objectivesAbove50,
            long objectivesBetween0And50,
            long objectivesAtZero,
            long keyResults,
            long completedProjects,
            long inProgressProjects
    ) {
    }

    public record DashboardScreenResponse(
            List<CatalogDtos.AcademicPeriodResponse> periods,
            DashboardSummaryResponse summary,
            List<CountByStatusResponse> projectsByStatus,
            List<ProgressBucketResponse> keyResultsByProgress,
            List<DepartmentExecutionResponse> departments,
            List<StrategicBetExecutionResponse> strategicBets,
            List<GoalExecutionResponse> goals
    ) {
    }
}
