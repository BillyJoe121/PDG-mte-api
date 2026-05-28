package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
class StrategicHierarchyTreeService {
    private final StrategicBetRepository strategicBetRepository;
    private final InstitutionalGoalRepository goalRepository;
    private final ObjectiveRepository objectiveRepository;
    private final ProjectKeyResultLinkRepository linkRepository;
    private final StrategyExecutionSummaryService executionSummaryService;

    StrategicHierarchyTreeService(
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
            ObjectiveRepository objectiveRepository,
            ProjectKeyResultLinkRepository linkRepository,
            StrategyExecutionSummaryService executionSummaryService
    ) {
        this.strategicBetRepository = strategicBetRepository;
        this.goalRepository = goalRepository;
        this.objectiveRepository = objectiveRepository;
        this.linkRepository = linkRepository;
        this.executionSummaryService = executionSummaryService;
    }

    List<StrategyDtos.StrategicHierarchyNodeResponse> tree(String period) {
        List<Objective> objectives = objectiveRepository.findAll();
        List<StrategicBet> bets = strategicBetRepository.findAll();
        List<InstitutionalGoal> goals = goalRepository.findAll();
        Map<Long, StrategyDtos.ExecutionSummaryResponse> betSummaries = executionSummaryService.forBets(
                bets.stream().map(StrategicBet::getId).toList(),
                period
        );
        Map<Long, StrategyDtos.ExecutionSummaryResponse> goalSummaries = executionSummaryService.forGoals(
                goals.stream().map(InstitutionalGoal::getId).toList(),
                period
        );
        Map<Long, List<ProjectKeyResultLink>> linksByKeyResult = linksByKeyResult(objectives);

        List<StrategyDtos.StrategicHierarchyNodeResponse> betRoots = bets
                .stream()
                .sorted(Comparator.comparing(StrategicBet::getName))
                .map(bet -> strategicBetNode(bet, objectives, betSummaries, linksByKeyResult))
                .toList();
        List<StrategyDtos.StrategicHierarchyNodeResponse> goalRoots = goals
                .stream()
                .sorted(Comparator.comparing(InstitutionalGoal::getName))
                .map(goal -> goalNode(goal, objectives, goalSummaries, linksByKeyResult))
                .toList();

        List<StrategyDtos.StrategicHierarchyNodeResponse> roots = new ArrayList<>();
        roots.addAll(betRoots);
        roots.addAll(goalRoots);
        return roots;
    }

    private StrategyDtos.StrategicHierarchyNodeResponse strategicBetNode(
            StrategicBet bet,
            List<Objective> objectives,
            Map<Long, StrategyDtos.ExecutionSummaryResponse> summaries,
            Map<Long, List<ProjectKeyResultLink>> linksByKeyResult
    ) {
        List<StrategyDtos.StrategicHierarchyNodeResponse> children = objectives.stream()
                .filter(objective -> objective.getStrategicBet().getId().equals(bet.getId()))
                .sorted(Comparator.comparing(Objective::getName))
                .map(objective -> objectiveNode(objective, "Meta: " + objective.getGoal().getName(), linksByKeyResult))
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
            List<Objective> objectives,
            Map<Long, StrategyDtos.ExecutionSummaryResponse> summaries,
            Map<Long, List<ProjectKeyResultLink>> linksByKeyResult
    ) {
        List<StrategyDtos.StrategicHierarchyNodeResponse> children = objectives.stream()
                .filter(objective -> objective.getGoal().getId().equals(goal.getId()))
                .sorted(Comparator.comparing(Objective::getName))
                .map(objective -> objectiveNode(objective, "Apuesta: " + objective.getStrategicBet().getName(), linksByKeyResult))
                .toList();
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "GOAL",
                goal.getId().toString(),
                goal.getName(),
                goal.getDescription(),
                null,
                summaries.getOrDefault(goal.getId(), executionSummaryService.empty()),
                goal.getMeasurementUnit().getName(),
                children
        );
    }

    private StrategyDtos.StrategicHierarchyNodeResponse objectiveNode(
            Objective objective,
            String badge,
            Map<Long, List<ProjectKeyResultLink>> linksByKeyResult
    ) {
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "OBJECTIVE",
                objective.getId().toString(),
                objective.getName(),
                objective.getDescription(),
                objective.completionPercentage(),
                null,
                badge,
                objective.getKeyResults()
                        .stream()
                        .sorted(Comparator.comparing(KeyResult::getId))
                        .map(keyResult -> keyResultNode(keyResult, linksByKeyResult))
                        .toList()
        );
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

    private Map<Long, List<ProjectKeyResultLink>> linksByKeyResult(List<Objective> objectives) {
        List<Long> keyResultIds = objectives.stream()
                .map(Objective::getKeyResults)
                .flatMap(Collection::stream)
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
