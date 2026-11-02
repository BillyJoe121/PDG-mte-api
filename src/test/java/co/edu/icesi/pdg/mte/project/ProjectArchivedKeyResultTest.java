package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ContributionType;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectArchivedKeyResultTest extends ProjectServiceTestSupport {
    private KeyResult archived;

    @BeforeEach
    void archivedKeyResultFixture() {
        archived = TestFixtures.keyResult(2L, TestFixtures.unit(1L));
        archived.setArchived(true);
        lenient().when(keyResultRepository.findById(2L)).thenReturn(Optional.of(archived));
        lenient().when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        lenient().when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(5L);
            }
            return saved;
        });
    }

    private ProjectDtos.ProjectRequest createRequest(Long keyResultId, List<ProjectDtos.ProjectKeyResultDraftRequest> links) {
        return new ProjectDtos.ProjectRequest(
                "Proyecto MSP", "Descripcion", ProjectType.INVESTIGACION, 1L, ProjectStatus.ACTIVO, "2026-1", "2026-2",
                null, null, keyResultId, BigDecimal.valueOf(25), "ACTIVO", null, null, List.of(), links);
    }

    private ProjectDtos.ProjectUpdateRequest updateRequest(Long keyResultId) {
        return new ProjectDtos.ProjectUpdateRequest(
                "Proyecto MSP", "Descripcion", ProjectType.INVESTIGACION, 1L, "2026-1", "2026-2", null, null, null,
                keyResultId, BigDecimal.valueOf(25), "ACTIVO", null, null, new ArrayList<>());
    }

    private static void assertArchivedConflict(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessageContaining("archivado")
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createRejectsAnArchivedDirectKeyResult() {
        assertThatThrownBy(() -> service.create(createRequest(2L, List.of()))).satisfies(ProjectArchivedKeyResultTest::assertArchivedConflict);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void createRejectsAnArchivedKeyResultInTheImmediateLinks() {
        var links = List.of(
                new ProjectDtos.ProjectKeyResultDraftRequest(1L, BigDecimal.valueOf(25), ContributionType.DIRECTA),
                new ProjectDtos.ProjectKeyResultDraftRequest(2L, BigDecimal.valueOf(25), ContributionType.INDIRECTA));

        assertThatThrownBy(() -> service.create(createRequest(1L, links))).satisfies(ProjectArchivedKeyResultTest::assertArchivedConflict);
    }

    @Test
    void theDefaultKeyResultNeverComesFromTheArchivedOnes() {
        keyResult.setArchived(true);

        assertThatThrownBy(() -> service.create(createRequest(null, List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateRejectsMovingTheProjectToAnArchivedKeyResult() {
        Project project = localProject();
        lenient().when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.update(1L, updateRequest(2L))).satisfies(ProjectArchivedKeyResultTest::assertArchivedConflict);
        assertThat(project.getKeyResult()).isSameAs(keyResult);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateKeepsTheCurrentKeyResultEvenIfItWasArchivedLater() {
        keyResult.setArchived(true);
        Project project = localProject();
        lenient().when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        service.update(1L, updateRequest(1L));

        assertThat(project.getKeyResult()).isSameAs(keyResult);
        verify(projectRepository).save(project);
    }
}
