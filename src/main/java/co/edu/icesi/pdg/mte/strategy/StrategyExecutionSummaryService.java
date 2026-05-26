package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
class StrategyExecutionSummaryService {
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(Q[1-4]|[1-2])$");

    private final ObjectiveRepository objectiveRepository;
    private final ProjectKeyResultLinkRepository linkRepository;

    StrategyExecutionSummaryService(
            ObjectiveRepository objectiveRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        this.objectiveRepository = objectiveRepository;
        this.linkRepository = linkRepository;
    }

    StrategyDtos.ExecutionSummaryResponse forBet(Long betId, String periodFilter) {
        return forBets(List.of(betId), periodFilter).getOrDefault(betId, empty());
    }

    StrategyDtos.ExecutionSummaryResponse forGoal(Long goalId, String periodFilter) {
        return forGoals(List.of(goalId), periodFilter).getOrDefault(goalId, empty());
    }

    Map<Long, StrategyDtos.ExecutionSummaryResponse> forBets(Collection<Long> betIds, String periodFilter) {
        if (betIds.isEmpty()) {
            return Map.of();
        }
        return forBets(betIds, periodFilter, objectiveRepository.findByStrategicBetIdIn(new LinkedHashSet<>(betIds)));
    }

    Map<Long, StrategyDtos.ExecutionSummaryResponse> forBets(
            Collection<Long> betIds,
            String periodFilter,
            List<Objective> objectives
    ) {
        Set<Long> requestedIds = new LinkedHashSet<>(betIds);
        Map<Long, List<Objective>> objectivesByBet = objectives
                .stream()
                .filter(objective -> objective.getStrategicBet() != null)
                .filter(objective -> requestedIds.contains(objective.getStrategicBet().getId()))
                .collect(java.util.stream.Collectors.groupingBy(
                        objective -> objective.getStrategicBet().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        return buildAll(betIds, objectivesByBet, periodFilter);
    }

    Map<Long, StrategyDtos.ExecutionSummaryResponse> forGoals(Collection<Long> goalIds, String periodFilter) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        return forGoals(goalIds, periodFilter, objectiveRepository.findByGoalIdIn(new LinkedHashSet<>(goalIds)));
    }

    Map<Long, StrategyDtos.ExecutionSummaryResponse> forGoals(
            Collection<Long> goalIds,
            String periodFilter,
            List<Objective> objectives
    ) {
        Set<Long> requestedIds = new LinkedHashSet<>(goalIds);
        Map<Long, List<Objective>> objectivesByGoal = objectives
                .stream()
                .filter(objective -> objective.getGoal() != null)
                .filter(objective -> requestedIds.contains(objective.getGoal().getId()))
                .collect(java.util.stream.Collectors.groupingBy(
                        objective -> objective.getGoal().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        return buildAll(goalIds, objectivesByGoal, periodFilter);
    }

    StrategyDtos.ExecutionSummaryResponse empty() {
        return new StrategyDtos.ExecutionSummaryResponse(
                "0 objetivos completos, 0 en desarrollo. 0 KPIs completos, 0 en desarrollo. 0 proyectos completados, 0 en desarrollo.",
                0,
                0,
                0,
                0,
                0,
                0,
                List.of()
        );
    }

    private Map<Long, StrategyDtos.ExecutionSummaryResponse> buildAll(
            Collection<Long> ownerIds,
            Map<Long, List<Objective>> objectivesByOwner,
            String periodFilter
    ) {
        Map<Long, List<ProjectKeyResultLink>> linksByKeyResult = linksByKeyResult(
                objectivesByOwner.values().stream().flatMap(Collection::stream).toList()
        );
        Map<Long, StrategyDtos.ExecutionSummaryResponse> summaries = new LinkedHashMap<>();
        for (Long ownerId : ownerIds) {
            summaries.put(ownerId, build(objectivesByOwner.getOrDefault(ownerId, List.of()), periodFilter, linksByKeyResult));
        }
        return summaries;
    }

    private StrategyDtos.ExecutionSummaryResponse build(
            List<Objective> objectives,
            String periodFilter,
            Map<Long, List<ProjectKeyResultLink>> linksByKeyResult
    ) {
        Map<String, ExecutionAccumulator> byPeriod = new LinkedHashMap<>();
        ExecutionAccumulator total = new ExecutionAccumulator();

        for (Objective objective : objectives) {
            Set<String> objectivePeriods = new LinkedHashSet<>();
            for (KeyResult keyResult : objective.getKeyResults()) {
                List<ProjectKeyResultLink> links = linksByKeyResult.getOrDefault(keyResult.getId(), List.of());
                if (links.isEmpty()) {
                    addUnlinkedKeyResult(periodFilter, byPeriod, objectivePeriods, objective, keyResult);
                    continue;
                }
                addLinkedKeyResult(periodFilter, byPeriod, objectivePeriods, objective, keyResult, links);
            }
            for (String period : objectivePeriods) {
                byPeriod.computeIfAbsent(period, ExecutionAccumulator::new).addObjective(objective);
            }
        }

        byPeriod.values().forEach(total::merge);
        List<StrategyDtos.PeriodExecutionSummaryResponse> periodResponses = byPeriod.values()
                .stream()
                .sorted(Comparator.comparing(ExecutionAccumulator::period))
                .map(ExecutionAccumulator::toPeriodResponse)
                .toList();
        return total.toResponse(periodResponses);
    }

    private Map<Long, List<ProjectKeyResultLink>> linksByKeyResult(List<Objective> objectives) {
        List<Long> keyResultIds = objectives.stream()
                .flatMap(objective -> objective.getKeyResults().stream())
                .map(KeyResult::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (keyResultIds.isEmpty()) {
            return Map.of();
        }
        return linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(keyResultIds)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        link -> link.getKeyResult().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
    }

    private void addUnlinkedKeyResult(
            String periodFilter,
            Map<String, ExecutionAccumulator> byPeriod,
            Set<String> objectivePeriods,
            Objective objective,
            KeyResult keyResult
    ) {
        String period = objective.getAcademicPeriod().getName();
        if (periodMatches(period, periodFilter)) {
            objectivePeriods.add(period);
            byPeriod.computeIfAbsent(period, ExecutionAccumulator::new).addKeyResult(keyResult);
        }
    }

    private void addLinkedKeyResult(
            String periodFilter,
            Map<String, ExecutionAccumulator> byPeriod,
            Set<String> objectivePeriods,
            Objective objective,
            KeyResult keyResult,
            List<ProjectKeyResultLink> links
    ) {
        for (ProjectKeyResultLink link : links) {
            Project project = link.getProject();
            String period = periodOf(project, objective);
            if (!periodMatches(period, periodFilter)) {
                continue;
            }
            objectivePeriods.add(period);
            byPeriod.computeIfAbsent(period, ExecutionAccumulator::new).addKeyResult(keyResult);
            byPeriod.get(period).addProject(project);
        }
    }

    private boolean periodMatches(String period, String periodFilter) {
        if (periodFilter == null || periodFilter.isBlank()) {
            return true;
        }
        return overlaps(parsePeriod(period), parsePeriod(periodFilter.trim()));
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

    private String periodOf(Project project, Objective fallbackObjective) {
        if (project == null) {
            return fallbackObjective.getAcademicPeriod().getName();
        }
        if (project.getEndPeriod() != null && !project.getEndPeriod().isBlank()) {
            return project.getEndPeriod();
        }
        if (project.getStartPeriod() != null && !project.getStartPeriod().isBlank()) {
            return project.getStartPeriod();
        }
        return fallbackObjective.getAcademicPeriod().getName();
    }

    private record PeriodRange(int startIndex, int endIndex) {
    }

    private static final class ExecutionAccumulator {
        private final String period;
        private final Set<Long> completedObjectives = new LinkedHashSet<>();
        private final Set<Long> inProgressObjectives = new LinkedHashSet<>();
        private final Set<Long> completedKeyResults = new LinkedHashSet<>();
        private final Set<Long> inProgressKeyResults = new LinkedHashSet<>();
        private final Set<Long> completedProjects = new LinkedHashSet<>();
        private final Set<Long> inProgressProjects = new LinkedHashSet<>();

        private ExecutionAccumulator() {
            this.period = "TOTAL";
        }

        private ExecutionAccumulator(String period) {
            this.period = period;
        }

        private String period() {
            return period;
        }

        private void addObjective(Objective objective) {
            if (objective.completionPercentage().compareTo(BigDecimal.valueOf(100)) >= 0) {
                completedObjectives.add(objective.getId());
                inProgressObjectives.remove(objective.getId());
                return;
            }
            if (!completedObjectives.contains(objective.getId())) {
                inProgressObjectives.add(objective.getId());
            }
        }

        private void addKeyResult(KeyResult keyResult) {
            BigDecimal progress = keyResult.getProgressPercentage() == null ? BigDecimal.ZERO : keyResult.getProgressPercentage();
            if (progress.compareTo(BigDecimal.valueOf(100)) >= 0) {
                completedKeyResults.add(keyResult.getId());
                inProgressKeyResults.remove(keyResult.getId());
                return;
            }
            if (!completedKeyResults.contains(keyResult.getId())) {
                inProgressKeyResults.add(keyResult.getId());
            }
        }

        private void addProject(Project project) {
            if (project == null || project.getId() == null) {
                return;
            }
            if (project.getStatus() == ProjectStatus.FINALIZADO) {
                completedProjects.add(project.getId());
                inProgressProjects.remove(project.getId());
                return;
            }
            if (!completedProjects.contains(project.getId())) {
                inProgressProjects.add(project.getId());
            }
        }

        private void merge(ExecutionAccumulator other) {
            completedObjectives.addAll(other.completedObjectives);
            inProgressObjectives.addAll(other.inProgressObjectives);
            inProgressObjectives.removeAll(completedObjectives);
            completedKeyResults.addAll(other.completedKeyResults);
            inProgressKeyResults.addAll(other.inProgressKeyResults);
            inProgressKeyResults.removeAll(completedKeyResults);
            completedProjects.addAll(other.completedProjects);
            inProgressProjects.addAll(other.inProgressProjects);
            inProgressProjects.removeAll(completedProjects);
        }

        private StrategyDtos.PeriodExecutionSummaryResponse toPeriodResponse() {
            return new StrategyDtos.PeriodExecutionSummaryResponse(
                    period,
                    completedObjectives.size(),
                    inProgressObjectives.size(),
                    completedKeyResults.size(),
                    inProgressKeyResults.size(),
                    completedProjects.size(),
                    inProgressProjects.size()
            );
        }

        private StrategyDtos.ExecutionSummaryResponse toResponse(List<StrategyDtos.PeriodExecutionSummaryResponse> periods) {
            return new StrategyDtos.ExecutionSummaryResponse(
                    "%d objetivos completos, %d en desarrollo. %d KPIs completos, %d en desarrollo. %d proyectos completados, %d en desarrollo."
                            .formatted(
                                    completedObjectives.size(),
                                    inProgressObjectives.size(),
                                    completedKeyResults.size(),
                                    inProgressKeyResults.size(),
                                    completedProjects.size(),
                                    inProgressProjects.size()
                            ),
                    completedObjectives.size(),
                    inProgressObjectives.size(),
                    completedKeyResults.size(),
                    inProgressKeyResults.size(),
                    completedProjects.size(),
                    inProgressProjects.size(),
                    periods
            );
        }
    }
}
