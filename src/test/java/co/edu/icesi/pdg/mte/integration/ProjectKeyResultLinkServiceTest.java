package co.edu.icesi.pdg.mte.integration;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnit;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultProgressService;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import co.edu.icesi.pdg.mte.strategy.Objective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectKeyResultLinkServiceTest {

    @Mock
    private ProjectKeyResultLinkRepository linkRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private KeyResultRepository keyResultRepository;
    @Mock
    private KeyResultProgressService progressService;
    @Mock
    private AuditService auditService;

    private ProjectKeyResultLinkService service;
    private Project project;
    private KeyResult keyResult;

    @BeforeEach
    void setUp() {
        service = new ProjectKeyResultLinkService(linkRepository, projectRepository, keyResultRepository, progressService, auditService);
        MeasurementUnit unit = TestFixtures.unit(1L);
        Department department = TestFixtures.department(1L);
        Objective objective = TestFixtures.objective(
                1L,
                unit,
                TestFixtures.period(1L),
                department,
                TestFixtures.goal(1L, unit),
                TestFixtures.strategicBet(1L)
        );
        keyResult = objective.getKeyResults().get(0);
        project = project(1L, ProjectStatus.ACTIVO, "2026-Q1", "2026-Q2");
    }

    @Test
    void linksProjectToKeyResultAndWarnsWhenWeightExceedsOneHundred() {
        ProjectKeyResultLink saved = link(project, keyResult, 60);
        ProjectKeyResultLink existing = link(project(2L, ProjectStatus.ACTIVO, "2026-Q1", "2026-Q2"), keyResult, 50);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(linkRepository.existsByProjectIdAndKeyResultIdAndActiveTrue(1L, 1L)).thenReturn(false);
        when(linkRepository.save(any(ProjectKeyResultLink.class))).thenReturn(saved);
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(existing, saved));

        var response = service.link(new ProjectDtos.ProjectKeyResultLinkRequest(1L, 1L, BigDecimal.valueOf(60)));

        assertThat(response.projectId()).isEqualTo(1L);
        assertThat(response.totalWeightForKeyResult()).isEqualByComparingTo("110.00");
        assertThat(response.overweightWarning()).isTrue();
        verify(progressService).recalculateKeyResult(1L);
    }

    @Test
    void listsLinksByProjectByKeyResultAndWithoutFilters() {
        ProjectKeyResultLink link = link(project, keyResult, 35);
        when(linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(link));
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(link));
        when(linkRepository.findAll()).thenReturn(List.of(link, inactiveLink(project, keyResult, 20)));

        assertThat(service.list(1L, null)).hasSize(1);
        assertThat(service.list(1L, 1L)).hasSize(1);
        assertThat(service.list(1L, 99L)).isEmpty();
        assertThat(service.list(null, 1L)).hasSize(1);
        assertThat(service.list(null, null)).hasSize(1);
    }

    @Test
    void unlinksAndRecalculatesKeyResult() {
        ProjectKeyResultLink link = link(project, keyResult, 35);
        when(linkRepository.findById(9L)).thenReturn(Optional.of(link));

        service.unlink(9L);

        assertThat(link.isActive()).isFalse();
        verify(linkRepository).save(link);
        verify(progressService).recalculateKeyResult(1L);
    }

    @Test
    void returnsImpactChainUsingOnlyCompletedProjectsAsAppliedContribution() {
        Project completed = project(2L, ProjectStatus.FINALIZADO, "2026-Q2", null);
        ProjectKeyResultLink activeLink = link(project, keyResult, 35);
        ProjectKeyResultLink completedLink = link(completed, keyResult, 65);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(activeLink, completedLink));

        var response = service.impactChain(1L);

        assertThat(response.impacts()).hasSize(2);
        assertThat(response.impacts().get(0).appliedContribution()).isEqualByComparingTo("0.00");
        assertThat(response.impacts().get(1).appliedContribution()).isEqualByComparingTo("65.00");
        assertThat(response.impacts().get(1).period()).isEqualTo("2026-Q2");
    }

    @Test
    void sadPathsRejectInvalidLinkOperations() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(keyResultRepository.findById(404L)).thenReturn(Optional.empty());
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(linkRepository.existsByProjectIdAndKeyResultIdAndActiveTrue(1L, 1L)).thenReturn(true);
        when(linkRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(new ProjectDtos.ProjectKeyResultLinkRequest(404L, 1L, BigDecimal.TEN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Proyecto");
        assertThatThrownBy(() -> service.link(new ProjectDtos.ProjectKeyResultLinkRequest(1L, 404L, BigDecimal.TEN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Key Result");
        assertThatThrownBy(() -> service.link(new ProjectDtos.ProjectKeyResultLinkRequest(1L, 1L, BigDecimal.TEN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya esta vinculado");
        assertThatThrownBy(() -> service.unlink(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vinculo");
        assertThatThrownBy(() -> service.impactChain(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Proyecto");
    }

    private ProjectKeyResultLink link(Project project, KeyResult keyResult, int weight) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setProject(project);
        link.setKeyResult(keyResult);
        link.setContributionWeight(BigDecimal.valueOf(weight));
        return link;
    }

    private ProjectKeyResultLink inactiveLink(Project project, KeyResult keyResult, int weight) {
        ProjectKeyResultLink link = link(project, keyResult, weight);
        link.setActive(false);
        return link;
    }

    private Project project(Long id, ProjectStatus status, String startPeriod, String endPeriod) {
        Project project = new Project();
        project.setId(id);
        project.setName("Proyecto " + id);
        project.setDescription("Descripcion");
        project.setType(ProjectType.INVESTIGACION);
        project.setStatus(status);
        project.setStartPeriod(startPeriod);
        project.setEndPeriod(endPeriod);
        project.setGlobalProgress(BigDecimal.ZERO);
        return project;
    }
}
