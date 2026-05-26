package co.edu.icesi.pdg.mte.integration;

import co.edu.icesi.pdg.mte.common.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrayectoriaProjectClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returnsMockProjectsWhenModeIsMock() {
        IntegrationProperties properties = new IntegrationProperties();
        properties.setMode("mock");

        var projects = new TrayectoriaProjectClient(properties, RestClient.builder()).fetchProjects(null);

        assertThat(projects).hasSize(2);
        assertThat(projects.get(0).externalProjectId()).isEqualTo(9001L);
    }

    @Test
    void fetchesExternalProjectArrayAndMapsCommonFields() throws Exception {
        startServer(200, """
                [
                  {
                    "id_proyecto": 11,
                    "nombre_proyecto": "Proyecto remoto",
                    "descripcion": "Descripcion remota",
                    "estado": "EN_CURSO",
                    "tipo": "investigacion",
                    "departamento": "Departamento de Computación y Sistemas inteligentes.",
                    "periodo_inicio": "2026-1",
                    "periodo_fin": "2026-2",
                    "fecha_inicio": "2026-01-01",
                    "fecha_fin": "2026-06-01",
                    "participantes": [{"nombre": "Tutor Remoto"}]
                  }
                ]
                """);

        var projects = externalClient().fetchProjects("Bearer token");

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).externalProjectId()).isEqualTo(11L);
        assertThat(projects.get(0).tutors()).containsExactly("Tutor Remoto");
    }

    @Test
    void parsesWrappedPayloadsAndFallbackNames() throws Exception {
        startServer(200, """
                {
                  "content": [
                    {
                      "id": "12",
                      "name": "Proyecto content",
                      "description": "Descripcion",
                      "startDate": "2026-02-01",
                      "endDate": "2026-05-01",
                      "tutors": [{"name": "Tutor Content"}]
                    }
                  ]
                }
                """);

        var projects = externalClient().fetchProjects("Bearer token");

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).externalProjectId()).isEqualTo(12L);
        assertThat(projects.get(0).name()).isEqualTo("Proyecto content");
        assertThat(projects.get(0).tutors()).containsExactly("Tutor Content");
    }

    @Test
    void parsesDataItemsSingleObjectAndInvalidIds() throws Exception {
        startServer(200, """
                {
                  "data": [
                    {"idProyecto": "no-numerico", "nombre": "Proyecto data"},
                    {"externalProjectId": 13, "nombreProyecto": "Proyecto id externo"}
                  ]
                }
                """);

        var dataProjects = externalClient().fetchProjects("Bearer token");

        assertThat(dataProjects).hasSize(2);
        assertThat(dataProjects.get(0).externalProjectId()).isNull();
        assertThat(dataProjects.get(1).externalProjectId()).isEqualTo(13L);
        tearDown();

        startServer(200, """
                {"items": [{"id": 14, "nombre": "Proyecto items"}]}
                """);
        var itemProjects = externalClient().fetchProjects("Bearer token");
        assertThat(itemProjects).hasSize(1);
        assertThat(itemProjects.get(0).externalProjectId()).isEqualTo(14L);
        tearDown();

        startServer(200, """
                {"id": 15, "nombre": "Proyecto unico", "tutors": []}
                """);
        var singleProject = externalClient().fetchProjects("Bearer token");
        assertThat(singleProject).hasSize(1);
        assertThat(singleProject.get(0).externalProjectId()).isEqualTo(15L);
    }

    @Test
    void sadPathsRejectMissingBearerAndRemoteFailures() throws Exception {
        IntegrationProperties properties = new IntegrationProperties();
        properties.setMode("external");
        properties.setExternalBaseUrl("http://localhost:1");
        TrayectoriaProjectClient client = new TrayectoriaProjectClient(properties, RestClient.builder());

        assertThatThrownBy(() -> client.fetchProjects(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.fetchProjects("")).isInstanceOf(BusinessException.class);

        startServer(500, "{\"message\":\"boom\"}");
        assertThatThrownBy(() -> externalClient().fetchProjects("Bearer token")).isInstanceOf(RuntimeException.class);
    }

    private TrayectoriaProjectClient externalClient() {
        IntegrationProperties properties = new IntegrationProperties();
        properties.setMode("external");
        properties.setExternalBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setProjectsPath("/proyectos");
        return new TrayectoriaProjectClient(properties, RestClient.builder());
    }

    private void startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/proyectos", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
}
