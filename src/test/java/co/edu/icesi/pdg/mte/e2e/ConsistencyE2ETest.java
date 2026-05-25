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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConsistencyE2ETest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesConsistencyFindingsAndCsvExport() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long departmentId = firstId("/api/v1/departments");
        Long betId = createStrategicBet();
        Long goalId = createGoal(unitId);
        JsonNode unlinkedObjective = doPost("/api/v1/objectives", objectivePayload("sin proyecto", departmentId, periodId, goalId, betId, unitId), 201);
        JsonNode projectObjective = doPost("/api/v1/objectives", objectivePayload("con proyecto", departmentId, periodId, goalId, betId, unitId), 201);
        Long projectKeyResultId = projectObjective.get("keyResults").get(0).get("id").asLong();

        Long projectId = createProject(departmentId, projectKeyResultId);

        JsonNode check = doGet("/api/v1/consistency/check?staleDays=15", 200);
        JsonNode highOnly = doGet("/api/v1/consistency/check?severity=ALTA", 200);
        JsonNode projectsOnly = doGet("/api/v1/consistency/check?module=PROYECTOS", 200);
        var csvResponse = mockMvc.perform(get("/api/v1/consistency/check/export.csv")
                        .param("module", "PROYECTOS"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        assertThat(check.get("summary").get("total").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(check.get("summary").get("high").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(check.get("summary").get("medium").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(check.findValues("type").stream().map(JsonNode::asText))
                .contains("KR_WITHOUT_ACTIVE_PROJECTS", "ACTIVE_PROJECT_WITHOUT_RECENT_PROGRESS");
        assertThat(check.findValues("entityCode").stream().map(JsonNode::asText))
                .contains("KR-" + unlinkedObjective.get("keyResults").get(0).get("id").asLong(), "PRJ-" + projectId);
        assertThat(highOnly.findValues("severity").stream().map(JsonNode::asText))
                .allMatch("ALTA"::equals);
        assertThat(projectsOnly.findValues("module").stream().map(JsonNode::asText))
                .allMatch("PROYECTOS"::equals);
        assertThat(csvResponse.getHeader("Content-Disposition")).contains("msp-consistency-findings.csv");
        assertThat(csvResponse.getContentAsString()).contains("id,type,severity,module", "ACTIVE_PROJECT_WITHOUT_RECENT_PROGRESS");
    }

    @Test
    void rejectsInvalidConsistencyFilters() throws Exception {
        doGet("/api/v1/consistency/check?severity=URGENTE", 400);
        doGet("/api/v1/consistency/check?module=FINANZAS", 400);
        doGet("/api/v1/consistency/check?staleDays=0", 400);
    }

    private Long createStrategicBet() throws Exception {
        return doPost("/api/v1/strategic-bets", """
                {
                  "name": "Apuesta consistencia %d",
                  "description": "Apuesta para consistencia.",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(SEQUENCE.incrementAndGet()), 201).get("id").asLong();
    }

    private Long createGoal(Long unitId) throws Exception {
        return doPost("/api/v1/goals", """
                {
                  "name": "Meta consistencia %d",
                  "description": "Meta para consistencia.",
                  "referenceIndicator": "Trazabilidad",
                  "expectedValue": 100,
                  "measurementUnitId": %d
                }
                """.formatted(SEQUENCE.incrementAndGet(), unitId), 201).get("id").asLong();
    }

    private Long createProject(Long departmentId, Long keyResultId) throws Exception {
        return doPost("/api/v1/projects", """
                {
                  "name": "Proyecto consistencia %d",
                  "description": "Proyecto activo sin avance.",
                  "type": "INVESTIGACION",
                  "departmentId": %d,
                  "status": "ACTIVO",
                  "startPeriod": "2026-1",
                  "endPeriod": "2026-1",
                  "keyResultId": %d
                }
                """.formatted(SEQUENCE.incrementAndGet(), departmentId, keyResultId), 201).get("id").asLong();
    }

    private String objectivePayload(String suffix, Long departmentId, Long periodId, Long goalId, Long betId, Long unitId) {
        return """
                {
                  "name": "Objetivo consistencia %s %d",
                  "description": "Objetivo para consistencia.",
                  "departmentId": %d,
                  "academicPeriodId": %d,
                  "goalId": %d,
                  "strategicBetId": %d,
                  "keyResults": [{
                    "name": "KR consistencia %s",
                    "description": "KR para consistencia",
                    "metric": "Trazabilidad",
                    "baseValue": 0,
                    "targetValue": 100,
                    "measurementUnitId": %d
                  }]
                }
                """.formatted(suffix, SEQUENCE.incrementAndGet(), departmentId, periodId, goalId, betId, suffix, unitId);
    }

    private Long firstId(String url) throws Exception {
        return doGet(url, 200).get(0).get("id").asLong();
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
        String body = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }
}
