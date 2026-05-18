package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.integration.ExternalProjectPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceSyncTest extends ProjectServiceTestSupport {
    @Test
    void syncsExternalProjectsAsInternalProjects() {
        ExternalProjectPayload imported = new ExternalProjectPayload(
                77L,
                "Proyecto externo",
                "Descripcion externa",
                "EN_CURSO",
                "extension",
                "Departamento de TIC",
                "2026-1",
                "2026-2",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 1),
                List.of("Tutor Externo"),
                "{\"id_proyecto\":77}"
        );
        ExternalProjectPayload withoutId = new ExternalProjectPayload(
                null,
                "Proyecto sin id",
                "No debe importarse",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "{}"
        );

        when(trayectoriaProjectClient.fetchProjects("Bearer token")).thenReturn(List.of(imported, withoutId));
        when(projectRepository.findByExternalSourceAndExternalProjectId("TRAYECTORIA_DOCENTE", 77L)).thenReturn(Optional.empty());
        when(departmentRepository.findByNameIgnoreCase("Departamento de TIC")).thenReturn(Optional.of(department));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.syncFromTrayectoria("Bearer token");

        assertThat(response.imported()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.warnings()).hasSize(1);
        verify(projectRepository).save(argThat(project -> project.getOrigin() == ProjectOrigin.SYNCED
                && project.getType() == ProjectType.EXTENSION
                && project.getStatus() == ProjectStatus.ACTIVO));
    }

    @Test
    void syncMapsExternalTypeStatusDefaultsAndFailures() {
        List<ExternalProjectPayload> payloads = List.of(
                externalPayload(101L, "grado", null, null, null, null, null, "Proyecto grado"),
                externalPayload(102L, "extension social", "FINALIZADO", "", "", "", "", ""),
                externalPayload(103L, "macro", "SUSPENDIDO", "Departamento de TIC", "2026-1", "2026-2", "{}", "Proyecto macro"),
                externalPayload(104L, "otro", "ARCHIVADO", "Departamento de TIC", "2026-1", "2026-2", "{}", "Proyecto otro"),
                externalPayload(105L, null, "desconocido", "Departamento de TIC", "2026-1", "2026-2", "{}", "Proyecto default")
        );

        when(trayectoriaProjectClient.fetchProjects(null)).thenReturn(payloads);
        for (ExternalProjectPayload payload : payloads) {
            when(projectRepository.findByExternalSourceAndExternalProjectId("TRAYECTORIA_DOCENTE", payload.externalProjectId()))
                    .thenReturn(Optional.empty());
        }
        when(departmentRepository.findByNameIgnoreCase("Departamento de TIC")).thenReturn(Optional.of(department));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            if (project.getExternalProjectId().equals(104L)) {
                throw new IllegalStateException("fallo controlado");
            }
            return project;
        });

        var response = service.syncFromTrayectoria(null);

        assertThat(response.imported()).isEqualTo(4);
        assertThat(response.failed()).isEqualTo(1);
        verify(projectRepository).save(argThat(project -> project.getExternalProjectId().equals(101L)
                && project.getType() == ProjectType.GRADO
                && project.getStatus() == ProjectStatus.BORRADOR
                && project.getDepartment() == null
                && project.getStartPeriod().equals("2026-1")));
        verify(projectRepository).save(argThat(project -> project.getExternalProjectId().equals(102L)
                && project.getType() == ProjectType.EXTENSION
                && project.getStatus() == ProjectStatus.FINALIZADO
                && project.getName().equals("Proyecto externo 102")));
        verify(projectRepository).save(argThat(project -> project.getExternalProjectId().equals(103L)
                && project.getType() == ProjectType.MACROPROYECTO
                && project.getStatus() == ProjectStatus.SUSPENDIDO));
        verify(projectRepository).save(argThat(project -> project.getExternalProjectId().equals(105L)
                && project.getType() == ProjectType.INVESTIGACION
                && project.getStatus() == ProjectStatus.BORRADOR));
    }

    @Test
    void updatesExistingExternalProjectDuringSync() {
        Project existing = localProject();
        existing.setExternalSource("TRAYECTORIA_DOCENTE");
        existing.setExternalProjectId(88L);
        ExternalProjectPayload payload = new ExternalProjectPayload(
                88L,
                null,
                null,
                "FINALIZADO",
                "macroproyecto",
                "Departamento no catalogado",
                null,
                null,
                null,
                null,
                null,
                "{}"
        );

        when(trayectoriaProjectClient.fetchProjects(null)).thenReturn(List.of(payload));
        when(projectRepository.findByExternalSourceAndExternalProjectId("TRAYECTORIA_DOCENTE", 88L)).thenReturn(Optional.of(existing));
        when(departmentRepository.findByNameIgnoreCase("Departamento no catalogado")).thenReturn(Optional.empty());
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.syncFromTrayectoria(null);

        assertThat(response.updated()).isEqualTo(1);
        assertThat(existing.getName()).isEqualTo("Proyecto externo 88");
        assertThat(existing.getType()).isEqualTo(ProjectType.MACROPROYECTO);
        assertThat(existing.getStatus()).isEqualTo(ProjectStatus.FINALIZADO);
    }
}
