package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ContributionType;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest extends ProjectServiceTestSupport {
    @Test
    void createsLocalProjectWhenPayloadIsValid() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(validRequest());

        assertThat(response.name()).isEqualTo("Proyecto MSP");
        assertThat(response.origin()).isEqualTo(ProjectOrigin.LOCAL);
        assertThat(response.syncStatus()).isEqualTo(ProjectSyncStatus.LOCAL_ONLY);
        assertThat(response.tutors()).containsExactly("Tutora Uno");
    }

    @Test
    void createsLocalProjectWithImmediateKeyResultLinks() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(10L);
            return project;
        });
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(linkRepository.existsByProjectIdAndKeyResultIdAndActiveTrue(10L, 1L)).thenReturn(false);
        when(linkRepository.save(any(ProjectKeyResultLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new ProjectDtos.ProjectRequest(
                "Proyecto con KR",
                "Descripcion",
                ProjectType.INVESTIGACION,
                1L,
                ProjectStatus.ACTIVO,
                "2026-1",
                "2026-2",
                null,
                null,
                List.of("Tutor"),
                List.of(new ProjectDtos.ProjectKeyResultDraftRequest(1L, BigDecimal.valueOf(35), ContributionType.SOPORTE))
        ));

        assertThat(response.id()).isEqualTo(10L);
        verify(linkRepository).save(argThat(link -> link.getProject().getId().equals(10L)
                && link.getKeyResult().getId().equals(1L)
                && link.getContributionWeight().compareTo(BigDecimal.valueOf(35)) == 0
                && link.getContributionType() == ContributionType.SOPORTE));
        verify(keyResultProgressService).recalculateKeyResult(1L);
    }

    @Test
    void rejectsImmediateKeyResultLinksWhenKeyResultIsMissingOrDuplicated() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(10L);
            return project;
        });

        when(keyResultRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(requestWithImmediateLink(999L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Key Result");

        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(linkRepository.existsByProjectIdAndKeyResultIdAndActiveTrue(10L, 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.create(requestWithImmediateLink(1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vinculado");

        verify(linkRepository, never()).save(any(ProjectKeyResultLink.class));
    }

    @Test
    void managesStatusProgressHistoryAndDetailForExistingProject() {
        Project project = localProject();
        ProjectProgressEntry entry = new ProjectProgressEntry();
        entry.setProject(project);
        entry.setProgressPercent(BigDecimal.valueOf(45));
        entry.setComment("Avance registrado");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(progressRepository.save(any(ProjectProgressEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(progressRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(entry));

        assertThat(service.get(1L).name()).isEqualTo("Proyecto MSP");
        assertThat(service.updateStatus(1L, new ProjectDtos.ProjectStatusRequest(ProjectStatus.FINALIZADO)).status())
                .isEqualTo(ProjectStatus.FINALIZADO);
        assertThat(service.registerProgress(1L, new ProjectDtos.ProjectProgressRequest(BigDecimal.valueOf(45), "Avance registrado", "Hito")).progressPercent())
                .isEqualByComparingTo("45");
        assertThat(service.history(1L)).hasSize(1);
        assertThat(project.getActualEndDate()).isNotNull();
    }

    @Test
    void sadPathsRejectInvalidProjectOperations() {
        Project project = localProject();
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.create(new ProjectDtos.ProjectRequest("Proyecto", "Descripcion", ProjectType.GRADO,
                99L, ProjectStatus.ACTIVO, "2026-1", null, null, null, List.of(), List.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Departamento");
        assertThatThrownBy(() -> service.create(new ProjectDtos.ProjectRequest("Proyecto", "Descripcion", ProjectType.GRADO,
                1L, ProjectStatus.ACTIVO, "2026-1", null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), List.of(), List.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("fecha de fin");
        assertThatThrownBy(() -> service.create(new ProjectDtos.ProjectRequest("Proyecto", "Descripcion", ProjectType.GRADO,
                1L, ProjectStatus.ACTIVO, "2026-2", "2026-1", null, null, List.of(), List.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("periodo de fin");
        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.updateStatus(404L, new ProjectDtos.ProjectStatusRequest(ProjectStatus.ACTIVO))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.registerProgress(404L, new ProjectDtos.ProjectProgressRequest(BigDecimal.TEN, "x", null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.history(404L)).isInstanceOf(BusinessException.class);

        SecurityContextHolder.clearContext();
        when(progressRepository.save(any(ProjectProgressEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.registerProgress(1L, new ProjectDtos.ProjectProgressRequest(BigDecimal.ONE, "Sin principal", null)).createdByExternalUserId())
                .isNull();
    }

    @Test
    void coversOptionalBranchesForCreateUpdateAndStatus() {
        Project project = localProject();
        project.setActualEndDate(LocalDate.of(2026, 12, 1));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(new ProjectDtos.ProjectRequest(
                "Proyecto borrador", "Descripcion", ProjectType.GRADO, 1L, null, "2026-1", null, null, null,
                1L, BigDecimal.valueOf(25), "ACTIVO", null, null, null));
        var updated = service.update(1L, new ProjectDtos.ProjectUpdateRequest(
                "Proyecto actualizado", "Descripcion", ProjectType.EXTENSION, 1L, "2026-1", null, null, null, null,
                1L, BigDecimal.valueOf(25), "ACTIVO", null, new ArrayList<>()));
        var active = service.updateStatus(1L, new ProjectDtos.ProjectStatusRequest(ProjectStatus.ACTIVO));
        project.setActualEndDate(LocalDate.of(2026, 12, 1));
        var finalized = service.updateStatus(1L, new ProjectDtos.ProjectStatusRequest(ProjectStatus.FINALIZADO));

        assertThat(created.status()).isEqualTo(ProjectStatus.BORRADOR);
        assertThat(updated.endPeriod()).isEqualTo("2026-1");
        assertThat(active.status()).isEqualTo(ProjectStatus.ACTIVO);
        assertThat(finalized.actualEndDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void enforcesFineGrainedProjectStatusTransitionMatrix() {
        Project archived = localProject();
        archived.setStatus(ProjectStatus.ARCHIVADO);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(archived));

        setRoles("DIRECTOR_ESCUELA");
        assertThatThrownBy(() -> service.updateStatus(1L, new ProjectDtos.ProjectStatusRequest(ProjectStatus.ACTIVO)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).status())
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);

        setRoles("ADMIN");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.updateStatus(1L, new ProjectDtos.ProjectStatusRequest(ProjectStatus.ACTIVO)).status())
                .isEqualTo(ProjectStatus.ACTIVO);
    }

    @Test
    void registerProgressWithNonExternalPrincipalStoresNullActor() {
        Project project = localProject();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("plain-user", null, List.of()));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(progressRepository.save(any(ProjectProgressEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.registerProgress(1L, new ProjectDtos.ProjectProgressRequest(BigDecimal.TEN, "Avance", null));

        assertThat(response.createdByExternalUserId()).isNull();
    }

    @Test
    void projectEntityHandlesNullTutorsAndTouchUpdatesTimestamp() {
        Project project = localProject();

        project.setTutors(null);
        var before = project.getUpdatedAt();
        project.touch();

        assertThat(project.getTutors()).isEmpty();
        assertThat(project.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void extractsBearerOnlyWhenHeaderIsValid() {
        assertThat(service.bearerValue("Bearer abc")).isEqualTo("Bearer abc");
        assertThat(service.bearerValue("Basic abc")).isNull();
        assertThat(service.bearerValue(null)).isNull();
    }
}
