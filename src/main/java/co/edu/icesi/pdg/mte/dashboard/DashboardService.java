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
    private final ProjectKeyResultLinkRepository linkRepository;

    public DashboardService(
            ProjectRepository projectRepository,
            ObjectiveRepository objectiveRepository,
            DepartmentRepository departmentRepository,
            StrategicBetRepository strategicBetRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        this.projectRepository = projectRepository;
        this.objectiveRepository = objectiveRepository;
        this.departmentRepository = departmentRepository;
        this.strategicBetRepository = strategicBetRepository;
        this.linkRepository = linkRepository;
    }

    public DashboardDtos.DashboardSummaryResponse summary(String period) {
        String normalizedPeriod = normalizePeriod(period);
        List<Project> projects = filteredProjects(normalizedPeriod);
        List<Objective> objectives = filteredObjectives(normalizedPeriod);
        List<KeyResult> keyResults = keyResultsFrom(objectives);

        return new DashboardDtos.DashboardSummaryResponse(
                normalizedPeriod,
                countProjects(projects, ProjectStatus.ACTIVO),
                countProjects(projects, ProjectStatus.FINALIZADO),
                countProjects(projects, ProjectStatus.BORRADOR),
                countProjects(projects, ProjectStatus.SUSPENDIDO),
                countProjects(projects, ProjectStatus.ARCHIVADO),
                objectives.stream().filter(objective -> objective.getStatus() == ObjectiveStatus.ACTIVO).count(),
                objectives.stream().filter(objective -> objective.completionPercentage().compareTo(AT_RISK) < 0).count(),
                keyResults.stream().filter(this::isCompleted).count(),
                keyResults.stream().filter(keyResult -> !isCompleted(keyResult)).count(),
                averageObjectiveCoverage(objectives),
                averageKeyResultCoverage(keyResults)
        );
    }

    public List<DashboardDtos.CountByStatusResponse> projectsByStatus(String period) {
        List<Project> projects = filteredProjects(normalizePeriod(period));
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

        keyResultsFrom(filteredObjectives(normalizePeriod(period))).stream()
                .map(this::progressBucket)
                .forEach(bucket -> buckets.merge(bucket, 1L, Long::sum));

        return buckets.entrySet()
                .stream()
                .map(entry -> new DashboardDtos.ProgressBucketResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<DashboardDtos.DepartmentExecutionResponse> departments(String period) {
        String normalizedPeriod = normalizePeriod(period);
        List<Project> projects = filteredProjects(normalizedPeriod);
        List<Objective> objectives = filteredObjectives(normalizedPeriod);

        return departmentRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Department::getName))
                .map(department -> departmentSummary(department, projects, objectives))
                .toList();
    }

    public List<DashboardDtos.StrategicBetExecutionResponse> strategicBets(String period) {
        String normalizedPeriod = normalizePeriod(period);
        List<Objective> objectives = filteredObjectives(normalizedPeriod);

        return strategicBetRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(StrategicBet::getName))
                .map(bet -> strategicBetSummary(bet, objectives, normalizedPeriod))
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

        return new DashboardDtos.DepartmentExecutionResponse(
                department.getId(),
                department.getName(),
                countProjects(departmentProjects, ProjectStatus.ACTIVO),
                countProjects(departmentProjects, ProjectStatus.FINALIZADO),
                departmentObjectives.size(),
                keyResults.stream().filter(this::isCompleted).count(),
                keyResults.stream().filter(keyResult -> !isCompleted(keyResult)).count(),
                averageObjectiveCoverage(departmentObjectives)
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
        List<KeyResult> keyResults = keyResultsFrom(betObjectives);
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

        return new DashboardDtos.StrategicBetExecutionResponse(
                bet.getId(),
                bet.getName(),
                betObjectives.size(),
                keyResults.size(),
                completedProjects.size(),
                inProgressProjects.size(),
                averageObjectiveCoverage(betObjectives)
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
        return period.equals(project.getStartPeriod()) || period.equals(project.getEndPeriod());
    }

    private boolean periodMatches(Objective objective, String period) {
        if (period == null) {
            return true;
        }
        return period.equals(objective.getAcademicPeriod().getName());
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

    private BigDecimal averageObjectiveCoverage(List<Objective> objectives) {
        if (objectives.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = objectives.stream()
                .map(Objective::completionPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(objectives.size()), 2, RoundingMode.HALF_UP);
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
}
