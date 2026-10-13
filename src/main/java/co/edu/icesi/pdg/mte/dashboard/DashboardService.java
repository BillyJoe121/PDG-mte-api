package co.edu.icesi.pdg.mte.dashboard;

import co.edu.icesi.pdg.mte.api.dto.DashboardDtos;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoal;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoalRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResultStatus;
import co.edu.icesi.pdg.mte.strategy.StrategicBet;
import co.edu.icesi.pdg.mte.strategy.StrategicBetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(Q[1-4]|[1-2])$");
    private static final BigDecimal COMPLETED = BigDecimal.valueOf(100);
    private static final BigDecimal ON_TRACK = BigDecimal.valueOf(60);
    private static final BigDecimal AT_RISK = BigDecimal.valueOf(30);

    private final ProjectRepository projectRepository;
    private final KeyResultRepository keyResultRepository;
    private final DepartmentRepository departmentRepository;
    private final StrategicBetRepository strategicBetRepository;
    private final InstitutionalGoalRepository goalRepository;
    private final ProjectKeyResultLinkRepository linkRepository;

    public DashboardService(
            ProjectRepository projectRepository,
            KeyResultRepository keyResultRepository,
            DepartmentRepository departmentRepository,
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        this.projectRepository = projectRepository;
        this.keyResultRepository = keyResultRepository;
        this.departmentRepository = departmentRepository;
        this.strategicBetRepository = strategicBetRepository;
        this.goalRepository = goalRepository;
        this.linkRepository = linkRepository;
    }

    public DashboardDtos.DashboardSummaryResponse summary(String period) {
        String normalizedPeriod = resolvePeriod(period);
        List<Project> projects = filteredProjects(normalizedPeriod);
        List<KeyResult> keyResults = filteredKeyResults(normalizedPeriod);
        ProgressBuckets buckets = progressBuckets(keyResults);

        return new DashboardDtos.DashboardSummaryResponse(
                normalizedPeriod,
                countProjects(projects, ProjectStatus.ACTIVO),
                countProjects(projects, ProjectStatus.FINALIZADO),
                countProjects(projects, ProjectStatus.BORRADOR),
                countProjects(projects, ProjectStatus.SUSPENDIDO),
                countProjects(projects, ProjectStatus.ARCHIVADO),
                keyResults.stream().filter(keyResult -> keyResult.getStatus() == KeyResultStatus.ACTIVO).count(),
                keyResults.stream().filter(keyResult -> progressOf(keyResult).compareTo(AT_RISK) < 0).count(),
                buckets.completed(),
                buckets.above50(),
                buckets.between0And50(),
                buckets.atZero(),
                keyResults.size() - buckets.completed(),
                averageKeyResultCoverage(keyResults)
        );
    }

    public List<DashboardDtos.CountByStatusResponse> projectsByStatus(String period) {
        List<Project> projects = filteredProjects(resolvePeriod(period));
        return List.of(ProjectStatus.values())
                .stream()
                .map(status -> new DashboardDtos.CountByStatusResponse(status.name(), countProjects(projects, status)))
                .toList();
    }

    public List<DashboardDtos.ProgressBucketResponse> keyResultsByProgress(String period) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("COMPLETED", 0L);
        buckets.put("ON_TRACK", 0L);
        buckets.put("AT_RISK", 0L);
        buckets.put("LOW", 0L);

        filteredKeyResults(resolvePeriod(period)).stream()
                .map(this::progressBucket)
                .forEach(bucket -> buckets.merge(bucket, 1L, Long::sum));

        return buckets.entrySet()
                .stream()
                .map(entry -> new DashboardDtos.ProgressBucketResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<DashboardDtos.DepartmentExecutionResponse> departments(String period) {
        String normalizedPeriod = resolvePeriod(period);
        List<Project> allProjects = projectRepository.findAll();
        List<Project> projects = allProjects.stream()
                .filter(project -> periodMatches(project, normalizedPeriod))
                .toList();
        List<KeyResult> keyResults = filteredKeyResults(normalizedPeriod);
        Map<Long, Set<Long>> departmentsByKeyResult = departmentsByKeyResult(keyResults, allProjects);

        return departmentRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Department::getName))
                .map(department -> departmentSummary(department, projects, keyResults, departmentsByKeyResult))
                .toList();
    }

    public List<DashboardDtos.StrategicBetExecutionResponse> strategicBets(String period) {
        String normalizedPeriod = resolvePeriod(period);
        List<KeyResult> keyResults = filteredKeyResults(normalizedPeriod);
        Map<Long, List<ProjectKeyResultLink>> linksByKeyResult = linksByKeyResult(keyResults);

        return strategicBetRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(StrategicBet::getName))
                .map(bet -> {
                    ExecutionBreakdown breakdown = executionBreakdown(
                            keyResults,
                            keyResult -> keyResult.getStrategicBets().stream().anyMatch(item -> bet.getId().equals(item.getId())),
                            linksByKeyResult,
                            normalizedPeriod
                    );
                    return new DashboardDtos.StrategicBetExecutionResponse(
                            bet.getId(),
                            bet.getName(),
                            breakdown.keyResults(),
                            breakdown.buckets().completed(),
                            breakdown.buckets().above50(),
                            breakdown.buckets().between0And50(),
                            breakdown.buckets().atZero(),
                            breakdown.completedProjects(),
                            breakdown.inProgressProjects()
                    );
                })
                .toList();
    }

    public List<DashboardDtos.GoalExecutionResponse> goals(String period) {
        String normalizedPeriod = resolvePeriod(period);
        List<KeyResult> keyResults = filteredKeyResults(normalizedPeriod);
        Map<Long, List<ProjectKeyResultLink>> linksByKeyResult = linksByKeyResult(keyResults);

        return goalRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(InstitutionalGoal::getName))
                .map(goal -> {
                    ExecutionBreakdown breakdown = executionBreakdown(
                            keyResults,
                            keyResult -> keyResult.getGoals().stream().anyMatch(item -> goal.getId().equals(item.getId())),
                            linksByKeyResult,
                            normalizedPeriod
                    );
                    return new DashboardDtos.GoalExecutionResponse(
                            goal.getId(),
                            goal.getName(),
                            breakdown.keyResults(),
                            breakdown.buckets().completed(),
                            breakdown.buckets().above50(),
                            breakdown.buckets().between0And50(),
                            breakdown.buckets().atZero(),
                            breakdown.completedProjects(),
                            breakdown.inProgressProjects()
                    );
                })
                .toList();
    }

    private DashboardDtos.DepartmentExecutionResponse departmentSummary(
            Department department,
            List<Project> projects,
            List<KeyResult> keyResults,
            Map<Long, Set<Long>> departmentsByKeyResult
    ) {
        List<Project> departmentProjects = projects.stream()
                .filter(project -> project.getDepartment() != null)
                .filter(project -> department.getId().equals(project.getDepartment().getId()))
                .toList();
        List<KeyResult> departmentKeyResults = keyResults.stream()
                .filter(keyResult -> departmentsByKeyResult.getOrDefault(keyResult.getId(), Set.of()).contains(department.getId()))
                .toList();
        ProgressBuckets buckets = progressBuckets(departmentKeyResults);

        return new DashboardDtos.DepartmentExecutionResponse(
                department.getId(),
                department.getName(),
                countProjects(departmentProjects, ProjectStatus.ACTIVO),
                countProjects(departmentProjects, ProjectStatus.FINALIZADO),
                departmentKeyResults.size(),
                buckets.completed(),
                buckets.above50(),
                buckets.between0And50(),
                buckets.atZero(),
                departmentKeyResults.size() - buckets.completed()
        );
    }

    /**
     * Un KR pertenece a un departamento cuando un proyecto de ese departamento lo referencia,
     * ya sea mediante un vinculo activo o como KR principal del proyecto.
     */
    private Map<Long, Set<Long>> departmentsByKeyResult(List<KeyResult> keyResults, List<Project> projects) {
        Map<Long, Set<Long>> departments = new LinkedHashMap<>();
        for (Project project : projects) {
            if (project.getKeyResult() != null && project.getDepartment() != null) {
                departments.computeIfAbsent(project.getKeyResult().getId(), ignored -> new LinkedHashSet<>())
                        .add(project.getDepartment().getId());
            }
        }
        linksByKeyResult(keyResults).forEach((keyResultId, links) -> links.stream()
                .map(ProjectKeyResultLink::getProject)
                .filter(project -> project != null && project.getDepartment() != null)
                .forEach(project -> departments.computeIfAbsent(keyResultId, ignored -> new LinkedHashSet<>())
                        .add(project.getDepartment().getId())));
        return departments;
    }

    private ExecutionBreakdown executionBreakdown(
            List<KeyResult> allKeyResults,
            Predicate<KeyResult> ownerFilter,
            Map<Long, List<ProjectKeyResultLink>> linksByKeyResult,
            String period
    ) {
        List<KeyResult> keyResults = allKeyResults.stream().filter(ownerFilter).toList();
        Set<Long> completedProjects = new LinkedHashSet<>();
        Set<Long> inProgressProjects = new LinkedHashSet<>();

        for (KeyResult keyResult : keyResults) {
            for (ProjectKeyResultLink link : linksByKeyResult.getOrDefault(keyResult.getId(), List.of())) {
                Project project = link.getProject();
                if (project == null || project.getId() == null || !periodMatches(project, period)) {
                    continue;
                }
                if (project.getStatus() == ProjectStatus.FINALIZADO) {
                    completedProjects.add(project.getId());
                    inProgressProjects.remove(project.getId());
                } else if (!completedProjects.contains(project.getId())) {
                    inProgressProjects.add(project.getId());
                }
            }
        }

        return new ExecutionBreakdown(
                progressBuckets(keyResults),
                keyResults.size(),
                completedProjects.size(),
                inProgressProjects.size()
        );
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

    private List<Project> filteredProjects(String period) {
        return projectRepository.findAll()
                .stream()
                .filter(project -> periodMatches(project, period))
                .toList();
    }

    private List<KeyResult> filteredKeyResults(String period) {
        return keyResultRepository.findAll()
                .stream()
                .filter(keyResult -> periodMatches(keyResult, period))
                .toList();
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
        return overlaps(new PeriodRange(projectStart.startIndex(), Math.max(projectStart.endIndex(), projectEnd.endIndex())), requestedPeriod);
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

    private String resolvePeriod(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        String trimmed = period.trim();
        if (!PERIOD_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El periodo debe tener formato YYYY-Q1..Q4 o YYYY-1..2.");
        }
        return trimmed;
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

    private long countProjects(List<Project> projects, ProjectStatus status) {
        return projects.stream().filter(project -> project.getStatus() == status).count();
    }

    private String progressBucket(KeyResult keyResult) {
        BigDecimal progress = progressOf(keyResult);
        if (progress.compareTo(COMPLETED) >= 0) {
            return "COMPLETED";
        }
        if (progress.compareTo(ON_TRACK) >= 0) {
            return "ON_TRACK";
        }
        if (progress.compareTo(AT_RISK) >= 0) {
            return "AT_RISK";
        }
        return "LOW";
    }

    private BigDecimal progressOf(KeyResult keyResult) {
        return keyResult.getProgressPercentage() == null ? BigDecimal.ZERO : keyResult.getProgressPercentage();
    }

    private ProgressBuckets progressBuckets(List<KeyResult> keyResults) {
        long completed = 0;
        long above50 = 0;
        long between0And50 = 0;
        long atZero = 0;

        for (KeyResult keyResult : keyResults) {
            BigDecimal progress = progressOf(keyResult);
            if (progress.compareTo(COMPLETED) >= 0) {
                completed++;
            } else if (progress.compareTo(BigDecimal.valueOf(50)) > 0) {
                above50++;
            } else if (progress.compareTo(BigDecimal.ZERO) > 0) {
                between0And50++;
            } else {
                atZero++;
            }
        }

        return new ProgressBuckets(completed, above50, between0And50, atZero);
    }

    private BigDecimal averageKeyResultCoverage(List<KeyResult> keyResults) {
        if (keyResults.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = keyResults.stream()
                .map(this::progressOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(keyResults.size()), 2, RoundingMode.HALF_UP);
    }

    private record PeriodRange(int startIndex, int endIndex) {
    }

    private record ProgressBuckets(long completed, long above50, long between0And50, long atZero) {
    }

    private record ExecutionBreakdown(
            ProgressBuckets buckets,
            long keyResults,
            long completedProjects,
            long inProgressProjects
    ) {
    }
}
