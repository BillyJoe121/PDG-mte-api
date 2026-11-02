package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectPlannerLinkTest extends ProjectServiceTestSupport {
    private static final String PLAN = "https://tasks.office.com/icesi.edu.co/Home/PlanViews/abc123";

    private ProjectDtos.ProjectRequest createRequest(String plannerUrl) {
        return new ProjectDtos.ProjectRequest(
                "Proyecto MSP", "Descripcion", ProjectType.INVESTIGACION, 1L, ProjectStatus.ACTIVO, "2026-1", "2026-2",
                null, null, 1L, BigDecimal.valueOf(25), "ACTIVO", null, plannerUrl, null, null);
    }

    private ProjectDtos.ProjectUpdateRequest updateRequest(String plannerUrl) {
        return new ProjectDtos.ProjectUpdateRequest(
                "Proyecto MSP", "Descripcion", ProjectType.INVESTIGACION, 1L, "2026-1", "2026-2", null, null, null,
                1L, BigDecimal.valueOf(25), "ACTIVO", null, plannerUrl, new ArrayList<>());
    }

    @Test
    void createStoresTheTrimmedLinkAndTheResponseExposesIt() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(createRequest("  " + PLAN + "  "));

        assertThat(response.plannerUrl()).isEqualTo(PLAN);
    }

    @Test
    void createWithoutLinkLeavesItEmpty() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(validRequest()).plannerUrl()).isNull();
    }

    @Test
    void updateReplacesAndThenClearsTheLink() {
        Project project = localProject();
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var replaced = service.update(1L, updateRequest(PLAN));
        var cleared = service.update(1L, updateRequest("   "));

        assertThat(replaced.plannerUrl()).isEqualTo(PLAN);
        assertThat(cleared.plannerUrl()).isNull();
    }

    @Test
    void createRejectsAnInvalidLinkWithBadRequestAndStoresNothing() {
        assertThatThrownBy(() -> service.create(createRequest("http://tasks.office.com/plan")))
                .isInstanceOf(BusinessException.class)
                .hasMessageStartingWith("El enlace de Planner no es valido")
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateRejectsAnInvalidLinkBeforeTouchingAnything() {
        assertThatThrownBy(() -> service.update(1L, updateRequest("https://evil.example/plan")))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(projectRepository, never()).save(any());
    }
}
