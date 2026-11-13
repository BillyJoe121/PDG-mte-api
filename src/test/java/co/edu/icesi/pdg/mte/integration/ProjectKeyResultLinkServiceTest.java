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
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
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
    private AuditService auditService;

    private ProjectKeyResultLinkService service;
    private Project project;
    private KeyResult keyResult;

    @BeforeEach
    void setUp() {
        service = new ProjectKeyResultLinkService(linkRepository, projectRepository, keyResultRepository, auditService);
        MeasurementUnit unit = TestFixtures.unit(1L);
        Department department = TestFixtures.department(1L);
        keyResult = TestFixtures.keyResult(
                1L,
                unit,
                TestFixtures.period(1L),
                TestFixtures.goal(1L, unit),
                TestFixtures.strategicBet(1L)
        );
        keyResult.setCurrentValue(BigDecimal.valueOf(33));
        keyResult.setProgressPercentage(BigDecimal.valueOf(44));
        project = project(1L, ProjectStatus.ACTIVO, "2026-Q1", "2026-Q2");
    }

    private void assertRecordedKeyResultValuesUnchanged() {
        assertThat(keyResult.getCurrentValue()).isEqualByComparingTo("33");
        assertThat(keyResult.getProgressPercentage()).isEqualByComparingTo("44");
        verify(keyResultRepository, never()).save(any());
    }

    @Test
    void linksProjectToKeyResultAndKeepsRecordedValues() {
        ProjectKeyResultLink saved = link(project, keyResult);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(linkRepository.existsByProjectIdAndKeyResultIdAndActiveTrue(1L, 1L)).thenReturn(false);
        when(linkRepository.save(any(ProjectKeyResultLink.class))).thenReturn(saved);

        var response = service.link(new ProjectDtos.ProjectKeyResultLinkRequest(1L, 1L));

        assertThat(response.projectId()).isEqualTo(1L);
        assertThat(response.keyResultId()).isEqualTo(1L);
        assertThat(response.active()).isTrue();
        assertRecordedKeyResultValuesUnchanged();
    }

    @Test
    void linksTheSameProjectToDifferentKeyResultsWhileStillCheckingEachPairForDuplicates() {
        KeyResult secondKeyResult = TestFixtures.keyResult(2L, TestFixtures.unit(1L));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(keyResultRepository.findById(2L)).thenReturn(Optional.of(secondKeyResult));
        when(linkRepository.save(any(ProjectKeyResultLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var first = service.link(new ProjectDtos.ProjectKeyResultLinkRequest(1L, 1L));
        var second = service.link(new ProjectDtos.ProjectKeyResultLinkRequest(1L, 2L));

        assertThat(first.keyResultId()).isEqualTo(1L);
        assertThat(second.keyResultId()).isEqualTo(2L);
        verify(linkRepository).existsByProjectIdAndKeyResultIdAndActiveTrue(1L, 1L);
        verify(linkRepository).existsByProjectIdAndKeyResultIdAndActiveTrue(1L, 2L);
        assertRecordedKeyResultValuesUnchanged();
        assertThat(secondKeyResult.getProgressPercentage()).isEqualByComparingTo("25");
    }

    @Test
    void listsLinksByProjectByKeyResultAndWithoutFilters() {
        ProjectKeyResultLink link = link(project, keyResult);
        when(linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(link));
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(link));
        when(linkRepository.findAll()).thenReturn(List.of(link, inactiveLink(project, keyResult)));

        assertThat(service.list(1L, null)).hasSize(1);
        assertThat(service.list(1L, 1L)).hasSize(1);
        assertThat(service.list(1L, 99L)).isEmpty();
        assertThat(service.list(null, 1L)).hasSize(1);
        assertThat(service.list(null, null)).hasSize(1);
    }

    @Test
    void listsActiveExternalStyleLinkWithoutLocalProject() {
        ProjectKeyResultLink linkWithoutProject = link(null, keyResult);
        when(linkRepository.findAll()).thenReturn(List.of(linkWithoutProject));

        var response = service.list(null, null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).projectId()).isNull();
        assertThat(response.get(0).projectName()).isNull();
        assertThat(response.get(0).keyResultId()).isEqualTo(1L);
    }

    @Test
    void unlinksWithoutChangingTheRecordedKeyResultValues() {
        ProjectKeyResultLink link = link(project, keyResult);
        when(linkRepository.findById(9L)).thenReturn(Optional.of(link));

        service.unlink(9L);

        assertThat(link.isActive()).isFalse();
        verify(linkRepository).save(link);
        assertRecordedKeyResultValuesUnchanged();
    }

    @Test
    void returnsImpactChainWithCompletionAndPeriodPerLink() {
        Project completed = project(2L, ProjectStatus.FINALIZADO, "2026-Q2", null);
        completed.setGlobalProgress(BigDecimal.valueOf(100));
        project.setGlobalProgress(BigDecimal.valueOf(20));
        ProjectKeyResultLink activeLink = link(project, keyResult);
        ProjectKeyResultLink completedLink = link(completed, keyResult);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(activeLink, completedLink));

        var response = service.impactChain(1L);

        assertThat(response.impacts()).hasSize(2);
        assertThat(response.impacts().get(0).keyResultId()).isEqualTo(1L);
        assertThat(response.impacts().get(0).projectCompleted()).isFalse();
        assertThat(response.impacts().get(1).projectCompleted()).isTrue();
        assertThat(response.impacts().get(1).period()).isEqualTo("2026-Q2");
    }

    @Test
    void impactChainHandlesNullProjectAndBlankEndPeriod() {
        Project blankEndPeriod = project(3L, ProjectStatus.ACTIVO, "2026-Q1", " ");
        ProjectKeyResultLink linkWithoutProject = link(null, keyResult);
        ProjectKeyResultLink blankEndPeriodLink = link(blankEndPeriod, keyResult);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(linkWithoutProject, blankEndPeriodLink));

        var response = service.impactChain(1L);

        assertThat(response.impacts()).hasSize(2);
        assertThat(response.impacts().get(0).projectCompleted()).isFalse();
        assertThat(response.impacts().get(0).period()).isNull();
        assertThat(response.impacts().get(1).period()).isEqualTo("2026-Q1");
    }

    @Test
    void sadPathsRejectInvalidLinkOperations() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(keyResultRepository.findById(404L)).thenReturn(Optional.empty());
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(linkRepository.existsByProjectIdAndKeyResultIdAndActiveTrue(1L, 1L)).thenReturn(true);
        when(linkRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(new ProjectDtos.ProjectKeyResultLinkRequest(404L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Proyecto");
        assertThatThrownBy(() -> service.link(new ProjectDtos.ProjectKeyResultLinkRequest(1L, 404L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Key Result");
        assertThatThrownBy(() -> service.link(new ProjectDtos.ProjectKeyResultLinkRequest(1L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya esta vinculado");
        assertThatThrownBy(() -> service.unlink(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vinculo");
        assertThatThrownBy(() -> service.impactChain(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Proyecto");
    }

    private ProjectKeyResultLink link(Project project, KeyResult keyResult) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setProject(project);
        link.setKeyResult(keyResult);
        return link;
    }

    private ProjectKeyResultLink inactiveLink(Project project, KeyResult keyResult) {
        ProjectKeyResultLink link = link(project, keyResult);
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
