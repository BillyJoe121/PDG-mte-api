package co.edu.icesi.pdg.mte.e2e;

import co.edu.icesi.pdg.mte.integration.TrayectoriaProjectClient;
import co.edu.icesi.pdg.mte.support.TrayectoriaFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuditTrailE2ETest {

    @MockitoBean
    private TrayectoriaProjectClient trayectoriaProjectClient;

    @BeforeEach
    void stubTrayectoria() {
        when(trayectoriaProjectClient.fetchProjects(any())).thenReturn(TrayectoriaFixtures.projects());
    }
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void recordsFiltersAndSummarizesSystemEventsIncludingKeyResultChanges() throws Exception {
        Instant from = Instant.now().minusSeconds(60);
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long departmentId = firstId("/api/v1/departments");

        JsonNode unit = post("/api/v1/measurement-units", """
                {"name": "Unidad audit %d", "type": "NUMERICA", "description": "Auditable"}
                """.formatted(SEQUENCE.incrementAndGet()), 201);
        put("/api/v1/measurement-units/" + unit.get("id").asLong(), """
                {"name": "Unidad audit editada %d", "type": "PORCENTAJE", "description": "Auditable"}
                """.formatted(SEQUENCE.incrementAndGet()), 200);
        patch("/api/v1/measurement-units/" + unit.get("id").asLong() + "/active", """
                {"active": false}
                """, 200);
        delete("/api/v1/measurement-units/" + unit.get("id").asLong(), 204);

        Long betId = post("/api/v1/strategic-bets", """
                {
                  "name": "Apuesta audit %d",
                  "description": "Apuesta auditable",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(SEQUENCE.incrementAndGet()), 201).get("id").asLong();
        Long goalId = post("/api/v1/goals", """
                {
                  "name": "Meta audit %d",
                  "description": "Meta auditable",
                  "expectedValue": 100,
                  "measurementUnitId": %d
                }
                """.formatted(SEQUENCE.incrementAndGet(), unitId), 201).get("id").asLong();
        post("/api/v1/goals/" + goalId + "/periods/" + periodId, "", 200);

        String keyResultPayload = """
                {
                  "name": "KR audit %d",
                  "description": "KR auditable",
                  "metric": "Cobertura",
                  "baseValue": 0,
                  "targetValue": 100,
                  "measurementUnitId": %d,
                  "academicPeriodId": %d,
                  "goalIds": [%d],
                  "strategicBetIds": [%d]
                }
                """;
        Long keyResultId = post("/api/v1/key-results",
                keyResultPayload.formatted(SEQUENCE.incrementAndGet(), unitId, periodId, goalId, betId), 201)
                .get("id").asLong();
        put("/api/v1/key-results/" + keyResultId,
                keyResultPayload.formatted(SEQUENCE.incrementAndGet(), unitId, periodId, goalId, betId), 200);

        JsonNode project = post("/api/v1/projects", """
                {
                  "name": "Proyecto audit %d",
                  "description": "Proyecto auditable",
                  "type": "INVESTIGACION",
                  "departmentId": %d,
                  "keyResultId": %d,
                  "status": "ACTIVO",
                  "startPeriod": "2026-1",
                  "endPeriod": "2026-1"
                }
                """.formatted(SEQUENCE.incrementAndGet(), departmentId, keyResultId), 201);
        Long projectId = project.get("id").asLong();
        JsonNode link = post("/api/v1/project-key-result-links", """
                {"projectId": %d, "keyResultId": %d, "contributionWeight": 50, "contributionType": "DIRECTA"}
                """.formatted(projectId, keyResultId), 201);
        post("/api/v1/projects/" + projectId + "/progress", """
                {"progressPercent": 45, "comment": "Avance auditado", "milestones": "Hito 1"}
                """, 201);
        patch("/api/v1/projects/" + projectId + "/status", """
                {"status": "FINALIZADO"}
                """, 200);
        delete("/api/v1/project-key-result-links/" + link.get("id").asLong(), 204);
        post("/api/v1/projects/sync/trayectoria", "", 200);

        Instant to = Instant.now().plusSeconds(60);
        JsonNode logs = getWithDates("/api/v1/audit-logs", from, to, 200);
        JsonNode projectCreates = get("/api/v1/audit-logs?action=CREATE&entityType=project&entityId=" + projectId, 200);
        JsonNode linkRemoved = get("/api/v1/audit-logs?action=LINK_REMOVED&entityType=PROJECT_KEY_RESULT_LINK", 200);
        JsonNode summary = getWithDates("/api/v1/audit-logs/summary", from, to, 200);
        JsonNode keyResultLogs = get("/api/v1/audit-logs?entityType=KEY_RESULT&entityId=" + keyResultId, 200);

        assertThat(logs.findValues("action").stream().map(JsonNode::asText))
                .contains("CREATE", "DELETE", "UPDATE", "STATUS_CHANGE", "PROGRESS_REGISTERED", "LINK_CREATED", "LINK_REMOVED", "EXTERNAL_SYNC");
        assertThat(projectCreates).hasSize(1);
        assertThat(linkRemoved.findValues("action").stream().map(JsonNode::asText)).contains("LINK_REMOVED");
        assertThat(summary.get("totalEvents").asLong()).isGreaterThanOrEqualTo(8);
        assertThat(summary.get("byAction").findValues("key").stream().map(JsonNode::asText))
                .contains("CREATE", "DELETE", "UPDATE", "STATUS_CHANGE", "PROGRESS_REGISTERED", "LINK_CREATED", "LINK_REMOVED", "EXTERNAL_SYNC");
        assertThat(summary.get("byActor")).isNotEmpty();
        assertThat(summary.get("trend")).isNotEmpty();
        assertThat(summary.get("trend").get(0).hasNonNull("date")).isTrue();
        assertThat(summary.get("trend").get(0).hasNonNull("count")).isTrue();
        assertThat(keyResultLogs.findValues("action").stream().map(JsonNode::asText)).contains("CREATE", "UPDATE");

        getWithDates("/api/v1/audit-logs", to, from, 400);
    }

    private Long firstId(String url) throws Exception {
        return get(url, 200).get(0).get("id").asLong();
    }

    private JsonNode get(String url, int expectedStatus) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.get(url))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode getWithDates(String url, Instant from, Instant to, int expectedStatus) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.get(url)
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode post(String url, String payload, int expectedStatus) throws Exception {
        var request = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON);
        if (!payload.isBlank()) {
            request.content(payload);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode put(String url, String payload, int expectedStatus) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode patch(String url, String payload, int expectedStatus) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.patch(url).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private void delete(String url, int expectedStatus) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete(url))
                .andExpect(status().is(expectedStatus));
    }

}
