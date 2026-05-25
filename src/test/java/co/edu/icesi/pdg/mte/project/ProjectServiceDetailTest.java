package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.strategy.Objective;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceDetailTest extends ProjectServiceTestSupport {
    @Test
    void returnsCompleteProjectDetailWithHistoryLinksContributionChainAndKpis() {
        Project project = localProject();
        project.setStatus(ProjectStatus.FINALIZADO);
        ProjectProgressEntry entry = new ProjectProgressEntry();
        entry.setProject(project);
        entry.setProgressPercent(BigDecimal.valueOf(80));
        entry.setComment("Avance");
        ProjectKeyResultLink link = link(project, keyResult, 60);
        ProjectKeyResultLink overweightLink = link(project, keyResult, 50);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(progressRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(entry));
        when(linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(link, overweightLink));
        when(linkRepository.sumActiveContributionWeightsByKeyResultIds(List.of(1L))).thenReturn(List.of(total(1L, 110)));

        var detail = service.detail(1L);

        assertThat(detail.project().name()).isEqualTo("Proyecto MSP");
        assertThat(detail.history()).hasSize(1);
        assertThat(detail.linkedKeyResults()).hasSize(2);
        assertThat(detail.project().linkedKeyResults()).hasSize(2);
        assertThat(detail.contributionChain().impacts()).hasSize(2);
        assertThat(detail.kpis().progressEntries()).isEqualTo(1);
        assertThat(detail.kpis().linkedKeyResults()).isEqualTo(2);
        assertThat(detail.kpis().declaredContributionWeight()).isEqualByComparingTo("110.00");
        assertThat(detail.kpis().appliedContribution()).isEqualByComparingTo("110.00");
        assertThat(detail.kpis().completed()).isTrue();
        assertThat(detail.kpis().overweightWarning()).isTrue();
    }

    @Test
    void returnsProjectDetailWithZeroAppliedContributionForActiveProjectAndNullExternalLinkProject() {
        Project project = localProject();
        ProjectKeyResultLink linkWithoutLocalProject = link(null, keyResult, 40);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(progressRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(linkWithoutLocalProject));
        when(linkRepository.sumActiveContributionWeightsByKeyResultIds(List.of(1L))).thenReturn(List.of(total(1L, 40)));

        var detail = service.detail(1L);

        assertThat(detail.kpis().declaredContributionWeight()).isEqualByComparingTo("40.00");
        assertThat(detail.kpis().appliedContribution()).isEqualByComparingTo("0.00");
        assertThat(detail.kpis().completed()).isFalse();
        assertThat(detail.kpis().overweightWarning()).isFalse();
        assertThat(detail.linkedKeyResults().get(0).projectId()).isNull();
        assertThat(detail.linkedKeyResults().get(0).overweightWarning()).isFalse();
        assertThat(detail.contributionChain().impacts().get(0).period()).isNull();
        assertThat(detail.contributionChain().impacts().get(0).projectCompleted()).isFalse();
    }

    @Test
    void contributionChainFallsBackToStartPeriodWhenEndPeriodIsBlank() {
        Project project = localProject();
        project.setEndPeriod(" ");
        ProjectKeyResultLink link = link(project, keyResult, 40);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(progressRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(link));

        var detail = service.detail(1L);

        assertThat(detail.contributionChain().impacts().get(0).period()).isEqualTo("2026-1");
    }

    @Test
    void listsProjectsWithNoFiltersAndBlankSearch() {
        when(projectRepository.findPage(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(localProject())));

        assertThat(service.list(null, null, null, null, null)).hasSize(1);
        assertThat(service.list("   ", null, null, null, "   ")).hasSize(1);
    }

    @Test
    void filtersProjectsWhenRequestedPeriodOverlapsProjectPeriodRange() {
        Project longRunning = localProject();
        longRunning.setId(1L);
        longRunning.setStartPeriod("2026-1");
        longRunning.setEndPeriod("2027-1");
        Project later = localProject();
        later.setId(2L);
        later.setStartPeriod("2027-2");
        later.setEndPeriod("2027-2");
        when(projectRepository.findPage(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(longRunning)));

        var filtered = service.list(null, null, null, null, "2026-2");

        assertThat(filtered).extracting(ProjectDtos.ProjectResponse::id).containsExactly(1L);
    }

    @Test
    void rejectsInvalidPeriodFilter() {
        assertThatThrownBy(() -> service.list(null, null, null, null, "2026-X"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("formato");
    }

    @Test
    void listsUnsavedProjectWithoutTryingToLoadLinkedKeyResults() {
        Project unsaved = localProject();
        unsaved.setId(null);
        when(projectRepository.findPage(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(unsaved)));

        var response = service.list(null, null, null, null, null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).linkedKeyResults()).isEmpty();
        verify(linkRepository, never()).findByProjectIdAndActiveTrueOrderByIdAsc(any());
    }

    @Test
    void screenDataAggregatesProjectsCatalogsAndObjectiveCards() {
        Project project = localProject();
        Objective objective = keyResult.getObjective();
        when(projectRepository.findPage(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(project)));
        when(catalogCacheService.listDepartments()).thenReturn(List.of(co.edu.icesi.pdg.mte.api.Mapper.toResponse(department)));
        when(catalogCacheService.listPeriods()).thenReturn(List.of(co.edu.icesi.pdg.mte.api.Mapper.toResponse(objective.getAcademicPeriod())));
        when(objectiveRepository.findReportCandidates(true, 8105, 8106, 1L, null)).thenReturn(List.of(objective));

        var screenData = service.screenData(null, null, null, 1L, "2026-1");

        assertThat(screenData.projects()).hasSize(1);
        assertThat(screenData.departments()).extracting("id").containsExactly(1L);
        assertThat(screenData.academicPeriods()).extracting("name").containsExactly("2026-1");
        assertThat(screenData.objectiveCards()).hasSize(1);
        assertThat(screenData.objectiveCards().get(0).keyResults()).hasSize(1);
    }

    @Test
    void returnsPagedProjectListWithMetadata() {
        when(projectRepository.findPage(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(localProject()), org.springframework.data.domain.PageRequest.of(0, 25), 40));

        var page = service.listPage(null, null, null, null, null, 0, 25);

        assertThat(page.content()).hasSize(1);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(25);
        assertThat(page.totalElements()).isEqualTo(40);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    private co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository.KeyResultWeightTotal total(Long keyResultId, int totalWeight) {
        return new co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository.KeyResultWeightTotal() {
            @Override
            public Long getKeyResultId() {
                return keyResultId;
            }

            @Override
            public BigDecimal getTotalWeight() {
                return BigDecimal.valueOf(totalWeight);
            }
        };
    }
}
