package co.edu.icesi.pdg.mte.integration;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectKeyResultLinkArchivedTest {
    @Mock
    private ProjectKeyResultLinkRepository linkRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private KeyResultRepository keyResultRepository;
    @Mock
    private AuditService auditService;

    @Test
    void linkingAnArchivedKeyResultIsRejectedWithoutSavingAnything() {
        ProjectKeyResultLinkService service =
                new ProjectKeyResultLinkService(linkRepository, projectRepository, keyResultRepository, auditService);
        Project project = new Project();
        project.setId(1L);
        project.setName("Proyecto");
        project.setDescription("Descripcion");
        project.setType(ProjectType.INVESTIGACION);
        project.setStatus(ProjectStatus.ACTIVO);
        KeyResult archived = TestFixtures.keyResult(1L, TestFixtures.unit(1L));
        archived.setArchived(true);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.link(new ProjectDtos.ProjectKeyResultLinkRequest(
                1L, 1L, BigDecimal.valueOf(40), ContributionType.DIRECTA)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("archivado")
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        verify(linkRepository, never()).save(any());
    }
}
