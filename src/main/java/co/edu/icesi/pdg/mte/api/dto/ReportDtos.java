package co.edu.icesi.pdg.mte.api.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record GeneralReportResponse(
            String period,
            Long departmentId,
            long totalProjects,
            long activeProjects,
            long completedProjects,
            long totalKeyResults,
            long activeKeyResults,
            long closedKeyResults
    ) {
    }

    public record DepartmentReportResponse(
            Long departmentId,
            String departmentName,
            long projects,
            long keyResults,
            long closedKeyResults
    ) {
    }

    public record KeyResultRankingResponse(
            Long keyResultId,
            String keyResultName,
            List<String> goalNames,
            List<String> strategicBetNames,
            String period,
            BigDecimal progressPercentage,
            int linkedProjects
    ) {
    }

    public record PeriodComparisonResponse(
            String basePeriod,
            String comparePeriod,
            GeneralReportResponse base,
            GeneralReportResponse compare
    ) {
    }

    public record ConsolidatedReportResponse(
            GeneralReportResponse general,
            List<DepartmentReportResponse> departments,
            List<KeyResultRankingResponse> keyResultRanking
    ) {
    }
}
