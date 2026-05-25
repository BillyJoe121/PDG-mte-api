package co.edu.icesi.pdg.mte.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthContextControllerTest {
    private final AuthContextController controller = new AuthContextController(new AccessControlService());

    @Test
    void returnsAnonymousContextWhenAuthenticationIsMissing() {
        var response = controller.me(null);

        assertThat(response.user().externalUserId()).isNull();
        assertThat(response.user().roles()).isEmpty();
        assertThat(response.supportedRoles()).containsExactly(
                "ADMIN",
                "DECANO",
                "DIRECTOR_ESCUELA",
                "JEFE_DPTO",
                "PROFESOR"
        );
        assertThat(response.capabilities()).allSatisfy((key, value) -> assertThat(value).isFalse());
    }

    @Test
    void returnsAnonymousContextWhenPrincipalIsNotExternalUserContext() {
        var authentication = new UsernamePasswordAuthenticationToken("plain-user", null, List.of());

        var response = controller.me(authentication);

        assertThat(response.user().username()).isNull();
        assertThat(response.user().roles()).isEmpty();
        assertThat(response.capabilities().get("viewDashboard")).isFalse();
    }

    @Test
    void normalizesExternalUserRolesAndCalculatesCapabilities() {
        ExternalUserContext context = new ExternalUserContext(
                11L,
                "jefe",
                "jefe@icesi.edu.co",
                List.of(" role_jefe_dpto ", "PROFESOR"),
                List.of("MTE_READ"),
                44L,
                "Jefe Dpto",
                "Departamento de Computaci\u00f3n y Sistemas inteligentes."
        );
        var authentication = new UsernamePasswordAuthenticationToken(context, null, List.of());

        var response = controller.me(authentication);

        assertThat(response.user().externalUserId()).isEqualTo(11L);
        assertThat(response.user().roles()).containsExactly("JEFE_DPTO", "PROFESOR");
        assertThat(response.user().permissions()).containsExactly("MTE_READ");
        assertThat(response.user().externalProfessorId()).isEqualTo(44L);
        assertThat(response.user().professorName()).isEqualTo("Jefe Dpto");
        assertThat(response.user().departmentName()).isEqualTo("Departamento de Computaci\u00f3n y Sistemas inteligentes.");
        assertThat(response.capabilities().get("linkProjectsToKeyResults")).isTrue();
        assertThat(response.capabilities().get("viewAuditLogs")).isFalse();
    }
}
