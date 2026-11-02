package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
class StrategyExecutionSummaryService {
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(Q[1-4]|[1-2])$");
    private static final String SUMMARY_TEMPLATE =
            "%d KRs completos, %d en desarrollo. %d proyectos completados, %d en desarrollo.";

    private final KeyResultRepository keyResultRepository;
    private final ProjectKeyResultLinkRepository linkRepository;

    StrategyExecutionSummaryService(
            KeyResultRepository keyResultRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        this.keyResultRepository = keyResultRepository;
        this.linkRepository = linkRepository;
    }

    StrategyDtos.ExecutionSummaryResponse forBet(Long betId, String periodFilter) {
        return forBets(List.of(betId), periodFilter).getOrDefault(betId, empty());
    }

    StrategyDtos.ExecutionSummaryResponse forGoal(Long goalId, String periodFilter) {
        return forGoals(List.of(goalId), periodFilter).getOrDefault(goalId, empty());
    }

    Map<Long, StrategyDtos.ExecutionSummaryResponse> forBets(Collection<Long> betIds, String periodFilter) {
        Map<Long, List<KeyResult>> keyResultsByBet = groupByOwner(betIds, keyResult -> keyResult.getStrategicBets()
                .stream()
                .map(StrategicBet::getId)
                .toList());
        return buildAll(betIds, keyResultsByBet, periodFilter);
    }

    Map<Long, StrategyDtos.ExecutionSummaryResponse> forGoals(Collection<Long> goalIds, String periodFilter) {
        Map<Long, List<KeyResult>> keyResultsByGoal = groupByOwner(goalIds, keyResult -> keyResult.getGoals()
                .stream()
                .map(InstitutionalGoal::getId)
                .toList());
        return buildAll(goalIds, keyResultsByGoal, periodFilter);
    }

    StrategyDtos.ExecutionSummaryResponse empty() {
        return new StrategyDtos.ExecutionSummaryResponse(
                SUMMARY_TEMPLATE.formatted(0, 0, 0, 0),
                0,
                0,
                0,
                0,
                List.of()
        );
    }

    private Map<Long, List<KeyResult>> groupByOwner(
            Collection<Long> ownerIds,
            Function<KeyResult, List<Long>> ownersOf
    ) {
        Map<Long, List<KeyResult>> keyResultsByOwner = new LinkedHashMap<>();
        for (KeyResult keyResult : keyResultRepository.findAll()) {
            for (Long ownerId : ownersOf.apply(keyResult)) {
                if (ownerIds.contains(ownerId)) {
                    keyResultsByOwner.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(keyResult);
                }
            }
        }
        return keyResultsByOwner;
    }

    private Map<Long, StrategyDtos.ExecutionSummaryResponse> buildAll(
            Collection<Long> ownerIds,
            Map<Long, List<KeyResult>> keyResultsByOwner,
            String periodFilter
    ) {
        Map<Long, List<ProjectKeyResultLink>> linksByKeyResult = linksByKeyResult(
                keyResultsByOwner.values().stream().flatMap(Collection::stream).toList()
        );
        Map<Long, StrategyDtos.ExecutionSummaryResponse> summaries = new LinkedHashMap<>();
        for (Long ownerId : ownerIds) {
            summaries.put(ownerId, build(keyResultsByOwner.getOrDefault(ownerId, List.of()), periodFilter, linksByKeyResult));
        }
        return summaries;
    }

    private StrategyDtos.ExecutionSummaryResponse build(
            List<KeyResult> keyResults,
            String periodFilter,
            Map<Long, List<ProjectKeyResultLink>> linksByKeyResult
    ) {
        Map<String, ExecutionAccumulator> byPeriod = new LinkedHashMap<>();
        ExecutionAccumulator total = new ExecutionAccumulator();

        for (KeyResult keyResult : keyResults) {
            List<ProjectKeyResultLink> links = linksByKeyResult.getOrDefault(keyResult.getId(), List.of());
            if (links.isEmpty()) {
                addUnlinkedKeyResult(periodFilter, byPeriod, keyResult);
                continue;
            }
            addLinkedKeyResult(periodFilter, byPeriod, keyResult, links);
        }

        byPeriod.values().forEach(total::merge);
        List<StrategyDtos.PeriodExecutionSummaryResponse> periodResponses = byPeriod.values()
                .stream()
                .sorted(Comparator.comparing(ExecutionAccumulator::period))
                .map(ExecutionAccumulator::toPeriodResponse)
                .toList();
        return total.toResponse(periodResponses);
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
            KeyResult keyResult
    ) {
        String period = periodOf(null, keyResult);
        if (periodMatches(period, periodFilter)) {
            byPeriod.computeIfAbsent(period, ExecutionAccumulator::new).addKeyResult(keyResult);
        }
    }

    private void addLinkedKeyResult(
            String periodFilter,
            Map<String, ExecutionAccumulator> byPeriod,
            KeyResult keyResult,
            List<ProjectKeyResultLink> links
    ) {
        for (ProjectKeyResultLink link : links) {
            Project project = link.getProject();
            String period = periodOf(project, keyResult);
            if (!periodMatches(period, periodFilter)) {
                continue;
            }
            byPeriod.computeIfAbsent(period, ExecutionAccumulator::new).addKeyResult(keyResult);
            byPeriod.get(period).addProject(project);
        }
    }

    private boolean periodMatches(String period, String periodFilter) {
        if (period == null) {
            // KR heredado sin periodo académico ni proyecto con periodo: no se puede ubicar en el tiempo.
            return false;
        }
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

    private String periodOf(Project project, KeyResult fallbackKeyResult) {
        if (project != null && project.getEndPeriod() != null && !project.getEndPeriod().isBlank()) {
            return project.getEndPeriod();
        }
        if (project != null && project.getStartPeriod() != null && !project.getStartPeriod().isBlank()) {
            return project.getStartPeriod();
        }
        return fallbackKeyResult.getAcademicPeriod() == null ? null : fallbackKeyResult.getAcademicPeriod().getName();
    }

    private record PeriodRange(int startIndex, int endIndex) {
    }

    private static final class ExecutionAccumulator {
        private final String period;
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

        private void addKeyResult(KeyResult keyResult) {
            if (keyResult.getStatus() == KeyResultStatus.CERRADO) {
                completedKeyResults.add(keyResult.getId());
                inProgressKeyResults.remove(keyResult.getId());
                return;
            }
            // Solo un KR ACTIVO cuenta como en desarrollo; un BORRADOR no cuenta en ninguna categoria.
            if (keyResult.getStatus() == KeyResultStatus.ACTIVO && !completedKeyResults.contains(keyResult.getId())) {
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
                    completedKeyResults.size(),
                    inProgressKeyResults.size(),
                    completedProjects.size(),
                    inProgressProjects.size()
            );
        }

        private StrategyDtos.ExecutionSummaryResponse toResponse(List<StrategyDtos.PeriodExecutionSummaryResponse> periods) {
            return new StrategyDtos.ExecutionSummaryResponse(
                    SUMMARY_TEMPLATE.formatted(
                            completedKeyResults.size(),
                            inProgressKeyResults.size(),
                            completedProjects.size(),
                            inProgressProjects.size()
                    ),
                    completedKeyResults.size(),
                    inProgressKeyResults.size(),
                    completedProjects.size(),
                    inProgressProjects.size(),
                    periods
            );
        }
    }
}
