package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.common.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalAuthClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void introspectsExternalUserWithProfessorContext() throws Exception {
        startServer(200, """
                {
                  "id_usuario": 7,
                  "username": "directora",
                  "email": "directora@icesi.edu.co",
                  "roles": ["DIRECTOR_ESCUELA"],
                  "permisos": ["MTE_READ"],
                  "profesor": {
                    "id_profesor": 55,
                    "nombre": "Ana",
                    "apellido": "Ruiz",
                    "departamento": "Departamento de Computación y Sistemas inteligentes."
                  }
                }
                """);

        ExternalUserContext context = client().introspect("Bearer token");

        assertThat(context.externalUserId()).isEqualTo(7L);
        assertThat(context.roles()).containsExactly("DIRECTOR_ESCUELA");
        assertThat(context.permissions()).containsExactly("MTE_READ");
        assertThat(context.externalProfessorId()).isEqualTo(55L);
        assertThat(context.professorName()).isEqualTo("Ana Ruiz");
        assertThat(context.departmentName()).isEqualTo("Departamento de Computaci\u00f3n y Sistemas inteligentes.");
    }

    @Test
    void introspectsExternalUserWhenProfessorPayloadIsMissing() throws Exception {
        startServer(200, """
                {
                  "id_usuario": "8",
                  "username": "admin",
                  "email": "admin@icesi.edu.co",
                  "roles": [],
                  "permisos": []
                }
                """);

        ExternalUserContext context = client().introspect("Bearer token");

        assertThat(context.externalUserId()).isEqualTo(8L);
        assertThat(context.professorName()).isNull();
        assertThat(context.roles()).isEmpty();
    }

    @Test
    void toleratesNonListRolesAndPartialProfessorName() throws Exception {
        startServer(200, """
                {
                  "username": "profesor",
                  "roles": "ADMIN",
                  "permisos": "MTE_READ",
                  "profesor": {
                    "apellido": "Soloapellido"
                  }
                }
                """);

        ExternalUserContext context = client().introspect("Bearer token");

        assertThat(context.externalUserId()).isNull();
        assertThat(context.roles()).isEmpty();
        assertThat(context.permissions()).isEmpty();
        assertThat(context.professorName()).isEqualTo("Soloapellido");
    }

    @Test
    void joinsProfessorNameWhenOnlyNameIsPresent() throws Exception {
        startServer(200, """
                {
                  "id_usuario": 9,
                  "username": "profesor",
                  "roles": ["PROFESOR"],
                  "profesor": {
                    "nombre": "Camila"
                  }
                }
                """);

        ExternalUserContext context = client().introspect("Bearer token");

        assertThat(context.professorName()).isEqualTo("Camila");
        assertThat(context.departmentName()).isNull();
    }

    @Test
    void rejectsMalformedNumericIdentifiersFromExternalProvider() throws Exception {
        startServer(200, """
                {
                  "id_usuario": "no-numerico",
                  "username": "admin",
                  "roles": ["ADMIN"]
                }
                """);

        assertThatThrownBy(() -> client().introspect("Bearer token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Token externo invalido");
    }

    @Test
    void rejectsNullEntriesInsideExternalRoleList() throws Exception {
        startServer(200, """
                {
                  "id_usuario": 10,
                  "username": "admin",
                  "roles": ["ADMIN", null]
                }
                """);

        assertThatThrownBy(() -> client().introspect("Bearer token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Token externo invalido");
    }

    @Test
    void rejectsExternalServiceErrors() throws Exception {
        startServer(500, """
                {"message": "boom"}
                """);

        assertThatThrownBy(() -> client().introspect("Bearer token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Token externo invalido");
    }

    @Test
    void rejectsEmptyExternalResponse() throws Exception {
        startServer(204, "");

        assertThatThrownBy(() -> client().introspect("Bearer token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No fue posible validar");
    }

    @Test
    void authPropertiesDetectsExternalModeCaseInsensitively() {
        assertThat(new AuthProperties("external", "http://localhost", "/auth/me").externalMode()).isTrue();
        assertThat(new AuthProperties("EXTERNAL", "http://localhost", "/auth/me").externalMode()).isTrue();
        assertThat(new AuthProperties("mock", "http://localhost", "/auth/me").externalMode()).isFalse();
    }

    private ExternalAuthClient client() {
        return new ExternalAuthClient(
                WebClient.builder(),
                new AuthProperties("external", "http://localhost:" + server.getAddress().getPort(), "/auth/me")
        );
    }

    private void startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/auth/me", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
}
