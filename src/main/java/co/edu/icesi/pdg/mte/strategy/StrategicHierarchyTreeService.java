package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        List<StrategyDtos.StrategicHierarchyNodeResponse> betRoots = strategicBetRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(StrategicBet::getName))
                .map(bet -> strategicBetNode(bet, objectives, period))
                .toList();
        List<StrategyDtos.StrategicHierarchyNodeResponse> goalRoots = goalRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(InstitutionalGoal::getName))
                .map(goal -> goalNode(goal, objectives, period))
                .toList();

        List<StrategyDtos.StrategicHierarchyNodeResponse> roots = new ArrayList<>();
        roots.addAll(betRoots);
        roots.addAll(goalRoots);
        return roots;
    }

    private StrategyDtos.StrategicHierarchyNodeResponse strategicBetNode(
            StrategicBet bet,
            List<Objective> objectives,
            String period
    ) {
        List<StrategyDtos.StrategicHierarchyNodeResponse> children = objectives.stream()
                .filter(objective -> objective.getStrategicBet().getId().equals(bet.getId()))
                .sorted(Comparator.comparing(Objective::getName))
                .map(objective -> objectiveNode(objective, "Meta: " + objective.getGoal().getName()))
                .toList();
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "STRATEGIC_BET",
                bet.getId().toString(),
                bet.getName(),
                bet.getDescription(),
                null,
                executionSummaryService.forBet(bet.getId(), period),
                null,
                children
        );
    }

    private StrategyDtos.StrategicHierarchyNodeResponse goalNode(
            InstitutionalGoal goal,
            List<Objective> objectives,
            String period
    ) {
        List<StrategyDtos.StrategicHierarchyNodeResponse> children = objectives.stream()
                .filter(objective -> objective.getGoal().getId().equals(goal.getId()))
                .sorted(Comparator.comparing(Objective::getName))
                .map(objective -> objectiveNode(objective, "Apuesta: " + objective.getStrategicBet().getName()))
                .toList();
        return new StrategyDtos.StrategicHierarchyNodeResponse(
                "GOAL",
                goal.getId().toString(),
                goal.getName(),
                goal.getDescription(),
                null,
                executionSummaryService.forGoal(goal.getId(), period),
                goal.getMeasurementUnit().getName(),
                children
        );
    }

    private StrategyDtos.StrategicHierarchyNodeResponse objectiveNode(Objective objective, String badge) {
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
                        .map(this::keyResultNode)
                        .toList()
        );
    }

    private StrategyDtos.StrategicHierarchyNodeResponse keyResultNode(KeyResult keyResult) {
        List<StrategyDtos.StrategicHierarchyNodeResponse> projectChildren = linkRepository
                .findByKeyResultIdAndActiveTrueOrderByIdAsc(keyResult.getId())
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
                    project.getStatus() == ProjectStatus.FINALIZADO ? BigDecimal.valueOf(100) : BigDecimal.ZERO,
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
}
