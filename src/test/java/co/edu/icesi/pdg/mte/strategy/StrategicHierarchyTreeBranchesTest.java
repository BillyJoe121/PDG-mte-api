package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategicHierarchyTreeBranchesTest extends StrategyServiceTestSupport {

    @Test
    void inactiveBetsAndGoalsAreLeftOutOfTheTree() {
        StrategicBet archivedBet = TestFixtures.strategicBet(2L);
        archivedBet.setStatus(StrategicStatus.INACTIVA);
        InstitutionalGoal archivedGoal = TestFixtures.goal(2L, unit);
        archivedGoal.setStatus(StrategicStatus.INACTIVA);
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet, archivedBet));
        when(goalRepository.findAll()).thenReturn(List.of(goal, archivedGoal));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        var tree = service.getStrategicHierarchyTree(null);

        assertThat(tree).extracting(StrategyDtos.StrategicHierarchyNodeResponse::nodeType)
                .containsExactly("STRATEGIC_BET", "GOAL");
        assertThat(tree).extracting(StrategyDtos.StrategicHierarchyNodeResponse::id).containsExactly("1", "1");
    }

    @Test
    void goalBadgeOmitsABlankReferenceIndicator() {
        goal.setReferenceIndicator("   ");
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(strategicBetRepository.findAll()).thenReturn(List.of());
        when(goalRepository.findAll()).thenReturn(List.of(goal));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        var tree = service.getStrategicHierarchyTree(null);

        assertThat(tree).singleElement().satisfies(node -> assertThat(node.badge()).isEqualTo("80 Porcentaje"));
    }

    @Test
    void linkedLocalProjectsShowTheirProgressOrZero() {
        Project started = project(10L, ProjectStatus.ACTIVO);
        started.setGlobalProgress(BigDecimal.valueOf(45));
        Project notStarted = project(11L, ProjectStatus.ACTIVO);
        notStarted.setGlobalProgress(null);
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet));
        when(goalRepository.findAll()).thenReturn(List.of());
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any()))
                .thenReturn(List.of(link(keyResult, started), link(keyResult, notStarted)));

        var projects = service.getStrategicHierarchyTree(null).get(0).children().get(0).children();

        assertThat(projects).extracting(StrategyDtos.StrategicHierarchyNodeResponse::id).containsExactly("10", "11");
        assertThat(projects.get(0).progressPercentage()).isEqualByComparingTo("45");
        assertThat(projects.get(1).progressPercentage()).isEqualByComparingTo("0");
        assertThat(projects.get(0).badge()).isNull();
    }

    @Test
    void externalLinksWithoutAnIdentifierUseAPlaceholder() {
        ProjectKeyResultLink external = new ProjectKeyResultLink();
        external.setKeyResult(keyResult);
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet));
        when(goalRepository.findAll()).thenReturn(List.of());
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(external));

        var projects = service.getStrategicHierarchyTree(null).get(0).children().get(0).children();

        assertThat(projects).singleElement().satisfies(node -> {
            assertThat(node.id()).isEqualTo("sin-id");
            assertThat(node.label()).isEqualTo("Proyecto externo sin-id");
        });
    }

    @Test
    void withoutKeyResultsNoLinksAreQueried() {
        when(keyResultRepository.findAll()).thenReturn(List.of());
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet));
        when(goalRepository.findAll()).thenReturn(List.of());

        var tree = service.getStrategicHierarchyTree(null);

        assertThat(tree).singleElement().satisfies(node -> assertThat(node.children()).isEmpty());
        verifyNoInteractions(linkRepository);
    }
}
