package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriodRepository;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
class StrategicHierarchyTreeService {
    private final StrategicBetRepository strategicBetRepository;
    private final InstitutionalGoalRepository goalRepository;
    private final KeyResultRepository keyResultRepository;
    private final AcademicPeriodRepository periodRepository;
    private final ProjectKeyResultLinkRepository linkRepository;
    private final StrategyExecutionSummaryService executionSummaryService;

    StrategicHierarchyTreeService(
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
            KeyResultRepository keyResultRepository,
            AcademicPeriodRepository periodRepository,
            ProjectKeyResultLinkRepository linkRepository,
            StrategyExecutionSummaryService executionSummaryService
    ) {
        this.strategicBetRepository = strategicBetRepository;
        this.goalRepository = goalRepository;
        this.keyResultRepository = keyResultRepository;
        this.periodRepository = periodRepository;
        this.linkRepository = linkRepository;
        this.executionSummaryService = executionSummaryService;
    }

    List<StrategyDtos.StrategicHierarchyNodeResponse> tree(String period) {
        AcademicPeriod selectedPeriod = StrategyPeriodScope.resolve(period, periodRepository);
        List<KeyResult> keyResults = keyResultRepository.findAll().stream()
                .filter(keyResult -> StrategyPeriodScope.matches(keyResult, selectedPeriod))
                .toList();
        List<StrategicBet> bets = strategicBetRepository.findAll().stream()
                .filter(bet -> bet.getStatus() == StrategicStatus.ACTIVA)
                .filter(bet -> StrategyPeriodScope.matches(bet, selectedPeriod))
                .toList();
        List<InstitutionalGoal> goals = goalRepository.findAll().stream()
                .filter(goal -> goal.getStatus() == StrategicStatus.ACTIVA)
                .filter(goal -> StrategyPeriodScope.matches(goal, selectedPeriod))
                .toList();
        Map<Long, StrategyDtos.ExecutionSummaryResponse> betSummaries = executionSummaryService.forBets(
                bets.stream().map(StrategicBet::getId).toList(),
                period
        );
        Map<Long, StrategyDtos.ExecutionSummaryResponse> goalSummaries = executionSummaryService.forGoals(
                goals.stream().map(InstitutionalGoal::getId).toList(),
                period
        );
        Map<Long, List<ProjectKeyResultLink>> linksByKeyResult = linksByKeyResult(keyResults);

        List<StrategyDtos.StrategicHierarchyNodeResponse> betRoots = bets
                .stream()
                .sorted(Comparator.comparing(StrategicBet::getName))
                .map(bet -> strategicBetNode(bet, keyResults, betSummaries, linksByKeyResult))
                .toList();
        List<StrategyDtos.StrategicHierarchyNodeResponse> goalRoots = goals
                .stream()
                .sorted(Comparator.comparing(InstitutionalGoal::getName))
                .map(goal -> goalNode(goal, keyResults, goalSummaries, linksByKeyResult))
                .toList();

        List<StrategyDtos.StrategicHierarchyNodeResponse> roots = new ArrayList<>();
        roots.addAll(betRoots);
        roots.addAll(goalRoots);
        return roots;
    }

    private StrategyDtos.StrategicHierarchyNodeResponse strategicBetNode(
            StrategicBet bet,
            List<KeyResult> keyResults,
            Map<Long, StrategyDtos.ExecutionSummaryResponse> summaries,
            Map<Long, List<ProjectKeyResultLink>> linksByKeyResult
    ) {
        List<StrategyDtos.StrategicHierarchyNodeResponse> children = keyResults.stream()
                .filter(keyResult -> keyResult.getStrategicBets().stream().anyMatch(item -> item.getId().equals(bet.getId())))
                .sorted(Comparator.comparing(KeyResult::getId))
                .map(keyResult -> keyResultNode(keyResult, linksByKeyResult))
                .toList();
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "STRATEGIC_BET",
                bet.getId().toString(),
                bet.getName(),
                bet.getDescription(),
                null,
                summaries.getOrDefault(bet.getId(), executionSummaryService.empty()),
                null,
                children
        );
    }

    private StrategyDtos.StrategicHierarchyNodeResponse goalNode(
            InstitutionalGoal goal,
            List<KeyResult> keyResults,
            Map<Long, StrategyDtos.ExecutionSummaryResponse> summaries,
            Map<Long, List<ProjectKeyResultLink>> linksByKeyResult
    ) {
        List<StrategyDtos.StrategicHierarchyNodeResponse> children = keyResults.stream()
                .filter(keyResult -> keyResult.getGoals().stream().anyMatch(item -> item.getId().equals(goal.getId())))
                .sorted(Comparator.comparing(KeyResult::getId))
                .map(keyResult -> keyResultNode(keyResult, linksByKeyResult))
                .toList();
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "GOAL",
                goal.getId().toString(),
                goal.getName(),
                goal.getDescription(),
                null,
                summaries.getOrDefault(goal.getId(), executionSummaryService.empty()),
                goalBadge(goal),
                children
        );
    }

    private String goalBadge(InstitutionalGoal goal) {
        String valueAndUnit = goal.getExpectedValue().stripTrailingZeros().toPlainString()
                + " " + goal.getMeasurementUnit().getName();
        if (goal.getReferenceIndicator() == null || goal.getReferenceIndicator().isBlank()) {
            return valueAndUnit;
        }
        return goal.getReferenceIndicator() + ": " + valueAndUnit;
    }

    private StrategyDtos.StrategicHierarchyNodeResponse keyResultNode(
            KeyResult keyResult,
            Map<Long, List<ProjectKeyResultLink>> linksByKeyResult
    ) {
        List<StrategyDtos.StrategicHierarchyNodeResponse> projectChildren = linksByKeyResult
                .getOrDefault(keyResult.getId(), List.of())
                .stream()
                .map(this::projectNode)
                .toList();
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "KEY_RESULT",
                keyResult.getId().toString(),
                keyResult.getName(),
                keyResult.getDescription(),
                keyResult.getProgressPercentage(),
                null,
                keyResult.getMeasurementUnit().getName(),
                projectChildren
        );
    }

    private StrategyDtos.StrategicHierarchyNodeResponse projectNode(ProjectKeyResultLink link) {
        if (link.getProject() != null) {
            var project = link.getProject();
            return new StrategyDtos.StrategicHierarchyNodeResponse(
                    "PROJECT",
                    project.getId().toString(),
                    project.getName(),
                    project.getDescription(),
                    project.getGlobalProgress() == null ? BigDecimal.ZERO : project.getGlobalProgress(),
                    null,
                    "Peso: " + link.getContributionWeight() + "%",
                    List.of()
            );
        }
        String externalProjectId = link.getExternalProjectId() == null ? "sin-id" : link.getExternalProjectId().toString();
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "PROJECT",
                externalProjectId,
                "Proyecto externo " + externalProjectId,
                link.getExternalProjectSource(),
                null,
                null,
                "Peso: " + link.getContributionWeight() + "%",
                List.of()
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
        return linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(keyResultIds)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        link -> link.getKeyResult().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
    }
}
