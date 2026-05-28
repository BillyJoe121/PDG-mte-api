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
        JsonNode listedByQuarterInsideRange = doGet("/api/v1/projects?period=2026-Q3");
        assertThat(ids(listedByQuarterInsideRange)).contains(projectId);
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
        mockMvc.perform(get("/api/v1/projects").param("period", "2026-X"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/audit-logs").param("action", "INVALIDO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsProjectWithImmediateKeyResultLinkAndRejectsInvalidImmediateLinks() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long departmentId = firstId("/api/v1/departments");
        Long strategicBetId = createStrategicBet("Apuesta proyecto inmediato " + SEQUENCE.incrementAndGet());
        Long goalId = createGoal("Meta proyecto inmediato " + SEQUENCE.incrementAndGet(), unitId);
        Long keyResultId = doPost("/api/v1/objectives", objectivePayload(
                "Objetivo proyecto inmediato " + SEQUENCE.incrementAndGet(),
                departmentId,
                periodId,
                goalId,
                strategicBetId,
                unitId
        ), 201).get("keyResults").get(0).get("id").asLong();

        JsonNode created = doPost("/api/v1/projects", projectPayloadWithImmediateLink(
                "Proyecto inmediato " + SEQUENCE.incrementAndGet(),
                departmentId,
                keyResultId
        ), 201);
        Long projectId = created.get("id").asLong();

        assertThat(created.get("linkedKeyResults")).hasSize(1);
        assertThat(created.get("linkedKeyResults").get(0).get("keyResultId").asLong()).isEqualTo(keyResultId);

        JsonNode detail = doGet("/api/v1/projects/" + projectId + "/detail");
        JsonNode chainBefore = doGet("/api/v1/projects/" + projectId + "/contribution-chain");
        assertThat(detail.get("linkedKeyResults")).hasSize(1);
        assertThat(chainBefore.get("impacts").get(0).get("appliedContribution").decimalValue()).isEqualByComparingTo("0.00");

        doPost("/api/v1/projects/" + projectId + "/progress", """
                {
                  "progressPercent": 100,
                  "comment": "Proyecto listo para aportar al KR"
                }
                """, 201);
        JsonNode chainWithProgress = doGet("/api/v1/projects/" + projectId + "/contribution-chain");
        assertThat(chainWithProgress.get("impacts").get(0).get("appliedContribution").decimalValue()).isEqualByComparingTo("45.00");

        doPatch("/api/v1/projects/" + projectId + "/status", """
                {"status": "FINALIZADO"}
                """, 200);
        JsonNode chainAfter = doGet("/api/v1/projects/" + projectId + "/contribution-chain");
        assertThat(chainAfter.get("impacts").get(0).get("appliedContribution").decimalValue()).isEqualByComparingTo("45.00");

        doPost("/api/v1/projects", projectPayloadWithImmediateLink(
                "Proyecto KR inexistente " + SEQUENCE.incrementAndGet(),
                departmentId,
                99999L
        ), 404);
        doPost("/api/v1/projects", """
                {
                  "name": "Proyecto link invalido %d",
                  "description": "Debe fallar por payload incompleto.",
                  "type": "INVESTIGACION",
                  "departmentId": %d,
                  "status": "ACTIVO",
                  "startPeriod": "2026-1",
                  "keyResultLinks": [
                    {"keyResultId": %d, "contributionWeight": 40}
                  ]
                }
                """.formatted(SEQUENCE.incrementAndGet(), departmentId, keyResultId), 400);
    }

    @Test
    void managesProjectKeyResultLinksWarningsDuplicatesAndLogicalUnlink() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long departmentId = firstId("/api/v1/departments");
        Long strategicBetId = createStrategicBet("Apuesta vinculos " + SEQUENCE.incrementAndGet());
        Long goalId = createGoal("Meta vinculos " + SEQUENCE.incrementAndGet(), unitId);
        Long keyResultId = doPost("/api/v1/objectives", objectivePayload(
                "Objetivo vinculos " + SEQUENCE.incrementAndGet(),
                departmentId,
                periodId,
                goalId,
                strategicBetId,
                unitId
        ), 201).get("keyResults").get(0).get("id").asLong();
        Long firstProjectId = doPost("/api/v1/projects", projectPayload("Proyecto vinculo A " + SEQUENCE.incrementAndGet()), 201)
                .get("id").asLong();
        Long secondProjectId = doPost("/api/v1/projects", projectPayload("Proyecto vinculo B " + SEQUENCE.incrementAndGet()), 201)
                .get("id").asLong();

        JsonNode firstLink = doPost("/api/v1/project-key-result-links", """
                {"projectId": %d, "keyResultId": %d, "contributionWeight": 70, "contributionType": "DIRECTA"}
                """.formatted(firstProjectId, keyResultId), 201);
        JsonNode secondLink = doPost("/api/v1/project-key-result-links", """
                {"projectId": %d, "keyResultId": %d, "contributionWeight": 40, "contributionType": "INDIRECTA"}
                """.formatted(secondProjectId, keyResultId), 201);

        assertThat(firstLink.get("overweightWarning").asBoolean()).isFalse();
        assertThat(secondLink.get("overweightWarning").asBoolean()).isTrue();
        assertThat(secondLink.get("totalWeightForKeyResult").decimalValue()).isEqualByComparingTo("110.00");
        assertThat(doGet("/api/v1/project-key-result-links?projectId=" + firstProjectId)).hasSize(1);
        assertThat(doGet("/api/v1/project-key-result-links?keyResultId=" + keyResultId)).hasSize(2);
        assertThat(doGet("/api/v1/project-key-result-links?projectId=" + firstProjectId + "&keyResultId=99999")).isEmpty();

        doPost("/api/v1/project-key-result-links", """
                {"projectId": %d, "keyResultId": %d, "contributionWeight": 10, "contributionType": "SOPORTE"}
                """.formatted(firstProjectId, keyResultId), 409);
        doPost("/api/v1/project-key-result-links", """
                {"projectId": %d, "keyResultId": %d, "contributionWeight": 10}
                """.formatted(firstProjectId, keyResultId), 400);

        doDelete("/api/v1/project-key-result-links/" + firstLink.get("id").asLong(), 204);
        JsonNode remainingByProject = doGet("/api/v1/project-key-result-links?projectId=" + firstProjectId);
        JsonNode removedAudit = doGet("/api/v1/audit-logs?action=LINK_REMOVED&entityType=PROJECT_KEY_RESULT_LINK&entityId="
                + firstLink.get("id").asLong());

        assertThat(remainingByProject).isEmpty();
        assertThat(removedAudit).hasSize(1);
        doDelete("/api/v1/project-key-result-links/99999", 404);
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

    private String projectPayloadWithImmediateLink(String name, Long departmentId, Long keyResultId) {
        return """
                {
                  "name": "%s",
                  "description": "Proyecto creado con vinculo inmediato a KR.",
                  "type": "INVESTIGACION",
                  "departmentId": %d,
                  "status": "ACTIVO",
                  "startPeriod": "2026-1",
                  "endPeriod": "2026-2",
                  "tutors": ["Tutora inmediata"],
                  "keyResultLinks": [
                    {"keyResultId": %d, "contributionWeight": 45, "contributionType": "DIRECTA"}
                  ]
                }
                """.formatted(name, departmentId, keyResultId);
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

    private Long createStrategicBet(String name) throws Exception {
        return doPost("/api/v1/strategic-bets", """
                {
                  "name": "%s",
                  "description": "Apuesta para pruebas de proyectos.",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(name), 201).get("id").asLong();
    }

    private Long createGoal(String name, Long unitId) throws Exception {
        return doPost("/api/v1/goals", """
                {
                  "name": "%s",
                  "description": "Meta para pruebas de proyectos.",
                  "expectedValue": 100,
                  "measurementUnitId": %d
                }
                """.formatted(name, unitId), 201).get("id").asLong();
    }

    private String objectivePayload(String name, Long departmentId, Long periodId, Long goalId, Long strategicBetId, Long unitId) {
        return """
                {
                  "name": "%s",
                  "description": "Objetivo para vincular proyectos.",
                  "departmentId": %d,
                  "academicPeriodId": %d,
                  "goalId": %d,
                  "strategicBetId": %d,
                  "keyResults": [{
                    "name": "KR proyecto inmediato",
                    "description": "KR para vinculo inmediato",
                    "metric": "Porcentaje",
                    "baseValue": 0,
                    "targetValue": 100,
                    "measurementUnitId": %d
                  }]
                }
                """.formatted(name, departmentId, periodId, goalId, strategicBetId, unitId);
    }

    private Long firstId(String url) throws Exception {
        return doGet(url).get(0).get("id").asLong();
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

    private void doDelete(String url, int expectedStatus) throws Exception {
        mockMvc.perform(delete(url))
                .andExpect(status().is(expectedStatus));
    }

    private java.util.List<Long> ids(JsonNode nodes) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        nodes.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }
}
