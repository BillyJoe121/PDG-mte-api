package co.edu.icesi.pdg.mte.report;

import co.edu.icesi.pdg.mte.api.dto.ReportDtos;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA','JEFE_DPTO')")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/general")
    public ReportDtos.GeneralReportResponse general(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long objectiveId
    ) {
        return reportService.general(period, departmentId, objectiveId);
    }

    @GetMapping("/departments")
    public List<ReportDtos.DepartmentReportResponse> departments(@RequestParam(required = false) String period) {
        return reportService.departments(period);
    }

    @GetMapping("/objectives/ranking")
    public List<ReportDtos.ObjectiveRankingResponse> objectiveRanking(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long departmentId
    ) {
        return reportService.objectiveRanking(period, departmentId);
    }

    @GetMapping("/period-comparison")
    public ReportDtos.PeriodComparisonResponse periodComparison(
            @RequestParam String basePeriod,
            @RequestParam String comparePeriod,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long objectiveId
    ) {
        return reportService.periodComparison(basePeriod, comparePeriod, departmentId, objectiveId);
    }

    @GetMapping
    public ReportDtos.ConsolidatedReportResponse consolidated(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long objectiveId
    ) {
        return reportService.consolidated(period, departmentId, objectiveId);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<String> csv(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long objectiveId
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("msp-report.csv")
                        .build()
                        .toString())
                .body(reportService.csv(period, departmentId, objectiveId));
    }

    @GetMapping(value = "/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long objectiveId
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("msp-report.pdf", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(reportService.pdf(period, departmentId, objectiveId));
    }
}
