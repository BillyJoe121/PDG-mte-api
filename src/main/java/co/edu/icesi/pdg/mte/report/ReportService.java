package co.edu.icesi.pdg.mte.report;

import co.edu.icesi.pdg.mte.api.dto.ReportDtos;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.Objective;
import co.edu.icesi.pdg.mte.strategy.ObjectiveRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class ReportService {
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(Q[1-4]|[1-2])$");

    private final ProjectRepository projectRepository;
    private final ObjectiveRepository objectiveRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectKeyResultLinkRepository linkRepository;

    public ReportService(
            ProjectRepository projectRepository,
            ObjectiveRepository objectiveRepository,
            DepartmentRepository departmentRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        this.projectRepository = projectRepository;
        this.objectiveRepository = objectiveRepository;
        this.departmentRepository = departmentRepository;
        this.linkRepository = linkRepository;
    }

    public ReportDtos.GeneralReportResponse general(String period, Long departmentId, Long objectiveId) {
        String normalizedPeriod = normalizePeriod(period);
        List<Project> projects = filteredProjects(normalizedPeriod, departmentId, objectiveId);
        List<Objective> objectives = filteredObjectives(normalizedPeriod, departmentId, objectiveId);
        List<KeyResult> keyResults = keyResultsFrom(objectives);
        return new ReportDtos.GeneralReportResponse(
                normalizedPeriod,
                departmentId,
                objectiveId,
                projects.size(),
                projects.stream().filter(project -> project.getStatus() == ProjectStatus.ACTIVO).count(),
                projects.stream().filter(project -> project.getStatus() == ProjectStatus.FINALIZADO).count(),
                objectives.size(),
                keyResults.size(),
                averageObjectiveCoverage(objectives),
                averageKeyResultCoverage(keyResults)
        );
    }

    public List<ReportDtos.DepartmentReportResponse> departments(String period) {
        return departments(period, null, null);
    }

    private List<ReportDtos.DepartmentReportResponse> departments(String period, Long departmentId, Long objectiveId) {
        String normalizedPeriod = normalizePeriod(period);
        List<Project> projects = filteredProjects(normalizedPeriod, departmentId, objectiveId);
        List<Objective> objectives = filteredObjectives(normalizedPeriod, departmentId, objectiveId);
        return departmentRepository.findAll()
                .stream()
                .filter(department -> departmentId == null || departmentId.equals(department.getId()))
                .sorted(Comparator.comparing(Department::getName))
                .map(department -> departmentReport(department, projects, objectives))
                .toList();
    }

    public List<ReportDtos.ObjectiveRankingResponse> objectiveRanking(String period, Long departmentId) {
        return objectiveRanking(period, departmentId, null);
    }

    private List<ReportDtos.ObjectiveRankingResponse> objectiveRanking(String period, Long departmentId, Long objectiveId) {
        String normalizedPeriod = normalizePeriod(period);
        return filteredObjectives(normalizedPeriod, departmentId, objectiveId)
                .stream()
                .sorted(Comparator.comparing(Objective::completionPercentage).reversed())
                .map(objective -> new ReportDtos.ObjectiveRankingResponse(
                        objective.getId(),
                        objective.getName(),
                        objective.getDepartment().getName(),
                        objective.getAcademicPeriod().getName(),
                        objective.completionPercentage(),
                        objective.getKeyResults().size()
                ))
                .toList();
    }

    public ReportDtos.PeriodComparisonResponse periodComparison(String basePeriod, String comparePeriod, Long departmentId) {
        return periodComparison(basePeriod, comparePeriod, departmentId, null);
    }

    public ReportDtos.PeriodComparisonResponse periodComparison(String basePeriod, String comparePeriod, Long departmentId, Long objectiveId) {
        String normalizedBase = requirePeriod(basePeriod, "basePeriod");
        String normalizedCompare = requirePeriod(comparePeriod, "comparePeriod");
        ReportDtos.GeneralReportResponse base = general(normalizedBase, departmentId, objectiveId);
        ReportDtos.GeneralReportResponse compare = general(normalizedCompare, departmentId, objectiveId);
        return new ReportDtos.PeriodComparisonResponse(
                normalizedBase,
                normalizedCompare,
                base,
                compare,
                compare.averageObjectiveCoverage().subtract(base.averageObjectiveCoverage()).setScale(2, RoundingMode.HALF_UP),
                compare.averageKeyResultCoverage().subtract(base.averageKeyResultCoverage()).setScale(2, RoundingMode.HALF_UP)
        );
    }

    public ReportDtos.ConsolidatedReportResponse consolidated(String period, Long departmentId, Long objectiveId) {
        return new ReportDtos.ConsolidatedReportResponse(
                general(period, departmentId, objectiveId),
                departments(period, departmentId, objectiveId),
                objectiveRanking(period, departmentId, objectiveId)
        );
    }

    public String csv(String period, Long departmentId, Long objectiveId) {
        ReportDtos.ConsolidatedReportResponse report = consolidated(period, departmentId, objectiveId);
        StringBuilder csv = new StringBuilder();
        csv.append("section,name,value\n");
        csv.append("filter,period,").append(report.general().period() == null ? "" : report.general().period()).append('\n');
        csv.append("filter,departmentId,").append(report.general().departmentId() == null ? "" : report.general().departmentId()).append('\n');
        csv.append("filter,objectiveId,").append(report.general().objectiveId() == null ? "" : report.general().objectiveId()).append('\n');
        csv.append("general,totalProjects,").append(report.general().totalProjects()).append('\n');
        csv.append("general,totalObjectives,").append(report.general().totalObjectives()).append('\n');
        csv.append("general,totalKeyResults,").append(report.general().totalKeyResults()).append('\n');
        csv.append("general,averageObjectiveCoverage,").append(report.general().averageObjectiveCoverage()).append('\n');
        for (ReportDtos.DepartmentReportResponse department : report.departments()) {
            csv.append("department,").append(escapeCsv(department.departmentName())).append(',')
                    .append(department.averageObjectiveCoverage()).append('\n');
        }
        for (ReportDtos.ObjectiveRankingResponse objective : report.objectiveRanking()) {
            csv.append("objective,").append(escapeCsv(objective.objectiveName())).append(',')
                    .append(objective.coveragePercentage()).append('\n');
        }
        return csv.toString();
    }

    public byte[] pdf(String period, Long departmentId, Long objectiveId) {
        String text = csv(period, departmentId, objectiveId)
                .replace("section,name,value", "Reporte consolidado MSP")
                .replace(",", "  ");
        return minimalPdf(text);
    }

    private ReportDtos.DepartmentReportResponse departmentReport(
            Department department,
            List<Project> projects,
            List<Objective> objectives
    ) {
        List<Project> departmentProjects = projects.stream()
                .filter(project -> project.getDepartment() != null)
                .filter(project -> department.getId().equals(project.getDepartment().getId()))
                .toList();
        List<Objective> departmentObjectives = objectives.stream()
                .filter(objective -> department.getId().equals(objective.getDepartment().getId()))
                .toList();
        return new ReportDtos.DepartmentReportResponse(
                department.getId(),
                department.getName(),
                departmentProjects.size(),
                departmentObjectives.size(),
                keyResultsFrom(departmentObjectives).size(),
                averageObjectiveCoverage(departmentObjectives)
        );
    }

    private List<Project> filteredProjects(String period, Long departmentId, Long objectiveId) {
        Set<Long> projectIdsForObjective = projectIdsForObjective(objectiveId);
        if (projectIdsForObjective != null && projectIdsForObjective.isEmpty()) {
            return List.of();
        }
        PeriodRange requestedPeriod = period == null ? null : parsePeriod(period);
        List<Long> projectIds = projectIdsForObjective == null ? List.of(-1L) : List.copyOf(projectIdsForObjective);
        List<Project> candidates = period == null && departmentId == null && projectIdsForObjective == null
                ? projectRepository.findAll()
                : projectRepository.findReportCandidates(
                        requestedPeriod != null,
                        requestedPeriod == null ? 0 : requestedPeriod.startIndex(),
                        requestedPeriod == null ? 0 : requestedPeriod.endIndex(),
                        departmentId,
                        projectIdsForObjective != null,
                        projectIds
                );
        return candidates
                .stream()
                .filter(project -> periodMatches(project, period))
                .filter(project -> departmentId == null || project.getDepartment() != null && departmentId.equals(project.getDepartment().getId()))
                .filter(project -> projectIdsForObjective == null || project.getId() != null && projectIdsForObjective.contains(project.getId()))
                .toList();
    }

    private List<Objective> filteredObjectives(String period, Long departmentId, Long objectiveId) {
        PeriodRange requestedPeriod = period == null ? null : parsePeriod(period);
        List<Objective> candidates = period == null && departmentId == null && objectiveId == null
                ? objectiveRepository.findAll()
                : objectiveRepository.findReportCandidates(
                        requestedPeriod != null,
                        requestedPeriod == null ? 0 : requestedPeriod.startIndex(),
                        requestedPeriod == null ? 0 : requestedPeriod.endIndex(),
                        departmentId,
                        objectiveId
                );
        return candidates
                .stream()
                .filter(objective -> periodMatches(objective, period))
                .filter(objective -> departmentId == null || departmentId.equals(objective.getDepartment().getId()))
                .filter(objective -> objectiveId == null || objectiveId.equals(objective.getId()))
                .toList();
    }

    private Set<Long> projectIdsForObjective(Long objectiveId) {
        if (objectiveId == null) {
            return null;
        }
        return new LinkedHashSet<>(linkRepository.findActiveProjectIdsByObjectiveId(objectiveId));
    }

    private boolean periodMatches(Project project, String period) {
        if (period == null) {
            return true;
        }
        PeriodRange requestedPeriod = parsePeriod(period);
        PeriodRange projectStart = parsePeriod(project.getStartPeriod());
        PeriodRange projectEnd = project.getEndPeriod() == null || project.getEndPeriod().isBlank()
                ? projectStart
                : parsePeriod(project.getEndPeriod().trim());
        return overlaps(
                new PeriodRange(projectStart.startIndex(), Math.max(projectStart.endIndex(), projectEnd.endIndex())),
                requestedPeriod
        );
    }

    private boolean periodMatches(Objective objective, String period) {
        if (period == null) {
            return true;
        }
        if (objective.getAcademicPeriod() == null || objective.getAcademicPeriod().getName() == null) {
            return false;
        }
        return overlaps(parsePeriod(objective.getAcademicPeriod().getName()), parsePeriod(period));
    }

    private List<KeyResult> keyResultsFrom(List<Objective> objectives) {
        return objectives.stream().flatMap(objective -> objective.getKeyResults().stream()).toList();
    }

    private BigDecimal averageObjectiveCoverage(List<Objective> objectives) {
        if (objectives.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = objectives.stream().map(Objective::completionPercentage).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(objectives.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageKeyResultCoverage(List<KeyResult> keyResults) {
        if (keyResults.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = keyResults.stream()
                .map(keyResult -> keyResult.getProgressPercentage() == null ? BigDecimal.ZERO : keyResult.getProgressPercentage())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(keyResults.size()), 2, RoundingMode.HALF_UP);
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        String trimmed = period.trim();
        if (!PERIOD_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El periodo debe tener formato YYYY-Q1..Q4 o YYYY-1..2.");
        }
        return trimmed;
    }

    private String requirePeriod(String period, String name) {
        String normalized = normalizePeriod(period);
        if (normalized == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, name + " es obligatorio.");
        }
        return normalized;
    }

    private PeriodRange parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El periodo debe tener formato YYYY-Q1..Q4 o YYYY-1..2.");
        }
        Matcher matcher = PERIOD_PATTERN.matcher(period.trim());
        if (!matcher.matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El periodo debe tener formato YYYY-Q1..Q4 o YYYY-1..2.");
        }
        int year = Integer.parseInt(period.trim().substring(0, 4));
        String term = matcher.group(1);
        int startQuarter;
        int endQuarter;
        if (term.startsWith("Q")) {
            startQuarter = Integer.parseInt(term.substring(1));
            endQuarter = startQuarter;
        } else if ("1".equals(term)) {
            startQuarter = 1;
            endQuarter = 2;
        } else {
            startQuarter = 3;
            endQuarter = 4;
        }
        int yearBase = year * 4;
        return new PeriodRange(yearBase + startQuarter, yearBase + endQuarter);
    }

    private boolean overlaps(PeriodRange first, PeriodRange second) {
        return first.startIndex() <= second.endIndex() && second.startIndex() <= first.endIndex();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private byte[] minimalPdf(String text) {
        String[] lines = text.lines().limit(28).toArray(String[]::new);
        StringBuilder content = new StringBuilder("BT /F1 12 Tf 50 780 Td ");
        for (String line : lines) {
            content.append('(').append(escapePdf(line)).append(") Tj 0 -18 Td ");
        }
        content.append("ET");
        String stream = content.toString();
        String pdf = """
                %%PDF-1.4
                1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
                2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
                3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj
                4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
                5 0 obj << /Length %d >> stream
                %s
                endstream endobj
                xref
                0 6
                0000000000 65535 f
                trailer << /Root 1 0 R /Size 6 >>
                startxref
                0
                %%%%EOF
                """.formatted(stream.getBytes(StandardCharsets.UTF_8).length, stream);
        return pdf.getBytes(StandardCharsets.UTF_8);
    }

    private String escapePdf(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private record PeriodRange(int startIndex, int endIndex) {
    }
}
