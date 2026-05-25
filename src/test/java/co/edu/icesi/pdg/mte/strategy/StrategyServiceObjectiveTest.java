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
class StrategyServiceObjectiveTest extends StrategyServiceTestSupport {
    @Test
    void updateKeyResultDoesNotUseLegacyManualCurrentValue() {
        KeyResult keyResult = TestFixtures.keyResult(1L, unit);
        keyResult.setCurrentValue(BigDecimal.valueOf(75));
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(keyResultRepository.save(any(KeyResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateKeyResult(1L, new StrategyDtos.KeyResultRequest(
                "KR actualizado",
                "KR actualizado",
                "Metric",
                BigDecimal.ZERO,
                BigDecimal.valueOf(100),
                1L
        ));

        assertThat(response.progressPercentage()).isEqualByComparingTo("0.00");
        assertThat(response.currentValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void deletesKeyResultWhenItHasNoActiveProjectLinks() {
        KeyResult keyResult = TestFixtures.keyResult(1L, unit);
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(linkRepository.countByKeyResultIdAndActiveTrue(1L)).thenReturn(0L);

        service.deleteKeyResult(1L);

        verify(keyResultRepository).delete(keyResult);
    }

    @Test
    void rejectsKeyResultDeletionWhenItHasActiveProjectLinks() {
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(TestFixtures.keyResult(1L, unit)));
        when(linkRepository.countByKeyResultIdAndActiveTrue(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteKeyResult(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("proyectos vinculados");
    }

    @Test
    void returnsObjectiveAndStrategicBetDetail() {
        when(objectiveRepository.findById(1L)).thenReturn(Optional.of(objective));
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(objectiveRepository.findAll()).thenReturn(List.of(objective));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        assertThat(service.getObjective(1L).id()).isEqualTo(1L);
        assertThat(service.getStrategicBet(1L, null).executionSummary().inProgressObjectives()).isEqualTo(1);
        assertThat(service.getStrategicBet(1L, null).executionSummary().inProgressKeyResults()).isEqualTo(1);
    }

    @Test
    void returnsEmptyStrategicBetSummaryWhenThereAreNoObjectives() {
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(objectiveRepository.findAll()).thenReturn(List.of());

        assertThat(service.getStrategicBet(1L, null).executionSummary().summaryText()).contains("0 objetivos completos");
    }

    @Test
    void listsObjectivesWithAndWithoutFilters() {
        when(objectiveRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(objective));

        assertThat(service.listObjectives(null, null, null, null)).hasSize(1);
        assertThat(service.listObjectives(1L, 1L, 1L, 1L)).hasSize(1);
        var cards = service.listObjectiveCards(1L, 1L, 1L, 1L);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).lowCompletionAlert()).isTrue();
    }

    @Test
    void objectiveCardsDoNotRaiseLowCompletionAlertAtThirtyPercentOrMore() {
        objective.getKeyResults().get(0).setProgressPercentage(BigDecimal.valueOf(30));
        when(objectiveRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(objective));

        var cards = service.listObjectiveCards(null, null, null, null);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).lowCompletionAlert()).isFalse();
    }

    @Test
    void objectiveScreenDataAggregatesCardsAndCatalogs() {
        when(objectiveRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(objective));
        StrategyDtos.ExecutionSummaryResponse emptySummary = new StrategyDtos.ExecutionSummaryResponse("", 0, 0, 0, 0, 0, 0, List.of());
        when(catalogCacheService.strategicBetCatalog()).thenReturn(List.of(co.edu.icesi.pdg.mte.api.Mapper.toResponse(bet, emptySummary)));
        when(catalogCacheService.goalCatalog()).thenReturn(List.of(co.edu.icesi.pdg.mte.api.Mapper.toResponse(goal, emptySummary)));
        when(catalogCacheService.listPeriods()).thenReturn(List.of(co.edu.icesi.pdg.mte.api.Mapper.toResponse(period)));
        when(catalogCacheService.listUnits()).thenReturn(List.of(co.edu.icesi.pdg.mte.api.Mapper.toResponse(unit)));
        when(catalogCacheService.listDepartments()).thenReturn(List.of(co.edu.icesi.pdg.mte.api.Mapper.toResponse(department)));

        var screenData = service.objectivesScreenData(1L, 1L, 1L, 1L);

        assertThat(screenData.objectiveCards()).hasSize(1);
        assertThat(screenData.strategicBets()).extracting("id").containsExactly(1L);
        assertThat(screenData.goals()).extracting("id").containsExactly(1L);
        assertThat(screenData.academicPeriods()).extracting("name").containsExactly("2026-1");
        assertThat(screenData.measurementUnits()).extracting("name").containsExactly("Porcentaje");
        assertThat(screenData.departments()).extracting("id").containsExactly(1L);
    }

    @Test
    void createsObjectiveWithoutSecurityPrincipal() {
        SecurityContextHolder.clearContext();
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(objectiveRepository.save(any(Objective.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createObjective(objectiveRequest(1L, 1L, 1L, 1L, 1L));

        assertThat(response.keyResults()).hasSize(1);
        verify(objectiveRepository).save(argThat(saved -> saved.getCreatedByExternalUserId() == null));
    }

    @Test
    void createsObjectiveWithoutExternalUserPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario-local",
                null,
                List.of()
        ));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(objectiveRepository.save(any(Objective.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createObjective(objectiveRequest(1L, 1L, 1L, 1L, 1L));

        verify(objectiveRepository).save(argThat(saved -> saved.getCreatedByExternalUserId() == null));
    }

    @Test
    void updatesObjectiveBasicFields() {
        when(objectiveRepository.findById(1L)).thenReturn(Optional.of(objective));
        when(objectiveRepository.save(any(Objective.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateObjective(1L, new StrategyDtos.ObjectiveUpdateRequest(
                "Objetivo actualizado",
                "Descripcion actualizada"
        ));

        assertThat(response.name()).isEqualTo("Objetivo actualizado");
        assertThat(response.description()).isEqualTo("Descripcion actualizada");
    }

    @Test
    void buildsStrategicHierarchyTreeWithProjectLinks() {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(objective.getKeyResults().get(0));
        link.setExternalProjectId(77L);
        link.setContributionWeight(BigDecimal.valueOf(35));

        when(objectiveRepository.findAll()).thenReturn(List.of(objective));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet));
        when(goalRepository.findAll()).thenReturn(List.of(goal));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(link));

        var tree = service.getStrategicHierarchyTree(null);

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).nodeType()).isEqualTo("STRATEGIC_BET");
        assertThat(tree.get(0).executionSummary().inProgressObjectives()).isEqualTo(1);
        assertThat(tree.get(0).children().get(0).badge()).contains("Meta:");
        assertThat(tree.get(0).children().get(0).children().get(0).children().get(0).nodeType()).isEqualTo("PROJECT");
        assertThat(tree.get(1).nodeType()).isEqualTo("GOAL");
        assertThat(tree.get(1).children().get(0).badge()).contains("Apuesta:");
    }

    @Test
    void hierarchyTreeLabelsExternalProjectWithoutIdAsSinId() {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(objective.getKeyResults().get(0));
        link.setExternalProjectId(null);
        link.setContributionWeight(BigDecimal.valueOf(35));

        when(objectiveRepository.findAll()).thenReturn(List.of(objective));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet));
        when(goalRepository.findAll()).thenReturn(List.of(goal));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(link));

        var tree = service.getStrategicHierarchyTree(null);

        var projectNode = tree.get(0).children().get(0).children().get(0).children().get(0);
        assertThat(projectNode.id()).isEqualTo("sin-id");
        assertThat(projectNode.label()).isEqualTo("Proyecto externo sin-id");
    }

    @Test
    void coverageTrendIgnoresInvalidLinksAndUsesLatestProgressAtEachEntry() {
        Project project = project(10L, ProjectStatus.ACTIVO);
        Project otherProject = project(99L, ProjectStatus.ACTIVO);
        Project withoutId = project(null, ProjectStatus.ACTIVO);
        ProjectKeyResultLink validLink = link(objective.getKeyResults().get(0), project, 50);
        ProjectKeyResultLink nullProjectLink = link(objective.getKeyResults().get(0), null, 40);
        ProjectKeyResultLink withoutIdLink = link(objective.getKeyResults().get(0), withoutId, 40);
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = Instant.parse("2026-01-02T00:00:00Z");
        ProjectProgressEntry firstEntry = progressEntry(project, BigDecimal.valueOf(20), first);
        ProjectProgressEntry secondEntry = progressEntry(project, BigDecimal.valueOf(80), second);
        ProjectProgressEntry ignoredEntry = progressEntry(otherProject, BigDecimal.valueOf(100), first);

        when(objectiveRepository.findById(1L)).thenReturn(Optional.of(objective));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(List.of(1L))).thenReturn(List.of(
                nullProjectLink,
                withoutIdLink,
                validLink
        ));
        when(progressRepository.findByProjectIdsOrderByCreatedAtAsc(anyCollection())).thenReturn(List.of(firstEntry, secondEntry));

        var trend = service.coverageTrend(1L);

        assertThat(trend).hasSize(4);
        assertThat(trend.get(0).source()).isEqualTo("OBJECTIVE_CREATED");
        assertThat(trend.get(1).source()).isEqualTo("PROJECT_PROGRESS");
        assertThat(trend.get(1).coveragePercentage()).isEqualByComparingTo("10.00");
        assertThat(trend.get(2).coveragePercentage()).isEqualByComparingTo("40.00");
        assertThat(trend.get(3).source()).isEqualTo("CURRENT");
    }

}
