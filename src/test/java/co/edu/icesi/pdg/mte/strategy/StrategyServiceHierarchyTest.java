package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyServiceHierarchyTest extends StrategyServiceTestSupport {

    @Test
    void strategicSummaryCountsCompletedKeyResultsWithBlankPeriodFilter() {
        keyResult.setStatus(KeyResultStatus.CERRADO);
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        var response = service.getStrategicBet(1L, "   ");

        assertThat(response.executionSummary().completedKeyResults()).isEqualTo(1);
        assertThat(response.executionSummary().inProgressKeyResults()).isZero();
        assertThat(response.executionSummary().periods().get(0).period()).isEqualTo("2026-1");
        assertThat(response.executionSummary().summaryText()).startsWith("1 KRs completos");
    }

    @Test
    void strategicSummaryOnlyCountsKeyResultsLinkedToTheRequestedOwner() {
        StrategicBet otherBet = TestFixtures.strategicBet(2L);
        KeyResult goalOnly = TestFixtures.keyResult(2L, unit, period, goal, null);
        KeyResult otherBetOnly = TestFixtures.keyResult(3L, unit, period, null, otherBet);
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult, goalOnly, otherBetOnly));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        assertThat(service.getStrategicBet(1L, null).executionSummary().inProgressKeyResults()).isEqualTo(1);
        assertThat(service.getGoal(1L, null).executionSummary().inProgressKeyResults()).isEqualTo(2);
    }

    @Test
    void strategicSummaryHandlesProjectPeriodsNullIdsAndDuplicateProjectStates() {
        keyResult.setProgressPercentage(BigDecimal.valueOf(60));
        Project completed = project(1L, ProjectStatus.FINALIZADO);
        completed.setEndPeriod("2026-Q2");
        Project activeSameId = project(1L, ProjectStatus.ACTIVO);
        activeSameId.setEndPeriod("2026-Q2");
        Project activeWithStartFallback = project(2L, ProjectStatus.ACTIVO);
        activeWithStartFallback.setStartPeriod("2026-Q3");
        activeWithStartFallback.setEndPeriod(null);
        Project activeWithKeyResultFallback = project(3L, ProjectStatus.ACTIVO);
        activeWithKeyResultFallback.setStartPeriod(" ");
        activeWithKeyResultFallback.setEndPeriod(" ");
        Project withoutId = project(null, ProjectStatus.ACTIVO);
        withoutId.setEndPeriod("2026-Q2");

        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(
                link(keyResult, completed, 40),
                link(keyResult, activeSameId, 20),
                link(keyResult, activeWithStartFallback, 20),
                link(keyResult, activeWithKeyResultFallback, 20),
                link(keyResult, withoutId, 20)
        ));

        var response = service.getGoal(1L, null);

        assertThat(response.executionSummary().completedProjects()).isEqualTo(1);
        assertThat(response.executionSummary().inProgressProjects()).isEqualTo(2);
        assertThat(response.executionSummary().periods().stream().map(StrategyDtos.PeriodExecutionSummaryResponse::period))
                .contains("2026-Q2", "2026-Q3", "2026-1");
    }

    @Test
    void strategicSummaryHonorsTrimmedPeriodFilter() {
        Project project = project(2L, ProjectStatus.ACTIVO);
        project.setEndPeriod("2026-Q4");
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(
                link(keyResult, project, 20)
        ));

        var response = service.getGoal(1L, " 2026-Q4 ");

        assertThat(response.executionSummary().periods()).hasSize(1);
        assertThat(response.executionSummary().periods().get(0).period()).isEqualTo("2026-Q4");
    }

    @Test
    void strategicSummarySkipsUnlinkedKeyResultsWithoutPeriod() {
        keyResult.setAcademicPeriod(null);
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        var summary = service.getStrategicBet(1L, "2026-1").executionSummary();

        assertThat(summary.inProgressKeyResults()).isZero();
        assertThat(summary.periods()).isEmpty();
    }

    @Test
    void strategicSummaryDoesNotReAddCompletedKeyResultsAsInProgress() {
        Project completedProject = project(1L, ProjectStatus.FINALIZADO);
        completedProject.setEndPeriod("2026-Q1");
        Project activeProject = project(2L, ProjectStatus.ACTIVO);
        activeProject.setEndPeriod("2026-Q2");
        keyResult.setStatus(KeyResultStatus.CERRADO);

        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(
                link(keyResult, completedProject, 50),
                link(keyResult, activeProject, 50)
        ));

        var response = service.getStrategicBet(1L, null);

        assertThat(response.executionSummary().completedKeyResults()).isEqualTo(1);
        assertThat(response.executionSummary().inProgressKeyResults()).isZero();
        assertThat(response.executionSummary().periods()).hasSize(2);
    }

    @Test
    void strategicSummaryCountsOnlyActiveKeyResultsAsInProgress() {
        KeyResult draft = TestFixtures.keyResult(2L, unit, period, null, bet);
        draft.setStatus(KeyResultStatus.BORRADOR);
        KeyResult closed = TestFixtures.keyResult(3L, unit, period, null, bet);
        closed.setStatus(KeyResultStatus.CERRADO);
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult, draft, closed));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        var summary = service.getStrategicBet(1L, null).executionSummary();

        assertThat(summary.inProgressKeyResults()).isEqualTo(1);
        assertThat(summary.completedKeyResults()).isEqualTo(1);
        assertThat(summary.summaryText()).startsWith("1 KRs completos, 1 en desarrollo.");
    }

    @Test
    void portfolioSummaryCountsAKeyResultLinkedToTwoBetsOnce() {
        keyResult.getStrategicBets().add(TestFixtures.strategicBet(2L));
        Project sharedProject = project(1L, ProjectStatus.ACTIVO);
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(
                link(keyResult, sharedProject, 100)
        ));

        var portfolio = service.portfolioExecutionSummary(null);

        assertThat(portfolio.inProgressKeyResults()).isEqualTo(1);
        assertThat(portfolio.inProgressProjects()).isEqualTo(1);
        assertThat(portfolio.completedKeyResults()).isZero();
        assertThat(portfolio.completedProjects()).isZero();
    }

    @Test
    void portfolioSummaryIncludesKeyResultsLinkedOnlyToAGoal() {
        KeyResult goalOnly = TestFixtures.keyResult(2L, unit, period, goal, null);
        goalOnly.setStatus(KeyResultStatus.CERRADO);
        when(keyResultRepository.findAll()).thenReturn(List.of(goalOnly));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        var portfolio = service.portfolioExecutionSummary("2026-1");

        assertThat(portfolio.completedKeyResults()).isEqualTo(1);
        assertThat(portfolio.inProgressKeyResults()).isZero();
    }

    @Test
    void portfolioSummaryExcludesArchivedKeyResults() {
        KeyResult archived = TestFixtures.keyResult(2L, unit, period, goal, null);
        archived.setArchived(true);
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult, archived));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        var portfolio = service.portfolioExecutionSummary(null);

        assertThat(portfolio.inProgressKeyResults()).isEqualTo(1);
        assertThat(portfolio.completedKeyResults()).isZero();
    }

    @Test
    void returnsEmptyStrategicBetSummaryWhenThereAreNoKeyResults() {
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(keyResultRepository.findAll()).thenReturn(List.of());

        assertThat(service.getStrategicBet(1L, null).executionSummary().summaryText())
                .isEqualTo("0 KRs completos, 0 en desarrollo. 0 proyectos completados, 0 en desarrollo.");
    }

    @Test
    void buildsStrategicHierarchyTreeWithKeyResultsUnderBetsAndGoals() {
        goal.setReferenceIndicator("Tasa de permanencia");
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResult);
        link.setExternalProjectId(77L);
        link.setContributionWeight(BigDecimal.valueOf(35));

        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet));
        when(goalRepository.findAll()).thenReturn(List.of(goal));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(link));

        var tree = service.getStrategicHierarchyTree(null);

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).nodeType()).isEqualTo("STRATEGIC_BET");
        assertThat(tree.get(0).executionSummary().inProgressKeyResults()).isEqualTo(1);
        assertThat(tree.get(0).children().get(0).nodeType()).isEqualTo("KEY_RESULT");
        assertThat(tree.get(0).children().get(0).children().get(0).nodeType()).isEqualTo("PROJECT");
        assertThat(tree.get(1).nodeType()).isEqualTo("GOAL");
        assertThat(tree.get(1).badge()).isEqualTo("Tasa de permanencia: 80 Porcentaje");
        assertThat(tree.get(1).children().get(0).nodeType()).isEqualTo("KEY_RESULT");
        assertThat(tree.get(1).children().get(0).id()).isEqualTo("1");
        assertThat(tree).flatExtracting(StrategyDtos.StrategicHierarchyNodeResponse::children)
                .extracting(StrategyDtos.StrategicHierarchyNodeResponse::nodeType)
                .doesNotContain("OBJECTIVE");
    }

    @Test
    void hierarchyTreeOnlyPlacesKeyResultsUnderTheirLinkedOwners() {
        KeyResult goalOnly = TestFixtures.keyResult(2L, unit, period, goal, null);
        when(keyResultRepository.findAll()).thenReturn(List.of(goalOnly));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet));
        when(goalRepository.findAll()).thenReturn(List.of(goal));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        var tree = service.getStrategicHierarchyTree(null);

        assertThat(tree.get(0).children()).isEmpty();
        assertThat(tree.get(1).children()).extracting(StrategyDtos.StrategicHierarchyNodeResponse::id).containsExactly("2");
    }

    @Test
    void filtersStrategicHierarchyRootsAndKeyResultsByPeriod() {
        StrategicBet futureBet = TestFixtures.strategicBet(2L);
        futureBet.setStartDate(LocalDate.of(2027, 1, 1));
        futureBet.setEndDate(LocalDate.of(2027, 12, 31));
        InstitutionalGoal futureGoal = TestFixtures.goal(2L, unit);
        futureGoal.setStartDate(LocalDate.of(2027, 1, 1));
        futureGoal.setEndDate(LocalDate.of(2027, 12, 31));
        var otherPeriod = TestFixtures.period(2L);
        otherPeriod.setName("2026-2");
        KeyResult otherPeriodKeyResult = TestFixtures.keyResult(2L, unit, otherPeriod, goal, bet);

        when(periodRepository.findByNameIgnoreCase("2026-1")).thenReturn(Optional.of(period));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult, otherPeriodKeyResult));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet, futureBet));
        when(goalRepository.findAll()).thenReturn(List.of(goal, futureGoal));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        var tree = service.getStrategicHierarchyTree("2026-1");

        assertThat(tree).extracting(StrategyDtos.StrategicHierarchyNodeResponse::id)
                .containsExactly("1", "1");
        assertThat(tree.get(0).children()).extracting(StrategyDtos.StrategicHierarchyNodeResponse::id).containsExactly("1");
    }

    @Test
    void hierarchyTreeLabelsExternalProjectWithoutIdAsSinId() {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResult);
        link.setExternalProjectId(null);
        link.setContributionWeight(BigDecimal.valueOf(35));

        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet));
        when(goalRepository.findAll()).thenReturn(List.of(goal));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(link));

        var tree = service.getStrategicHierarchyTree(null);

        var projectNode = tree.get(0).children().get(0).children().get(0);
        assertThat(projectNode.id()).isEqualTo("sin-id");
        assertThat(projectNode.label()).isEqualTo("Proyecto externo sin-id");
    }

    @Test
    void rejectsWhenTargetEntitiesDoNotExist() {
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());
        when(strategicBetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGoal(99L, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getStrategicBet(99L, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void strategicSummaryCountsOnlyClosedKeyResultsAsCompletedEvenWithFullRecordedProgress() {
        keyResult.setStatus(KeyResultStatus.ACTIVO);
        keyResult.setProgressPercentage(BigDecimal.valueOf(100));
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        var summary = service.getStrategicBet(1L, null).executionSummary();

        assertThat(summary.completedKeyResults()).isZero();
        assertThat(summary.inProgressKeyResults()).isEqualTo(1);
    }

    @Test
    void keyResultKeepsRecordedValuesWithoutDerivingOneFromTheOther() {
        KeyResult metricKeyResult = TestFixtures.keyResult(1L, unit);
        metricKeyResult.setBaseValue(BigDecimal.TEN);
        metricKeyResult.setTargetValue(BigDecimal.valueOf(30));
        metricKeyResult.setCurrentValue(BigDecimal.valueOf(20));
        metricKeyResult.setProgressPercentage(BigDecimal.valueOf(7));

        assertThat(metricKeyResult.getCurrentValue()).isEqualByComparingTo("20");
        assertThat(metricKeyResult.getProgressPercentage()).isEqualByComparingTo("7");
    }
}
