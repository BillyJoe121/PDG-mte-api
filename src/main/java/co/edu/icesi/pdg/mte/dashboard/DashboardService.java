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
import co.edu.icesi.pdg.mte.strategy.Objective;
import co.edu.icesi.pdg.mte.strategy.ObjectiveRepository;
import co.edu.icesi.pdg.mte.strategy.ObjectiveStatus;
import co.edu.icesi.pdg.mte.strategy.StrategicBet;
import co.edu.icesi.pdg.mte.strategy.StrategicBetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
public class DashboardService {
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(Q[1-4]|[1-2])$");
    private static final BigDecimal COMPLETED = BigDecimal.valueOf(100);
    private static final BigDecimal ON_TRACK = BigDecimal.valueOf(60);
    private static final BigDecimal AT_RISK = BigDecimal.valueOf(30);

    private final ProjectRepository projectRepository;
    private final ObjectiveRepository objectiveRepository;
    private final DepartmentRepository departmentRepository;
    private final StrategicBetRepository strategicBetRepository;
    private final InstitutionalGoalRepository goalRepository;
    private final ProjectKeyResultLinkRepository linkRepository;

    public DashboardService(
            ProjectRepository projectRepository,
            ObjectiveRepository objectiveRepository,
            DepartmentRepository departmentRepository,
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        this.projectRepository = projectRepository;
        this.objectiveRepository = objectiveRepository;
        this.departmentRepository = departmentRepository;
        this.strategicBetRepository = strategicBetRepository;
        this.goalRepository = goalRepository;
        this.linkRepository = linkRepository;
    }

    public DashboardDtos.DashboardSummaryResponse summary(String period) {
        String normalizedPeriod = resolvePeriod(period);
        List<Project> projects = filteredProjects(normalizedPeriod);
        List<Objective> objectives = filteredObjectives(normalizedPeriod);
        List<KeyResult> keyResults = keyResultsFrom(objectives);
        ObjectiveProgressBuckets objectiveBuckets = objectiveProgressBuckets(objectives);

        return new DashboardDtos.DashboardSummaryResponse(
                normalizedPeriod,
                countProjects(projects, ProjectStatus.ACTIVO),
                countProjects(projects, ProjectStatus.FINALIZADO),
                countProjects(projects, ProjectStatus.BORRADOR),
                countProjects(projects, ProjectStatus.SUSPENDIDO),
                countProjects(projects, ProjectStatus.ARCHIVADO),
                objectives.stream().filter(objective -> objective.getStatus() == ObjectiveStatus.ACTIVO).count(),
                objectives.stream().filter(objective -> objective.completionPercentage().compareTo(AT_RISK) < 0).count(),
                objectiveBuckets.completed(),
                objectiveBuckets.above50(),
                objectiveBuckets.between0And50(),
                objectiveBuckets.atZero(),
                keyResults.stream().filter(this::isCompleted).count(),
                keyResults.stream().filter(keyResult -> !isCompleted(keyResult)).count(),
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

        keyResultsFrom(filteredObjectives(resolvePeriod(period))).stream()
                .map(this::progressBucket)
                .forEach(bucket -> buckets.merge(bucket, 1L, Long::sum));

        return buckets.entrySet()
                .stream()
                .map(entry -> new DashboardDtos.ProgressBucketResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<DashboardDtos.DepartmentExecutionResponse> departments(String period) {
        String normalizedPeriod = resolvePeriod(period);
        List<Project> projects = filteredProjects(normalizedPeriod);
        List<Objective> objectives = filteredObjectives(normalizedPeriod);

        return departmentRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Department::getName))
                .map(department -> departmentSummary(department, projects, objectives))
                .toList();
    }

    public List<DashboardDtos.StrategicBetExecutionResponse> strategicBets(String period) {
        String normalizedPeriod = resolvePeriod(period);
        List<Objective> objectives = filteredObjectives(normalizedPeriod);

        return strategicBetRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(StrategicBet::getName))
                .map(bet -> strategicBetSummary(bet, objectives, normalizedPeriod))
                .toList();
    }

    public List<DashboardDtos.GoalExecutionResponse> goals(String period) {
        String normalizedPeriod = resolvePeriod(period);
        List<Objective> objectives = filteredObjectives(normalizedPeriod);

        return goalRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(InstitutionalGoal::getName))
                .map(goal -> goalSummary(goal, objectives, normalizedPeriod))
                .toList();
    }

    private DashboardDtos.DepartmentExecutionResponse departmentSummary(
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
        List<KeyResult> keyResults = keyResultsFrom(departmentObjectives);
        ObjectiveProgressBuckets objectiveBuckets = objectiveProgressBuckets(departmentObjectives);

        return new DashboardDtos.DepartmentExecutionResponse(
                department.getId(),
                department.getName(),
                countProjects(departmentProjects, ProjectStatus.ACTIVO),
                countProjects(departmentProjects, ProjectStatus.FINALIZADO),
                departmentObjectives.size(),
                objectiveBuckets.completed(),
                objectiveBuckets.above50(),
                objectiveBuckets.between0And50(),
                objectiveBuckets.atZero(),
                keyResults.stream().filter(this::isCompleted).count(),
                keyResults.stream().filter(keyResult -> !isCompleted(keyResult)).count()
        );
    }

    private DashboardDtos.StrategicBetExecutionResponse strategicBetSummary(
            StrategicBet bet,
            List<Objective> objectives,
            String period
    ) {
        List<Objective> betObjectives = objectives.stream()
                .filter(objective -> bet.getId().equals(objective.getStrategicBet().getId()))
                .toList();
        ExecutionBreakdown breakdown = executionBreakdown(betObjectives, period);

        return new DashboardDtos.StrategicBetExecutionResponse(
                bet.getId(),
                bet.getName(),
                betObjectives.size(),
                breakdown.objectives().completed(),
                breakdown.objectives().above50(),
                breakdown.objectives().between0And50(),
                breakdown.objectives().atZero(),
                breakdown.keyResults(),
                breakdown.completedProjects(),
                breakdown.inProgressProjects()
        );
    }

    private DashboardDtos.GoalExecutionResponse goalSummary(
            InstitutionalGoal goal,
            List<Objective> objectives,
            String period
    ) {
        List<Objective> goalObjectives = objectives.stream()
                .filter(objective -> goal.getId().equals(objective.getGoal().getId()))
                .toList();
        ExecutionBreakdown breakdown = executionBreakdown(goalObjectives, period);

        return new DashboardDtos.GoalExecutionResponse(
                goal.getId(),
                goal.getName(),
                goalObjectives.size(),
                breakdown.objectives().completed(),
                breakdown.objectives().above50(),
                breakdown.objectives().between0And50(),
                breakdown.objectives().atZero(),
                breakdown.keyResults(),
                breakdown.completedProjects(),
                breakdown.inProgressProjects()
        );
    }

    private ExecutionBreakdown executionBreakdown(List<Objective> objectives, String period) {
        List<KeyResult> keyResults = keyResultsFrom(objectives);
        ObjectiveProgressBuckets objectiveBuckets = objectiveProgressBuckets(objectives);
        Set<Long> completedProjects = new LinkedHashSet<>();
        Set<Long> inProgressProjects = new LinkedHashSet<>();

        for (KeyResult keyResult : keyResults) {
            for (ProjectKeyResultLink link : linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(keyResult.getId())) {
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
                objectiveBuckets,
                keyResults.size(),
                completedProjects.size(),
                inProgressProjects.size()
        );
    }

    private List<Project> filteredProjects(String period) {
        return projectRepository.findAll()
                .stream()
                .filter(project -> periodMatches(project, period))
                .toList();
    }

    private List<Objective> filteredObjectives(String period) {
        return objectiveRepository.findAll()
                .stream()
                .filter(objective -> periodMatches(objective, period))
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

    private boolean periodMatches(Objective objective, String period) {
        if (period == null) {
            return true;
        }
        if (objective.getAcademicPeriod() == null || objective.getAcademicPeriod().getName() == null) {
            return false;
        }
        return overlaps(parsePeriod(objective.getAcademicPeriod().getName()), parsePeriod(period));
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

    private List<KeyResult> keyResultsFrom(List<Objective> objectives) {
        return objectives.stream()
                .flatMap(objective -> objective.getKeyResults().stream())
                .toList();
    }

    private boolean isCompleted(KeyResult keyResult) {
        return progressOf(keyResult).compareTo(COMPLETED) >= 0;
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

    private ObjectiveProgressBuckets objectiveProgressBuckets(List<Objective> objectives) {
        long completed = 0;
        long above50 = 0;
        long between0And50 = 0;
        long atZero = 0;

        for (Objective objective : objectives) {
            BigDecimal progress = objective.completionPercentage();
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

        return new ObjectiveProgressBuckets(completed, above50, between0And50, atZero);
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

    private record ObjectiveProgressBuckets(long completed, long above50, long between0And50, long atZero) {
    }

    private record ExecutionBreakdown(
            ObjectiveProgressBuckets objectives,
            long keyResults,
            long completedProjects,
            long inProgressProjects
    ) {
    }
}
