package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(link, overweightLink));

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
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(linkWithoutLocalProject));

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
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(link));

        var detail = service.detail(1L);

        assertThat(detail.contributionChain().impacts().get(0).period()).isEqualTo("2026-1");
    }

    @Test
    void listsProjectsWithNoFiltersAndBlankSearch() {
        when(projectRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(localProject()));

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
        when(projectRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(longRunning, later));

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
        when(projectRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(unsaved));

        var response = service.list(null, null, null, null, null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).linkedKeyResults()).isEmpty();
        verify(linkRepository, never()).findByProjectIdAndActiveTrueOrderByIdAsc(any());
    }
}
