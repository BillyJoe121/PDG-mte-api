package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.project.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessControlServiceTest {
    private final AccessControlService service = new AccessControlService();

    @Test
    void exposesOnlyAdminAndUserRoles() {
        assertThat(AccessControlService.SUPPORTED_ROLES).containsExactly("ADMIN", "USER");
    }

    @Test
    void adminReceivesEveryCapability() {
        var capabilities = service.capabilities(List.of("ROLE_ADMIN"));

        assertThat(capabilities).allSatisfy((key, value) -> assertThat(value).isTrue());
        assertThat(service.normalizeToList(List.of("ROLE_ADMIN"))).containsExactly("ADMIN");
    }

    @Test
    void userCanManageObjectivesAndKeyResultsAndReadReportsOnly() {
        var capabilities = service.capabilities(List.of("ROLE_USER"));

        assertThat(capabilities.get("viewDashboard")).isTrue();
        assertThat(capabilities.get("viewStrategy")).isTrue();
        assertThat(capabilities.get("manageObjectives")).isTrue();
        assertThat(capabilities.get("manageKeyResults")).isTrue();
        assertThat(capabilities.get("viewReports")).isTrue();
        assertThat(capabilities.get("viewPresentation")).isTrue();
        assertThat(capabilities.get("manageCatalogs")).isFalse();
        assertThat(capabilities.get("manageStrategicBets")).isFalse();
        assertThat(capabilities.get("manageGoals")).isFalse();
        assertThat(capabilities.get("viewProjects")).isFalse();
        assertThat(capabilities.get("createProjects")).isFalse();
        assertThat(capabilities.get("viewConsistency")).isFalse();
        assertThat(capabilities.get("viewAuditLogs")).isFalse();
    }

    @Test
    void legacyInstitutionalRolesCollapseIntoUser() {
        assertThat(service.normalizeToList(List.of("DECANO", "DIRECTOR_ESCUELA", "JEFE_DPTO", "PROFESOR")))
                .containsExactly("USER");
        assertThat(service.normalizeToList(List.of("director", "jefe", "tutor", "usuario")))
                .containsExactly("USER");
    }

    @Test
    void normalizesRolesByTrimmingUppercasingRemovingPrefixAndDeduplicating() {
        assertThat(service.normalizeToList(Arrays.asList(" role_admin ", "ADMIN", "", " user ", null)))
                .containsExactly("ADMIN", "USER");
    }

    @Test
    void nullAndUnknownRolesReceiveNoCapabilities() {
        var anonymous = service.capabilities(null);
        var unknown = service.capabilities(List.of("ROLE_INVITADO"));

        assertThat(anonymous).allSatisfy((key, value) -> assertThat(value).isFalse());
        assertThat(unknown).allSatisfy((key, value) -> assertThat(value).isFalse());
    }

    @Test
    void onlyAdminCanChangeProjectStatus() {
        assertThat(service.canChangeProjectStatus(ProjectStatus.ACTIVO, ProjectStatus.FINALIZADO, List.of("ADMIN"))).isTrue();
        assertThat(service.canChangeProjectStatus(ProjectStatus.ARCHIVADO, ProjectStatus.ACTIVO, List.of("ADMIN"))).isTrue();
        assertThat(service.canChangeProjectStatus(ProjectStatus.ACTIVO, ProjectStatus.FINALIZADO, List.of("USER"))).isFalse();
        assertThat(service.canChangeProjectStatus(null, ProjectStatus.ACTIVO, List.of("ADMIN"))).isFalse();
    }
}
