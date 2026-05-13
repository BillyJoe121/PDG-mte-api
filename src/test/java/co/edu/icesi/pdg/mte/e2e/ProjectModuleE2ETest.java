package co.edu.icesi.pdg.mte.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "mte.integration.trayectoria.mode=mock")
@AutoConfigureMockMvc
class ProjectModuleE2ETest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void happyPathManagesLocalProjectsAndSyncsExternalProjects() throws Exception {
        JsonNode sync = doPost("/api/v1/projects/sync/trayectoria", "", 200);
        assertThat(sync.get("imported").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(sync.get("failed").asInt()).isZero();

        JsonNode created = doPost("/api/v1/projects", projectPayload("Proyecto E2E " + SEQUENCE.incrementAndGet()), 201);
        Long projectId = created.get("id").asLong();
        assertThat(created.get("origin").asText()).isEqualTo("LOCAL");

        JsonNode listed = doGet("/api/v1/projects?search=Proyecto E2E&type=INVESTIGACION&status=ACTIVO&period=2026-1&departmentId=1");
        assertThat(ids(listed)).contains(projectId);
        assertThat(doGet("/api/v1/projects")).isNotEmpty();
        mockMvc.perform(get("/api/v1/projects")
                        .param("search", "  ")
                        .param("period", "  "))
                .andExpect(status().isOk());

        JsonNode detail = doGet("/api/v1/projects/" + projectId);
        assertThat(detail.get("name").asText()).contains("Proyecto E2E");

        JsonNode updated = doPut("/api/v1/projects/" + projectId, updatePayload("Proyecto E2E actualizado"), 200);
        assertThat(updated.get("name").asText()).isEqualTo("Proyecto E2E actualizado");

        JsonNode statusUpdated = doPatch("/api/v1/projects/" + projectId + "/status", """
                {"status": "SUSPENDIDO"}
                """, 200);
        assertThat(statusUpdated.get("status").asText()).isEqualTo("SUSPENDIDO");

        JsonNode progress = doPost("/api/v1/projects/" + projectId + "/progress", """
                {
                  "progressPercent": 64.5,
                  "comment": "Avance probado por E2E",
                  "milestones": "CRUD y seguimiento funcionales"
                }
                """, 201);
        assertThat(progress.get("progressPercent").decimalValue()).isEqualByComparingTo("64.50");

        JsonNode history = doGet("/api/v1/projects/" + projectId + "/history");
        assertThat(history).hasSize(1);
        assertThat(doGet("/api/v1/projects/" + projectId).get("globalProgress").decimalValue()).isEqualByComparingTo("64.50");
        JsonNode fullDetail = doGet("/api/v1/projects/" + projectId + "/detail");
        assertThat(fullDetail.get("history")).hasSize(1);
        assertThat(fullDetail.get("kpis").get("progressEntries").asInt()).isEqualTo(1);
        assertThat(fullDetail.get("contributionChain").get("projectId").asLong()).isEqualTo(projectId);
        JsonNode audit = doGet("/api/v1/audit-logs?entityType=PROJECT&entityId=" + projectId);
        assertThat(audit.findValues("action").stream().map(JsonNode::asText))
                .contains("CREATE", "UPDATE", "STATUS_CHANGE", "PROGRESS_REGISTERED");
        assertThat(doGet("/api/v1/audit-logs?action=CREATE")).isNotEmpty();
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "   ")
                        .param("entityId", "   "))
                .andExpect(status().isOk());
    }

    @Test
    void sadPathsForProjectModuleReturnExpectedErrors() throws Exception {
        JsonNode created = doPost("/api/v1/projects", projectPayload("Proyecto sad " + SEQUENCE.incrementAndGet()), 201);
        Long projectId = created.get("id").asLong();

        doPost("/api/v1/projects", """
                {
                  "name": "",
                  "description": "Descripcion",
                  "type": "INVESTIGACION",
                  "departmentId": 1,
                  "status": "ACTIVO",
                  "startPeriod": "2026-1"
                }
                """, 400);
        doPost("/api/v1/projects", """
                {
                  "name": "Proyecto sin departamento",
                  "description": "Descripcion",
                  "type": "INVESTIGACION",
                  "departmentId": 99999,
                  "status": "ACTIVO",
                  "startPeriod": "2026-1"
                }
                """, 404);
        doPost("/api/v1/projects", """
                {
                  "name": "Proyecto periodo invalido",
                  "description": "Descripcion",
                  "type": "INVESTIGACION",
                  "departmentId": 1,
                  "status": "ACTIVO",
                  "startPeriod": "2026-X"
                }
                """, 400);
        doPost("/api/v1/projects", """
                {
                  "name": "Proyecto fechas invalidas",
                  "description": "Descripcion",
                  "type": "INVESTIGACION",
                  "departmentId": 1,
                  "status": "ACTIVO",
                  "startPeriod": "2026-1",
                  "startDate": "2026-06-01",
                  "endDate": "2026-01-01"
                }
                """, 400);
        doGet("/api/v1/projects/99999", 404);
        doPut("/api/v1/projects/99999", updatePayload("No existe"), 404);
        doPatch("/api/v1/projects/99999/status", """
                {"status": "ACTIVO"}
                """, 404);
        doPatch("/api/v1/projects/" + projectId + "/status", """
                {"status": null}
                """, 400);
        doPost("/api/v1/projects/99999/progress", """
                {"progressPercent": 10, "comment": "x"}
                """, 404);
        doGet("/api/v1/projects/99999/detail", 404);
        doPost("/api/v1/projects/" + projectId + "/progress", """
                {"progressPercent": 150, "comment": "x"}
                """, 400);
        doPost("/api/v1/projects/" + projectId + "/progress", """
                {"progressPercent": 10, "comment": ""}
                """, 400);
        doGet("/api/v1/projects/99999/history", 404);
        mockMvc.perform(get("/api/v1/projects").param("status", "INVALIDO"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/projects").param("type", "INVALIDO"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/audit-logs").param("action", "INVALIDO"))
                .andExpect(status().isBadRequest());
    }

    private String projectPayload(String name) {
        return """
                {
                  "name": "%s",
                  "description": "Proyecto creado en MSP para seguimiento independiente.",
                  "type": "INVESTIGACION",
                  "departmentId": 1,
                  "status": "ACTIVO",
                  "startPeriod": "2026-1",
                  "endPeriod": "2026-2",
                  "startDate": "2026-01-15",
                  "endDate": "2026-11-30",
                  "tutors": ["Tutora A", "Tutor B"]
                }
                """.formatted(name);
    }

    private String updatePayload(String name) {
        return """
                {
                  "name": "%s",
                  "description": "Proyecto actualizado en MSP.",
                  "type": "EXTENSION",
                  "departmentId": 1,
                  "startPeriod": "2026-1",
                  "endPeriod": "2026-2",
                  "startDate": "2026-01-20",
                  "endDate": "2026-12-01",
                  "actualEndDate": null,
                  "tutors": ["Tutora Actualizada"]
                }
                """.formatted(name);
    }

    private JsonNode doGet(String url) throws Exception {
        return doGet(url, 200);
    }

    private JsonNode doGet(String url, int expectedStatus) throws Exception {
        String body = mockMvc.perform(get(url))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode doPost(String url, String payload, int expectedStatus) throws Exception {
        var builder = post(url).contentType(MediaType.APPLICATION_JSON);
        if (!payload.isBlank()) {
            builder.content(payload);
        }
        String body = mockMvc.perform(builder)
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode doPut(String url, String payload, int expectedStatus) throws Exception {
        String body = mockMvc.perform(put(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode doPatch(String url, String payload, int expectedStatus) throws Exception {
        String body = mockMvc.perform(patch(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private java.util.List<Long> ids(JsonNode nodes) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        nodes.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }
}
