package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceBranchesTest extends ProjectServiceTestSupport {

    @Test
    void blankLinkStatusOnCreateDefaultsToActive() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProjectDtos.ProjectRequest base = validRequest();

        var created = service.create(new ProjectDtos.ProjectRequest(
                base.name(), base.description(), base.type(), base.departmentId(), base.status(),
                base.startPeriod(), base.endPeriod(), base.startDate(), base.endDate(),
                1L, BigDecimal.valueOf(25), "   ", null, null, List.of(), List.of()));

        assertThat(created.linkStatus()).isEqualTo("ACTIVO");
    }

    @Test
    void updateKeepsLinkedKeyResultWhenSwitchingTargetOrWhenProjectHadNone() {
        KeyResult other = TestFixtures.keyResult(2L, TestFixtures.unit(1L));
        Project project = localProject();
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(keyResultRepository.findById(2L)).thenReturn(Optional.of(other));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var switched = service.update(1L, updateRequest(2L));
        assertThat(switched.keyResultId()).isEqualTo(2L);

        project.setKeyResult(null);
        var linked = service.update(1L, updateRequest(1L));
        assertThat(linked.keyResultId()).isEqualTo(1L);
    }

    @Test
    void updateWithoutKeyResultLeavesTheCurrentTargetUntouched() {
        Project project = localProject();
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.update(1L, updateRequest(null));

        assertThat(updated.keyResultId()).isEqualTo(1L);
        verify(keyResultRepository, never()).findById(any());
    }

    @Test
    void statusChangeIsRefusedWithoutAnAuthenticatedUser() {
        Project project = localProject();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.updateStatus(1L, new ProjectDtos.ProjectStatusRequest(ProjectStatus.FINALIZADO)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void statusChangeUsesGrantedAuthoritiesForNonExternalPrincipals() {
        Project project = localProject();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "plain-user", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        var updated = service.updateStatus(1L, new ProjectDtos.ProjectStatusRequest(ProjectStatus.SUSPENDIDO));

        assertThat(updated.status()).isEqualTo(ProjectStatus.SUSPENDIDO);
    }

    @Test
    void syncReportsFailureWhenOnlyArchivedKeyResultsExist() {
        KeyResult archived = TestFixtures.keyResult(3L, TestFixtures.unit(1L));
        archived.setArchived(true);
        when(keyResultRepository.findAll()).thenReturn(List.of(archived));
        var payload = externalPayload(301L, "grado", "ACTIVO", COMPUTING_DEPARTMENT, "2026-1", "2026-2", "{}", "Proyecto sin KR");
        when(trayectoriaProjectClient.fetchProjects(null)).thenReturn(List.of(payload));
        when(departmentRepository.findByNameIgnoreCase(COMPUTING_DEPARTMENT)).thenReturn(Optional.of(department));

        var response = service.syncFromTrayectoria(null);

        assertThat(response.imported()).isZero();
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.warnings()).singleElement().asString().contains("301").contains("key_result_id");
        verify(projectRepository, never()).save(any());
    }

    private ProjectDtos.ProjectUpdateRequest updateRequest(Long keyResultId) {
        return new ProjectDtos.ProjectUpdateRequest(
                "Proyecto MSP", "Descripcion", ProjectType.INVESTIGACION, 1L, "2026-1", "2026-2",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1), null,
                keyResultId, BigDecimal.valueOf(25), "ACTIVO", null, null, List.of("Tutora Uno"));
    }
}
