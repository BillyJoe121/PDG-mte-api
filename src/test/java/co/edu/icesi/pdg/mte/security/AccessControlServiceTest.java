package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.project.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessControlServiceTest {
    private final AccessControlService service = new AccessControlService();

    @Test
    void adminReceivesAdministrativeCapabilities() {
        var capabilities = service.capabilities(List.of("ROLE_ADMIN"));

        assertThat(capabilities.get("manageCatalogs")).isTrue();
        assertThat(capabilities.get("viewAuditLogs")).isTrue();
        assertThat(capabilities.get("viewReports")).isTrue();
        assertThat(service.normalizeToList(List.of("ROLE_ADMIN"))).containsExactly("ADMIN");
    }

    @Test
    void decanoReceivesExecutiveCapabilitiesWithoutCatalogOrAuditAdministration() {
        var capabilities = service.capabilities(List.of("decano"));

        assertThat(capabilities.get("manageStrategicBets")).isTrue();
        assertThat(capabilities.get("changeProjectStatus")).isTrue();
        assertThat(capabilities.get("viewPresentation")).isTrue();
        assertThat(capabilities.get("manageCatalogs")).isFalse();
        assertThat(capabilities.get("viewAuditLogs")).isFalse();
    }

    @Test
    void jefeAndProfesorCapabilitiesAreSeparated() {
        var jefe = service.capabilities(List.of("JEFE_DPTO"));
        var profesor = service.capabilities(List.of("PROFESOR"));

        assertThat(jefe.get("linkProjectsToKeyResults")).isTrue();
        assertThat(jefe.get("viewReports")).isTrue();
        assertThat(jefe.get("viewPresentation")).isFalse();
        assertThat(profesor.get("createProjects")).isTrue();
        assertThat(profesor.get("registerProjectProgress")).isTrue();
        assertThat(profesor.get("linkProjectsToKeyResults")).isFalse();
        assertThat(profesor.get("viewReports")).isFalse();
    }

    @Test
    void normalizesRolesByTrimmingUppercasingRemovingPrefixAndDeduplicating() {
        assertThat(service.normalizeToList(Arrays.asList(" role_admin ", "ADMIN", "", " profesor ", null)))
                .containsExactly("ADMIN", "PROFESOR");
    }

    @Test
    void normalizesFrontendRoleAliases() {
        assertThat(service.normalizeToList(List.of("administrador", "director", "jefe", "tutor")))
                .containsExactly("ADMIN", "DIRECTOR_ESCUELA", "JEFE_DPTO", "PROFESOR");
    }

    @Test
    void nullAndUnknownRolesReceiveNoCapabilities() {
        var anonymous = service.capabilities(null);
        var unknown = service.capabilities(List.of("ROLE_INVITADO"));

        assertThat(anonymous).containsOnlyKeys(
                "viewDashboard",
                "viewStrategy",
                "viewProjects",
                "manageCatalogs",
                "manageStrategicBets",
                "manageGoals",
                "manageObjectives",
                "manageKeyResults",
                "createProjects",
                "updateProjects",
                "registerProjectProgress",
                "changeProjectStatus",
                "syncExternalProjects",
                "linkProjectsToKeyResults",
                "viewReports",
                "viewPresentation",
                "viewAuditLogs"
        );
        assertThat(anonymous).allSatisfy((key, value) -> assertThat(value).isFalse());
        assertThat(unknown).allSatisfy((key, value) -> assertThat(value).isFalse());
    }

    @Test
    void directorCanManageStrategyAndPresentationWithoutCatalogsOrAuditLogs() {
        var capabilities = service.capabilities(List.of("director_escuela"));

        assertThat(capabilities.get("manageStrategicBets")).isTrue();
        assertThat(capabilities.get("manageGoals")).isTrue();
        assertThat(capabilities.get("manageObjectives")).isTrue();
        assertThat(capabilities.get("linkProjectsToKeyResults")).isTrue();
        assertThat(capabilities.get("viewPresentation")).isTrue();
        assertThat(capabilities.get("manageCatalogs")).isFalse();
        assertThat(capabilities.get("viewAuditLogs")).isFalse();
    }

    @Test
    void projectStatusTransitionMatrixSeparatesOperationalAndAdministrativeTransitions() {
        assertThat(service.canChangeProjectStatus(ProjectStatus.ACTIVO, ProjectStatus.FINALIZADO, List.of("DIRECTOR_ESCUELA"))).isTrue();
        assertThat(service.canChangeProjectStatus(ProjectStatus.SUSPENDIDO, ProjectStatus.ACTIVO, List.of("DECANO"))).isTrue();
        assertThat(service.canChangeProjectStatus(ProjectStatus.ARCHIVADO, ProjectStatus.ACTIVO, List.of("DIRECTOR_ESCUELA"))).isFalse();
        assertThat(service.canChangeProjectStatus(ProjectStatus.ARCHIVADO, ProjectStatus.ACTIVO, List.of("ADMIN"))).isTrue();
        assertThat(service.canChangeProjectStatus(ProjectStatus.ACTIVO, ProjectStatus.FINALIZADO, List.of("PROFESOR"))).isFalse();
    }
}
