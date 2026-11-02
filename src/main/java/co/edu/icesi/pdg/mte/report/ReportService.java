package co.edu.icesi.pdg.mte.report;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.ReportDtos;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResultStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class ReportService {
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(Q[1-4]|[1-2])$");

    private final ProjectRepository projectRepository;
    private final KeyResultRepository keyResultRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectKeyResultLinkRepository linkRepository;

    public ReportService(
            ProjectRepository projectRepository,
            KeyResultRepository keyResultRepository,
            DepartmentRepository departmentRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        this.projectRepository = projectRepository;
        this.keyResultRepository = keyResultRepository;
        this.departmentRepository = departmentRepository;
        this.linkRepository = linkRepository;
    }

    public ReportDtos.GeneralReportResponse general(String period, Long departmentId) {
        return general(snapshot(normalizePeriod(period)), departmentId);
    }

    public List<ReportDtos.DepartmentReportResponse> departments(String period) {
        return departments(snapshot(normalizePeriod(period)), null);
    }

    public List<ReportDtos.KeyResultRankingResponse> keyResultRanking(String period, Long departmentId) {
        return keyResultRanking(snapshot(normalizePeriod(period)), departmentId);
    }

    public ReportDtos.PeriodComparisonResponse periodComparison(String basePeriod, String comparePeriod, Long departmentId) {
        String normalizedBase = requirePeriod(basePeriod, "basePeriod");
        String normalizedCompare = requirePeriod(comparePeriod, "comparePeriod");
        ReportDtos.GeneralReportResponse base = general(normalizedBase, departmentId);
        ReportDtos.GeneralReportResponse compare = general(normalizedCompare, departmentId);
        return new ReportDtos.PeriodComparisonResponse(normalizedBase, normalizedCompare, base, compare);
    }

    public ReportDtos.ConsolidatedReportResponse consolidated(String period, Long departmentId) {
        Snapshot snapshot = snapshot(normalizePeriod(period));
        return new ReportDtos.ConsolidatedReportResponse(
                general(snapshot, departmentId),
                departments(snapshot, departmentId),
                keyResultRanking(snapshot, departmentId)
        );
    }

    public String csv(String period, Long departmentId) {
        ReportDtos.ConsolidatedReportResponse report = consolidated(period, departmentId);
        StringBuilder csv = new StringBuilder();
        csv.append("seccion,nombre,valor\n");
        csv.append("filtro,periodo,").append(report.general().period() == null ? "" : report.general().period()).append('\n');
        csv.append("filtro,departamentoId,").append(report.general().departmentId() == null ? "" : report.general().departmentId()).append('\n');
        csv.append("general,totalProyectos,").append(report.general().totalProjects()).append('\n');
        csv.append("general,proyectosActivos,").append(report.general().activeProjects()).append('\n');
        csv.append("general,proyectosFinalizados,").append(report.general().completedProjects()).append('\n');
        csv.append("general,totalKRs,").append(report.general().totalKeyResults()).append('\n');
        csv.append("general,krsActivos,").append(report.general().activeKeyResults()).append('\n');
        csv.append("general,krsCerrados,").append(report.general().closedKeyResults()).append('\n');
        for (ReportDtos.DepartmentReportResponse department : report.departments()) {
            String name = escapeCsv(department.departmentName());
            csv.append("departamentoProyectos,").append(name).append(',').append(department.projects()).append('\n');
            csv.append("departamentoKRs,").append(name).append(',').append(department.keyResults()).append('\n');
            csv.append("departamentoKRsCerrados,").append(name).append(',').append(department.closedKeyResults()).append('\n');
        }
        for (ReportDtos.KeyResultRankingResponse keyResult : report.keyResultRanking()) {
            csv.append("krAvanceRegistrado,").append(escapeCsv(keyResult.keyResultName())).append(',')
                    .append(keyResult.progressPercentage() == null ? "" : keyResult.progressPercentage().toPlainString())
                    .append('\n');
        }
        return csv.toString();
    }

    public byte[] pdf(String period, Long departmentId) {
        String text = csv(period, departmentId)
                .replace("seccion,nombre,valor", "Reporte consolidado MSP")
                .replace(",", "  ");
        return minimalPdf(text);
    }

    private ReportDtos.GeneralReportResponse general(Snapshot snapshot, Long departmentId) {
        List<Project> projects = projectsOf(snapshot, departmentId);
        List<KeyResult> keyResults = keyResultsOf(snapshot, departmentId);
        return new ReportDtos.GeneralReportResponse(
                snapshot.period(),
                departmentId,
                projects.size(),
                projects.stream().filter(project -> project.getStatus() == ProjectStatus.ACTIVO).count(),
                projects.stream().filter(project -> project.getStatus() == ProjectStatus.FINALIZADO).count(),
                keyResults.size(),
                countByStatus(keyResults, KeyResultStatus.ACTIVO),
                countByStatus(keyResults, KeyResultStatus.CERRADO)
        );
    }

    private List<ReportDtos.DepartmentReportResponse> departments(Snapshot snapshot, Long departmentId) {
        return departmentRepository.findAll()
                .stream()
                .filter(department -> departmentId == null || departmentId.equals(department.getId()))
                .sorted(Comparator.comparing(Department::getName))
                .map(department -> {
                    List<KeyResult> keyResults = keyResultsOf(snapshot, department.getId());
                    return new ReportDtos.DepartmentReportResponse(
                            department.getId(),
                            department.getName(),
                            projectsOf(snapshot, department.getId()).size(),
                            keyResults.size(),
                            countByStatus(keyResults, KeyResultStatus.CERRADO)
                    );
                })
                .toList();
    }

    private List<ReportDtos.KeyResultRankingResponse> keyResultRanking(Snapshot snapshot, Long departmentId) {
        return keyResultsOf(snapshot, departmentId)
                .stream()
                .sorted(Comparator.comparing(KeyResult::getProgressPercentage,
                                Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()))
                        .thenComparing(KeyResult::getId))
                .map(keyResult -> new ReportDtos.KeyResultRankingResponse(
                        keyResult.getId(),
                        keyResult.getName(),
                        Mapper.goalNames(keyResult),
                        Mapper.strategicBetNames(keyResult),
                        keyResult.getAcademicPeriod() == null ? null : keyResult.getAcademicPeriod().getName(),
                        keyResult.getProgressPercentage(),
                        snapshot.linksByKeyResult().getOrDefault(keyResult.getId(), List.of()).size()
                ))
                .toList();
    }

    private Snapshot snapshot(String period) {
        List<Project> allProjects = projectRepository.findAll();
        List<Project> projects = allProjects.stream()
                .filter(project -> periodMatches(project, period))
                .toList();
        List<KeyResult> keyResults = keyResultRepository.findAll()
                .stream()
                .filter(keyResult -> !keyResult.isArchived())
                .filter(keyResult -> periodMatches(keyResult, period))
                .toList();
        Map<Long, List<ProjectKeyResultLink>> linksByKeyResult = linksByKeyResult(keyResults);
        return new Snapshot(period, projects, keyResults, linksByKeyResult, departmentsByKeyResult(allProjects, linksByKeyResult));
    }

    private List<Project> projectsOf(Snapshot snapshot, Long departmentId) {
        return snapshot.projects()
                .stream()
                .filter(project -> departmentId == null
                        || project.getDepartment() != null && departmentId.equals(project.getDepartment().getId()))
                .toList();
    }

    private List<KeyResult> keyResultsOf(Snapshot snapshot, Long departmentId) {
        return snapshot.keyResults()
                .stream()
                .filter(keyResult -> departmentId == null
                        || snapshot.departmentsByKeyResult().getOrDefault(keyResult.getId(), Set.of()).contains(departmentId))
                .toList();
    }

    private Map<Long, List<ProjectKeyResultLink>> linksByKeyResult(List<KeyResult> keyResults) {
        List<Long> keyResultIds = keyResults.stream()
                .map(KeyResult::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (keyResultIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ProjectKeyResultLink>> links = new LinkedHashMap<>();
        for (ProjectKeyResultLink link : linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(keyResultIds)) {
            links.computeIfAbsent(link.getKeyResult().getId(), ignored -> new ArrayList<>()).add(link);
        }
        return links;
    }

    /**
     * Un KR pertenece a un departamento cuando un proyecto de ese departamento lo referencia,
     * ya sea mediante un vinculo activo o como KR principal del proyecto.
     */
    private Map<Long, Set<Long>> departmentsByKeyResult(
            List<Project> projects,
            Map<Long, List<ProjectKeyResultLink>> linksByKeyResult
    ) {
        Map<Long, Set<Long>> departments = new LinkedHashMap<>();
        for (Project project : projects) {
            if (project.getKeyResult() != null && project.getDepartment() != null) {
                departments.computeIfAbsent(project.getKeyResult().getId(), ignored -> new LinkedHashSet<>())
                        .add(project.getDepartment().getId());
            }
        }
        linksByKeyResult.forEach((keyResultId, links) -> links.stream()
                .map(ProjectKeyResultLink::getProject)
                .filter(project -> project != null && project.getDepartment() != null)
                .forEach(project -> departments.computeIfAbsent(keyResultId, ignored -> new LinkedHashSet<>())
                        .add(project.getDepartment().getId())));
        return departments;
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

    private boolean periodMatches(KeyResult keyResult, String period) {
        if (period == null) {
            return true;
        }
        if (keyResult.getAcademicPeriod() == null || keyResult.getAcademicPeriod().getName() == null) {
            return false;
        }
        return overlaps(parsePeriod(keyResult.getAcademicPeriod().getName()), parsePeriod(period));
    }

    private long countByStatus(List<KeyResult> keyResults, KeyResultStatus status) {
        return keyResults.stream().filter(keyResult -> keyResult.getStatus() == status).count();
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

    private record Snapshot(
            String period,
            List<Project> projects,
            List<KeyResult> keyResults,
            Map<Long, List<ProjectKeyResultLink>> linksByKeyResult,
            Map<Long, Set<Long>> departmentsByKeyResult
    ) {
    }
}
