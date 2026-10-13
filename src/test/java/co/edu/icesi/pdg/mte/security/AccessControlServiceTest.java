package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.project.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessControlServiceTest {
    private final AccessControlService service = new AccessControlService();

    @Test
    void exposesExactlyThreeSupportedRoles() {
        assertThat(AccessControlService.SUPPORTED_ROLES).containsExactly("ADMIN", "MANAGER", "CONTRIBUTOR");
    }

    @Test
    void adminReceivesEveryCapability() {
        var capabilities = service.capabilities(List.of("ROLE_ADMIN"));

        assertThat(capabilities).allSatisfy((key, value) -> assertThat(value).isTrue());
        assertThat(service.normalizeToList(List.of("ROLE_ADMIN"))).containsExactly("ADMIN");
    }

    @Test
    void managerCanManageStrategyAndProjectsButNotPlatformAdministration() {
        var capabilities = service.capabilities(List.of("ROLE_MANAGER"));

        assertThat(capabilities.get("viewDashboard")).isTrue();
        assertThat(capabilities.get("manageStrategicBets")).isTrue();
        assertThat(capabilities.get("manageKeyResults")).isTrue();
        assertThat(capabilities).doesNotContainKey("manageObjectives");
        assertThat(capabilities.get("createProjects")).isTrue();
        assertThat(capabilities.get("changeProjectStatus")).isTrue();
        assertThat(capabilities.get("viewReports")).isTrue();
        assertThat(capabilities.get("manageCatalogs")).isFalse();
        assertThat(capabilities.get("syncExternalProjects")).isFalse();
        assertThat(capabilities.get("viewAuditLogs")).isFalse();
    }

    @Test
    void contributorCanConsultAndRegisterProgressOnly() {
        var capabilities = service.capabilities(List.of("ROLE_CONTRIBUTOR"));

        assertThat(capabilities.get("viewDashboard")).isTrue();
        assertThat(capabilities.get("viewStrategy")).isTrue();
        assertThat(capabilities.get("viewProjects")).isTrue();
        assertThat(capabilities.get("registerProjectProgress")).isTrue();
        assertThat(capabilities.get("viewPresentation")).isTrue();
        assertThat(capabilities.get("manageKeyResults")).isFalse();
        assertThat(capabilities.get("createProjects")).isFalse();
        assertThat(capabilities.get("updateProjects")).isFalse();
        assertThat(capabilities.get("linkProjectsToKeyResults")).isFalse();
        assertThat(capabilities.get("viewReports")).isFalse();
    }

    @Test
    void legacyInstitutionalRolesMapToCanonicalLevels() {
        assertThat(service.normalizeToList(List.of("ADMINISTRADOR"))).containsExactly("ADMIN");
        assertThat(service.normalizeToList(List.of("DECANO", "DIRECTOR_ESCUELA", "JEFE_DPTO", "USER")))
                .containsExactly("MANAGER");
        assertThat(service.normalizeToList(List.of("PROFESOR", "TUTOR")))
                .containsExactly("CONTRIBUTOR");
    }

    @Test
    void normalizesRolesByTrimmingUppercasingRemovingPrefixAndDeduplicating() {
        assertThat(service.normalizeToList(Arrays.asList(" role_admin ", "ADMIN", "", " manager ", "ROLE_CONTRIBUTOR", null)))
                .containsExactly("ADMIN", "MANAGER", "CONTRIBUTOR");
    }

    @Test
    void nullAndUnknownRolesReceiveNoCapabilities() {
        var anonymous = service.capabilities(null);
        var unknown = service.capabilities(List.of("ROLE_INVITADO"));

        assertThat(anonymous).allSatisfy((key, value) -> assertThat(value).isFalse());
        assertThat(unknown).allSatisfy((key, value) -> assertThat(value).isFalse());
    }

    @Test
    void projectStatusTransitionsRespectRoleBoundaries() {
        assertThat(service.canChangeProjectStatus(ProjectStatus.ARCHIVADO, ProjectStatus.ACTIVO, List.of("ADMIN"))).isTrue();
        assertThat(service.canChangeProjectStatus(ProjectStatus.ACTIVO, ProjectStatus.FINALIZADO, List.of("MANAGER"))).isTrue();
        assertThat(service.canChangeProjectStatus(ProjectStatus.FINALIZADO, ProjectStatus.ARCHIVADO, List.of("MANAGER"))).isFalse();
        assertThat(service.canChangeProjectStatus(ProjectStatus.ACTIVO, ProjectStatus.FINALIZADO, List.of("CONTRIBUTOR"))).isFalse();
        assertThat(service.canChangeProjectStatus(null, ProjectStatus.ACTIVO, List.of("ADMIN"))).isFalse();
    }

    @Test
    void managerStatusTransitionMatrixIsExplicitAndNullSafe() {
        assertThat(service.canChangeProjectStatus(ProjectStatus.BORRADOR, null, List.of("MANAGER"))).isFalse();
        assertThat(service.canChangeProjectStatus(ProjectStatus.BORRADOR, ProjectStatus.ACTIVO, List.of("MANAGER"))).isTrue();
        assertThat(service.canChangeProjectStatus(ProjectStatus.BORRADOR, ProjectStatus.SUSPENDIDO, List.of("MANAGER"))).isTrue();
        assertThat(service.canChangeProjectStatus(ProjectStatus.BORRADOR, ProjectStatus.FINALIZADO, List.of("MANAGER"))).isFalse();
        assertThat(service.canChangeProjectStatus(ProjectStatus.ACTIVO, ProjectStatus.SUSPENDIDO, List.of("MANAGER"))).isTrue();
        assertThat(service.canChangeProjectStatus(ProjectStatus.ACTIVO, ProjectStatus.ACTIVO, List.of("MANAGER"))).isFalse();
        assertThat(service.canChangeProjectStatus(ProjectStatus.SUSPENDIDO, ProjectStatus.ACTIVO, List.of("MANAGER"))).isTrue();
        assertThat(service.canChangeProjectStatus(ProjectStatus.SUSPENDIDO, ProjectStatus.FINALIZADO, List.of("MANAGER"))).isFalse();
        assertThat(service.canChangeProjectStatus(ProjectStatus.ARCHIVADO, ProjectStatus.ACTIVO, List.of("MANAGER"))).isFalse();
        assertThat(service.canChangeProjectStatus(ProjectStatus.ACTIVO, ProjectStatus.FINALIZADO, null)).isFalse();
    }
}
