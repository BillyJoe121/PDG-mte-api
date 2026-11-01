package co.edu.icesi.pdg.mte.api.dto;

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
            long draftKeyResults,
            long activeKeyResults,
            long closedKeyResults
    ) {
    }

    public record CountByStatusResponse(
            String status,
            long count
    ) {
    }

    public record DepartmentExecutionResponse(
            Long departmentId,
            String departmentName,
            long activeProjects,
            long completedProjects,
            long keyResults,
            long draftKeyResults,
            long activeKeyResults,
            long closedKeyResults
    ) {
    }

    public record StrategicBetExecutionResponse(
            Long strategicBetId,
            String strategicBetName,
            long keyResults,
            long draftKeyResults,
            long activeKeyResults,
            long closedKeyResults,
            long completedProjects,
            long inProgressProjects
    ) {
    }

    public record GoalExecutionResponse(
            Long goalId,
            String goalName,
            long keyResults,
            long draftKeyResults,
            long activeKeyResults,
            long closedKeyResults,
            long completedProjects,
            long inProgressProjects
    ) {
    }
}
