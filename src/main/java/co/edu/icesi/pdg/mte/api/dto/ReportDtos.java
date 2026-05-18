package co.edu.icesi.pdg.mte.api.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record GeneralReportResponse(
            String period,
            Long departmentId,
            Long objectiveId,
            long totalProjects,
            long activeProjects,
            long completedProjects,
            long totalObjectives,
            long totalKeyResults,
            BigDecimal averageObjectiveCoverage,
            BigDecimal averageKeyResultCoverage
    ) {
    }

    public record DepartmentReportResponse(
            Long departmentId,
            String departmentName,
            long projects,
            long objectives,
            long keyResults,
            BigDecimal averageObjectiveCoverage
    ) {
    }

    public record ObjectiveRankingResponse(
            Long objectiveId,
            String objectiveName,
            String departmentName,
            String period,
            BigDecimal coveragePercentage,
            int keyResults
    ) {
    }

    public record PeriodComparisonResponse(
            String basePeriod,
            String comparePeriod,
            GeneralReportResponse base,
            GeneralReportResponse compare,
            BigDecimal objectiveCoverageDelta,
            BigDecimal keyResultCoverageDelta
    ) {
    }

    public record ConsolidatedReportResponse(
            GeneralReportResponse general,
            List<DepartmentReportResponse> departments,
            List<ObjectiveRankingResponse> objectiveRanking
    ) {
    }
}
