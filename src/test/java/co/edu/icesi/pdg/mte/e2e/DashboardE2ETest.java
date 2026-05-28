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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardE2ETest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void happyPathExposesDashboardKpisChartsAndContributionChainAlias() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long departmentId = firstId("/api/v1/departments");
        Long betId = createStrategicBet();
        Long goalId = createGoal(unitId);
        JsonNode objective = doPost("/api/v1/objectives", objectivePayload(departmentId, periodId, goalId, betId, unitId), 201);
        Long keyResultId = objective.get("keyResults").get(0).get("id").asLong();
        Long projectId = createProject(departmentId);

        doPost("/api/v1/project-key-result-links", """
                {"projectId": %d, "keyResultId": %d, "contributionWeight": 100, "contributionType": "DIRECTA"}
                """.formatted(projectId, keyResultId), 201);
        doPatch("/api/v1/projects/" + projectId + "/status", """
                {"status": "FINALIZADO"}
                """, 200);

        JsonNode summary = doGet("/api/v1/dashboard/summary?period=2026-1", 200);
        JsonNode globalSummary = doGet("/api/v1/dashboard/summary", 200);
        JsonNode statusChart = doGet("/api/v1/dashboard/projects/by-status?period=2026-1", 200);
        JsonNode krChart = doGet("/api/v1/dashboard/key-results/by-progress?period=2026-1", 200);
        JsonNode departments = doGet("/api/v1/dashboard/departments/summary?period=2026-1", 200);
        JsonNode bets = doGet("/api/v1/dashboard/strategic-bets/summary?period=2026-1", 200);
        JsonNode goals = doGet("/api/v1/dashboard/goals/summary?period=2026-1", 200);
        JsonNode contributionChain = doGet("/api/v1/projects/" + projectId + "/contribution-chain", 200);
        JsonNode generalReport = doGet("/api/v1/reports/general?period=2026-1", 200);
        JsonNode departmentsReport = doGet("/api/v1/reports/departments?period=2026-1", 200);
        JsonNode rankingReport = doGet("/api/v1/reports/objectives/ranking?period=2026-1", 200);
        JsonNode comparisonReport = doGet("/api/v1/reports/period-comparison?basePeriod=2026-1&comparePeriod=2026-1", 200);
        JsonNode presentation = doGet("/api/v1/presentation?period=2026-1", 200);
        expectGet("/api/v1/reports/export.csv?period=2026-1", 200);
        expectGet("/api/v1/reports/export.pdf?period=2026-1", 200);

        assertThat(summary.get("completedProjects").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(globalSummary.hasNonNull("period")).isFalse();
        assertThat(globalSummary.get("completedProjects").asLong()).isGreaterThanOrEqualTo(summary.get("completedProjects").asLong());
        assertThat(summary.get("completedKeyResults").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(statusChart.findValues("status").stream().map(JsonNode::asText))
                .contains("BORRADOR", "ACTIVO", "FINALIZADO", "SUSPENDIDO", "ARCHIVADO");
        assertThat(krChart.findValues("bucket").stream().map(JsonNode::asText))
                .containsExactly("COMPLETED", "ON_TRACK", "AT_RISK", "LOW");
        assertThat(departments.findValues("departmentId").stream().map(JsonNode::asLong))
                .contains(departmentId);
        assertThat(bets.findValues("strategicBetId").stream().map(JsonNode::asLong))
                .contains(betId);
        assertThat(bets.findValues("averageObjectiveCoverage")).isEmpty();
        assertThat(bets.findValues("completedObjectives").stream().map(JsonNode::asLong))
                .anyMatch(value -> value >= 1);
        assertThat(goals.findValues("goalId").stream().map(JsonNode::asLong))
                .contains(goalId);
        assertThat(goals.findValues("averageObjectiveCoverage")).isEmpty();
        assertThat(goals.findValues("completedObjectives").stream().map(JsonNode::asLong))
                .anyMatch(value -> value >= 1);
        assertThat(contributionChain.get("impacts").get(0).get("appliedContribution").decimalValue())
                .isEqualByComparingTo("100.00");
        assertThat(generalReport.get("totalProjects").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(departmentsReport).isNotEmpty();
        assertThat(rankingReport).isNotEmpty();
        assertThat(comparisonReport.get("objectiveCoverageDelta").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(presentation.get("slides").findValues("type").stream().map(JsonNode::asText))
                .contains("COVER", "STRATEGIC_BET", "CLOSING");
    }

    @Test
    void sadPathsRejectMalformedDashboardPeriods() throws Exception {
        doGet("/api/v1/dashboard/summary?period=2026-X", 400);
        doGet("/api/v1/dashboard/projects/by-status?period=2026-0", 400);
        doGet("/api/v1/dashboard/key-results/by-progress?period=abc", 400);
        doGet("/api/v1/dashboard/departments/summary?period=2026-Q5", 400);
        doGet("/api/v1/dashboard/strategic-bets/summary?period=26-1", 400);
        doGet("/api/v1/dashboard/goals/summary?period=26-1", 400);
    }

    @Test
    void sadPathsRejectMalformedReportPeriodsAndMissingComparisonInputs() throws Exception {
        doGet("/api/v1/reports/general?period=2026-3", 400);
        doGet("/api/v1/reports/departments?period=2026-Q9", 400);
        doGet("/api/v1/reports/objectives/ranking?period=26-1", 400);
        doGet("/api/v1/reports/period-comparison?basePeriod=&comparePeriod=2026-1", 400);
        doGet("/api/v1/reports/period-comparison?basePeriod=2026-1&comparePeriod=", 400);
        doGet("/api/v1/reports/export.csv?period=2026-X", 400);
        doGet("/api/v1/reports/export.pdf?period=2026-X", 400);
    }

    @Test
    void reportsExportsAndPresentationExposeFrontendReadyContracts() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long departmentId = firstId("/api/v1/departments");
        Long betId = createStrategicBet();
        Long goalId = createGoal(unitId);
        JsonNode objective = doPost("/api/v1/objectives", objectivePayload(departmentId, periodId, goalId, betId, unitId), 201);
        Long objectiveId = objective.get("id").asLong();
        Long keyResultId = objective.get("keyResults").get(0).get("id").asLong();
        Long projectId = createProject(departmentId);

        doPost("/api/v1/project-key-result-links", """
                {"projectId": %d, "keyResultId": %d, "contributionWeight": 75, "contributionType": "DIRECTA"}
                """.formatted(projectId, keyResultId), 201);
        doPatch("/api/v1/projects/" + projectId + "/status", """
                {"status": "FINALIZADO"}
                """, 200);

        JsonNode consolidated = doGet("/api/v1/reports?period=2026-1&departmentId=" + departmentId + "&objectiveId=" + objectiveId, 200);
        JsonNode comparison = doGet("/api/v1/reports/period-comparison?basePeriod=2026-1&comparePeriod=2026-1&departmentId=" + departmentId, 200);
        JsonNode presentation = doGet("/api/v1/presentation?period=2026-1", 200);
        var csvResponse = mockMvc.perform(get("/api/v1/reports/export.csv")
                        .param("period", "2026-1")
                        .param("departmentId", departmentId.toString())
                        .param("objectiveId", objectiveId.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        var pdfResponse = mockMvc.perform(get("/api/v1/reports/export.pdf")
                        .param("period", "2026-1")
                        .param("departmentId", departmentId.toString())
                        .param("objectiveId", objectiveId.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        assertThat(consolidated.get("general").get("departmentId").asLong()).isEqualTo(departmentId);
        assertThat(consolidated.get("general").get("objectiveId").asLong()).isEqualTo(objectiveId);
        assertThat(consolidated.get("objectiveRanking")).hasSize(1);
        assertThat(comparison.get("objectiveCoverageDelta").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(presentation.get("controls").get("fullscreenEnabled").asBoolean()).isTrue();
        assertThat(presentation.get("controls").get("keyboardNavigationEnabled").asBoolean()).isTrue();
        assertThat(presentation.get("controls").get("nextKeys").toString()).contains("ArrowRight", "Space");
        assertThat(csvResponse.getHeader("Content-Disposition")).contains("msp-report.csv");
        assertThat(csvResponse.getContentAsString()).contains("section,name,value", "general,totalProjects");
        assertThat(pdfResponse.getHeader("Content-Disposition")).contains("msp-report.pdf");
        assertThat(pdfResponse.getContentAsString()).startsWith("%PDF-1.4");
    }

    private Long createStrategicBet() throws Exception {
        return doPost("/api/v1/strategic-bets", """
                {
                  "name": "Apuesta dashboard %d",
                  "description": "Apuesta para agregados ejecutivos.",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(SEQUENCE.incrementAndGet()), 201).get("id").asLong();
    }

    private Long createGoal(Long unitId) throws Exception {
        return doPost("/api/v1/goals", """
                {
                  "name": "Meta dashboard %d",
                  "description": "Meta para tablero ejecutivo.",
                  "referenceIndicator": "Cobertura operativa",
                  "expectedValue": 100,
                  "measurementUnitId": %d
                }
                """.formatted(SEQUENCE.incrementAndGet(), unitId), 201).get("id").asLong();
    }

    private Long createProject(Long departmentId) throws Exception {
        return doPost("/api/v1/projects", """
                {
                  "name": "Proyecto dashboard %d",
                  "description": "Proyecto para dashboard.",
                  "type": "INVESTIGACION",
                  "departmentId": %d,
                  "status": "ACTIVO",
                  "startPeriod": "2026-1",
                  "endPeriod": "2026-1"
                }
                """.formatted(SEQUENCE.incrementAndGet(), departmentId), 201).get("id").asLong();
    }

    private String objectivePayload(Long departmentId, Long periodId, Long goalId, Long betId, Long unitId) {
        return """
                {
                  "name": "Objetivo dashboard %d",
                  "description": "Objetivo para metricas de tablero.",
                  "departmentId": %d,
                  "academicPeriodId": %d,
                  "goalId": %d,
                  "strategicBetId": %d,
                  "keyResults": [{
                    "name": "KR dashboard",
                    "description": "KR de ejecucion completa",
                    "metric": "Contribucion de proyectos finalizados",
                    "baseValue": 0,
                    "targetValue": 100,
                    "measurementUnitId": %d
                  }]
                }
                """.formatted(SEQUENCE.incrementAndGet(), departmentId, periodId, goalId, betId, unitId);
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

    private void expectGet(String url, int expectedStatus) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().is(expectedStatus));
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
}
