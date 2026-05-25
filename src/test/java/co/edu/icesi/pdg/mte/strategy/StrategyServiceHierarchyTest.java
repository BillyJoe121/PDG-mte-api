package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.project.ProjectProgressEntry;
import org.springframework.test.util.ReflectionTestUtils;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.catalog.PeriodStatus;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyServiceHierarchyTest extends StrategyServiceTestSupport {
    @Test
    void objectiveDetailIncludesObjectiveAndCoverageTrend() {
        when(objectiveRepository.findById(1L)).thenReturn(Optional.of(objective));

        var detail = service.getObjectiveDetail(1L);

        assertThat(detail.objective().id()).isEqualTo(1L);
        assertThat(detail.coverageTrend()).extracting(StrategyDtos.CoverageTrendPointResponse::source)
                .containsExactly("OBJECTIVE_CREATED", "CURRENT");
    }

    @Test
    void estimatedCoverageAtReturnsZeroWhenObjectiveHasNoKeyResults() {
        Objective emptyObjective = TestFixtures.objective(9L, unit, period, department, goal, bet);
        emptyObjective.setKeyResults(List.of());
        when(objectiveRepository.findById(9L)).thenReturn(Optional.of(emptyObjective));

        var trend = service.coverageTrend(9L);

        assertThat(trend.get(trend.size() - 1).coveragePercentage()).isEqualByComparingTo("0.00");
    }

    @Test
    void strategicSummaryCountsCompletedObjectivesAndKeyResultsWithBlankPeriodFilter() {
        objective.getKeyResults().get(0).setProgressPercentage(BigDecimal.valueOf(100));
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(objectiveRepository.findAll()).thenReturn(List.of(objective));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        var response = service.getStrategicBet(1L, "   ");

        assertThat(response.executionSummary().completedObjectives()).isEqualTo(1);
        assertThat(response.executionSummary().completedKeyResults()).isEqualTo(1);
        assertThat(response.executionSummary().periods().get(0).period()).isEqualTo("2026-1");
    }

    @Test
    void strategicSummaryHandlesProjectPeriodsNullIdsAndDuplicateProjectStates() {
        KeyResult keyResult = objective.getKeyResults().get(0);
        keyResult.setProgressPercentage(BigDecimal.valueOf(60));
        Project completed = project(1L, ProjectStatus.FINALIZADO);
        completed.setEndPeriod("2026-Q2");
        Project activeSameId = project(1L, ProjectStatus.ACTIVO);
        activeSameId.setEndPeriod("2026-Q2");
        Project activeWithStartFallback = project(2L, ProjectStatus.ACTIVO);
        activeWithStartFallback.setStartPeriod("2026-Q3");
        activeWithStartFallback.setEndPeriod(null);
        Project activeWithObjectiveFallback = project(3L, ProjectStatus.ACTIVO);
        activeWithObjectiveFallback.setStartPeriod(" ");
        activeWithObjectiveFallback.setEndPeriod(" ");
        Project withoutId = project(null, ProjectStatus.ACTIVO);
        withoutId.setEndPeriod("2026-Q2");

        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(objectiveRepository.findAll()).thenReturn(List.of(objective));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(
                link(keyResult, completed, 40),
                link(keyResult, activeSameId, 20),
                link(keyResult, activeWithStartFallback, 20),
                link(keyResult, activeWithObjectiveFallback, 20),
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
        when(objectiveRepository.findAll()).thenReturn(List.of(objective));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(
                link(objective.getKeyResults().get(0), project, 20)
        ));

        var response = service.getGoal(1L, " 2026-Q4 ");

        assertThat(response.executionSummary().periods()).hasSize(1);
        assertThat(response.executionSummary().periods().get(0).period()).isEqualTo("2026-Q4");
    }

    @Test
    void strategicSummaryDoesNotReAddCompletedDuplicateObjectivesOrKeyResultsAsInProgress() {
        Objective completedObjective = TestFixtures.objective(1L, unit, period, department, goal, bet);
        completedObjective.getKeyResults().get(0).setId(1L);
        completedObjective.getKeyResults().get(0).setProgressPercentage(BigDecimal.valueOf(100));
        Objective duplicateObjective = TestFixtures.objective(1L, unit, period, department, goal, bet);
        duplicateObjective.getKeyResults().get(0).setId(1L);
        duplicateObjective.getKeyResults().get(0).setProgressPercentage(null);

        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(objectiveRepository.findAll()).thenReturn(List.of(completedObjective, duplicateObjective));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        var response = service.getStrategicBet(1L, null);

        assertThat(response.executionSummary().completedObjectives()).isEqualTo(1);
        assertThat(response.executionSummary().inProgressObjectives()).isZero();
        assertThat(response.executionSummary().completedKeyResults()).isEqualTo(1);
        assertThat(response.executionSummary().inProgressKeyResults()).isZero();
    }

    @Test
    void listsKeyResultsForExistingObjective() {
        when(objectiveRepository.findById(1L)).thenReturn(Optional.of(objective));
        when(keyResultRepository.findByObjectiveIdOrderByIdAsc(1L)).thenReturn(objective.getKeyResults());

        assertThat(service.listObjectiveKeyResults(1L)).hasSize(1);
    }

    @Test
    void rejectsWhenTargetEntitiesDoNotExist() {
        when(objectiveRepository.findById(99L)).thenReturn(Optional.empty());
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());
        when(keyResultRepository.findById(99L)).thenReturn(Optional.empty());
        when(strategicBetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getObjective(99L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getGoal(99L, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getStrategicBet(99L, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.updateKeyResult(99L, new StrategyDtos.KeyResultRequest(
                "KR",
                "Descripcion",
                "Metric",
                BigDecimal.ZERO,
                BigDecimal.ONE,
                1L
        ))).isInstanceOf(BusinessException.class);
    }

    @Test
    void keyResultProgressIgnoresManualValuesWhenThereAreNoCompletedProjects() {
        KeyResult keyResult = TestFixtures.keyResult(1L, unit);
        keyResult.setBaseValue(BigDecimal.TEN);
        keyResult.setTargetValue(BigDecimal.TEN);
        keyResult.setCurrentValue(BigDecimal.valueOf(20));

        keyResult.recalculateProgress();

        assertThat(keyResult.getProgressPercentage()).isEqualByComparingTo("0");
    }

    @Test
    void keyResultProgressSumsCompletedProjectWeightsOnly() {
        KeyResult keyResult = TestFixtures.keyResult(1L, unit);
        Project completed = project(1L, ProjectStatus.FINALIZADO);
        Project active = project(2L, ProjectStatus.ACTIVO);
        ProjectKeyResultLink completedLink = link(keyResult, completed, 45);
        ProjectKeyResultLink activeLink = link(keyResult, active, 35);

        keyResult.recalculateProgress(List.of(completedLink, activeLink));

        assertThat(keyResult.getProgressPercentage()).isEqualByComparingTo("45.00");
    }

}
