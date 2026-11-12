package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ExternalProjectPayload;
import co.edu.icesi.pdg.mte.integration.IntegrationProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectFieldMapperTest {
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final IntegrationProperties properties = new IntegrationProperties();
    private final ProjectFieldMapper mapper = new ProjectFieldMapper(departments, properties, new ProjectPeriodService());

    @Test
    void localFieldsAreTrimmedAndEndPeriodDefaultsToStart() {
        when(departments.findById(1L)).thenReturn(Optional.of(TestFixtures.department(1L)));
        Project project = new Project();

        mapper.applyLocalFields(project, " Nombre ", " Desc ", ProjectType.GRADO, 1L, " 2026-1 ", null,
                null, null, null, Arrays.asList(" Tutor ", null, " "));
        assertThat(project.getName()).isEqualTo("Nombre");
        assertThat(project.getEndPeriod()).isEqualTo("2026-1");
        assertThat(project.getTutors()).containsExactly("Tutor");

        mapper.applyLocalFields(project, "N", "D", ProjectType.GRADO, 1L, "2026-1", "  ", null, null, null, null);
        assertThat(project.getEndPeriod()).isEqualTo("2026-1");
        assertThat(project.getTutors()).isEmpty();

        mapper.applyLocalFields(project, "N", "D", ProjectType.GRADO, 1L, "2026-1", " 2026-2 ", null, null, null, List.of());
        assertThat(project.getEndPeriod()).isEqualTo("2026-2");
    }

    @Test
    void dateRangesAreValidated() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        assertThatThrownBy(() -> apply(start, start, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> apply(start, start.plusDays(5), start.minusDays(1)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("fin real");
        // Missing start or end dates are accepted, as is an actual end on the start date.
        apply(null, start, start.minusDays(30));
        apply(start, null, null);
        apply(start, start.plusDays(5), start);
    }

    @Test
    void externalTypeMappingCoversEveryKnownLabel() {
        assertThat(externalType(null)).isEqualTo(ProjectType.INVESTIGACION);
        assertThat(externalType(" proyecto de grado ")).isEqualTo(ProjectType.GRADO);
        assertThat(externalType("extension")).isEqualTo(ProjectType.EXTENSION);
        assertThat(externalType("extensa")).isEqualTo(ProjectType.EXTENSION);
        assertThat(externalType("macro")).isEqualTo(ProjectType.MACROPROYECTO);
        assertThat(externalType("otro")).isEqualTo(ProjectType.INVESTIGACION);
    }

    @Test
    void externalStatusMappingCoversEveryKnownLabel() {
        assertThat(externalStatus(null)).isEqualTo(ProjectStatus.BORRADOR);
        assertThat(externalStatus("en curso")).isEqualTo(ProjectStatus.ACTIVO);
        assertThat(externalStatus("activo")).isEqualTo(ProjectStatus.ACTIVO);
        assertThat(externalStatus("finalizado")).isEqualTo(ProjectStatus.FINALIZADO);
        assertThat(externalStatus("suspendido")).isEqualTo(ProjectStatus.SUSPENDIDO);
        assertThat(externalStatus("archivado")).isEqualTo(ProjectStatus.ARCHIVADO);
        assertThat(externalStatus("desconocido")).isEqualTo(ProjectStatus.BORRADOR);
    }

    @Test
    void externalDefaultsApplyForBlankNamePeriodsAndDepartment() {
        Project project = new Project();
        mapper.applyExternalPayload(project, payload(9L, "  ", null, "   ", "  ", null));

        assertThat(project.getName()).isEqualTo("Proyecto externo 9");
        assertThat(project.getDescription()).contains("Trayectoria");
        assertThat(project.getStartPeriod()).isEqualTo("2026-1");
        assertThat(project.getEndPeriod()).isEqualTo("2026-1");
        assertThat(project.getDepartment()).isNull();
        assertThat(project.getDepartmentName()).isEqualTo("  ");
        assertThat(project.getTutors()).isEmpty();
        assertThat(project.getOrigin()).isEqualTo(ProjectOrigin.SYNCED);
    }

    @Test
    void externalDepartmentIsResolvedByNameWhenKnown() {
        var department = TestFixtures.department(1L);
        when(departments.findByNameIgnoreCase(department.getName())).thenReturn(Optional.of(department));
        when(departments.findByNameIgnoreCase("Desconocido")).thenReturn(Optional.empty());
        Project known = new Project();
        Project unknown = new Project();

        mapper.applyExternalPayload(known, payload(1L, "Nombre", " 2026-2 ", "2027-1", department.getName(), List.of(" Tutor ")));
        mapper.applyExternalPayload(unknown, payload(2L, "Nombre", null, null, "Desconocido", null));

        assertThat(known.getDepartment()).isSameAs(department);
        assertThat(known.getStartPeriod()).isEqualTo("2026-2");
        assertThat(known.getEndPeriod()).isEqualTo("2027-1");
        assertThat(known.getTutors()).containsExactly("Tutor");
        assertThat(unknown.getDepartment()).isNull();
        assertThat(unknown.getDepartmentName()).isEqualTo("Desconocido");
    }

    private void apply(LocalDate start, LocalDate end, LocalDate actualEnd) {
        when(departments.findById(1L)).thenReturn(Optional.of(TestFixtures.department(1L)));
        mapper.applyLocalFields(new Project(), "N", "D", ProjectType.GRADO, 1L, "2026-1", null, start, end, actualEnd, null);
    }

    private ProjectType externalType(String raw) {
        Project project = new Project();
        mapper.applyExternalPayload(project, new ExternalProjectPayload(
                1L, "N", "D", null, raw, null, null, null, null, null, null, "{}"));
        return project.getType();
    }

    private ProjectStatus externalStatus(String raw) {
        Project project = new Project();
        mapper.applyExternalPayload(project, new ExternalProjectPayload(
                1L, "N", "D", raw, null, null, null, null, null, null, null, "{}"));
        return project.getStatus();
    }

    private ExternalProjectPayload payload(Long id, String name, String startPeriod, String endPeriod,
                                           String departmentName, List<String> tutors) {
        return new ExternalProjectPayload(id, name, null, null, null, departmentName, startPeriod, endPeriod,
                null, null, tutors, "{}");
    }
}
