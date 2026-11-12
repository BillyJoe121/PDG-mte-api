package co.edu.icesi.pdg.mte.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TrayectoriaProjectClientEdgeCasesTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void emptyResponseBodyYieldsNoProjects() throws Exception {
        startServer(204, null);

        assertThat(externalClient().fetchProjects("Bearer token")).isEmpty();
    }

    @Test
    void skipsBlankNullAndMissingFieldsAndParticipantsWithoutName() throws Exception {
        startServer(200, """
                {
                  "id": true,
                  "nombre": "Proyecto parcial",
                  "descripcion": null,
                  "estado": "   ",
                  "participantes": [{"cargo": "sin nombre"}, {"nombre": "Tutor Uno"}]
                }
                """);

        var projects = externalClient().fetchProjects("Bearer token");

        assertThat(projects).hasSize(1);
        var project = projects.get(0);
        assertThat(project.externalProjectId()).isNull();
        assertThat(project.name()).isEqualTo("Proyecto parcial");
        assertThat(project.description()).isNull();
        assertThat(project.status()).isNull();
        assertThat(project.tutors()).containsExactly("Tutor Uno");
    }

    @Test
    void projectWithoutAnyKnownIdentifierKeepsNullExternalId() throws Exception {
        startServer(200, "{\"nombre\": \"Sin identificador\"}");

        var projects = externalClient().fetchProjects("Bearer token");

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).externalProjectId()).isNull();
        assertThat(projects.get(0).tutors()).isEmpty();
    }

    private TrayectoriaProjectClient externalClient() {
        IntegrationProperties properties = new IntegrationProperties();
        properties.setMode("external");
        properties.setExternalBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setProjectsPath("/proyectos");
        return new TrayectoriaProjectClient(properties, WebClient.builder());
    }

    private void startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/proyectos", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (body == null) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }
}
