package co.edu.icesi.pdg.mte.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
class StrategicHierarchyE2ETest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KeyResultRepository keyResultRepository;

    @Autowired
    private ProjectKeyResultLinkRepository linkRepository;

    @Test
    void happyPathBuildsStrategicHierarchyAndManagesKeyResults() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long departmentId = firstId("/api/v1/departments");

        Long strategicBetId = createStrategicBet("Transformacion digital " + SEQUENCE.incrementAndGet());
        Long goalId = createGoal("Impacto institucional " + SEQUENCE.incrementAndGet(), unitId);

        mockMvc.perform(post("/api/v1/goals/{id}/periods/{periodId}", goalId, periodId))
                .andExpect(status().isOk());

        JsonNode objective = doPost("/api/v1/objectives", objectivePayload(
                "Seguimiento de proyectos " + SEQUENCE.incrementAndGet(),
                departmentId,
                periodId,
                goalId,
                strategicBetId,
                unitId
        ), 201);
        Long objectiveId = objective.get("id").asLong();
        Long keyResultId = objective.get("keyResults").get(0).get("id").asLong();

        assertThat(objective.get("completionPercentage").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(doGet("/api/v1/objectives/" + objectiveId).get("id").asLong()).isEqualTo(objectiveId);
        assertThat(doGet("/api/v1/objectives?strategicBetId=" + strategicBetId)).hasSize(1);
        assertThat(doGet("/api/v1/objectives?goalId=" + goalId)).hasSize(1);
        assertThat(ids(doGet("/api/v1/objectives?departmentId=" + departmentId))).contains(objectiveId);
        assertThat(ids(doGet("/api/v1/objectives?periodId=" + periodId))).contains(objectiveId);
        assertThat(doGet("/api/v1/objectives?strategicBetId=" + strategicBetId
                + "&goalId=" + goalId
                + "&departmentId=" + departmentId
                + "&periodId=" + periodId)).hasSize(1);
        assertThat(doGet("/api/v1/objectives/" + objectiveId + "/key-results")).hasSize(1);
        assertThat(doGet("/api/v1/strategic-bets")).isNotEmpty();
        assertThat(doGet("/api/v1/goals")).isNotEmpty();

        JsonNode createdUnit = doPost("/api/v1/measurement-units", """
                {"name": "Horas %d", "type": "NUMERICA", "description": "Unidad e2e"}
                """.formatted(SEQUENCE.incrementAndGet()), 201);
        JsonNode createdPeriod = doPost("/api/v1/academic-periods", """
                {"name": "2099-%d", "startDate": "2099-01-01", "endDate": "2099-06-01", "status": "FUTURO"}
                """.formatted(SEQUENCE.incrementAndGet()), 201);
        assertThat(createdUnit.get("id").asLong()).isPositive();
        assertThat(createdPeriod.get("id").asLong()).isPositive();

        JsonNode addedKr = doPost("/api/v1/objectives/" + objectiveId + "/key-results", keyResultPayload(unitId), 201);
        JsonNode updatedKr = doPut("/api/v1/key-results/" + addedKr.get("id").asLong(), keyResultPayloadWithCurrent(unitId, 40), 200);
        JsonNode patchedKr = doPatch("/api/v1/key-results/" + keyResultId + "/current-value", """
                {"currentValue": 50}
                """, 200);

        assertThat(updatedKr.get("progressPercentage").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(patchedKr.get("progressPercentage").decimalValue()).isEqualByComparingTo("0.00");

        mockMvc.perform(delete("/api/v1/key-results/{id}", addedKr.get("id").asLong()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/goals/{id}/periods/{periodId}", goalId, periodId))
                .andExpect(status().isOk());

        assertThat(doGet("/api/v1/strategic-bets/" + strategicBetId).get("id").asLong()).isEqualTo(strategicBetId);
        assertThat(doGet("/api/v1/goals/" + goalId).get("id").asLong()).isEqualTo(goalId);
    }

    @Test
    void happyPathCompletesCatalogLifecycleForUnitsAndPeriods() throws Exception {
        JsonNode unit = doPost("/api/v1/measurement-units", """
                {"name": "Unidad ciclo %d", "type": "OTRA", "description": "Temporal"}
                """.formatted(SEQUENCE.incrementAndGet()), 201);
        Long unitId = unit.get("id").asLong();

        JsonNode updatedUnit = doPut("/api/v1/measurement-units/" + unitId, """
                {"name": "Unidad ciclo editada %d", "type": "NUMERICA", "description": "Editada"}
                """.formatted(SEQUENCE.incrementAndGet()), 200);
        JsonNode inactiveUnit = doPatch("/api/v1/measurement-units/" + unitId + "/active", """
                {"active": false}
                """, 200);

        assertThat(updatedUnit.get("type").asText()).isEqualTo("NUMERICA");
        assertThat(inactiveUnit.get("active").asBoolean()).isFalse();
        doDelete("/api/v1/measurement-units/" + unitId, 204);

        JsonNode period = doPost("/api/v1/academic-periods", """
                {"name": "2199-%d", "startDate": "2199-01-01", "endDate": "2199-06-01", "status": "FUTURO"}
                """.formatted(SEQUENCE.incrementAndGet()), 201);
        Long periodId = period.get("id").asLong();

        JsonNode updatedPeriod = doPut("/api/v1/academic-periods/" + periodId, """
                {"name": "2199-%d", "startDate": "2199-07-01", "endDate": "2199-12-01", "status": "FUTURO"}
                """.formatted(SEQUENCE.incrementAndGet()), 200);
        JsonNode closedPeriod = doPatch("/api/v1/academic-periods/" + periodId + "/status", """
                {"status": "CERRADO"}
                """, 200);

        assertThat(updatedPeriod.get("startDate").asText()).isEqualTo("2199-07-01");
        assertThat(closedPeriod.get("status").asText()).isEqualTo("CERRADO");
        doDelete("/api/v1/academic-periods/" + periodId, 204);
    }

    @Test
    void happyPathLinksProjectToKeyResultAndStatusDrivesStrategicExecution() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long departmentId = firstId("/api/v1/departments");
        Long strategicBetId = createStrategicBet("Apuesta ejecucion " + SEQUENCE.incrementAndGet());
        Long goalId = createGoal("Meta ejecucion " + SEQUENCE.incrementAndGet(), unitId);
        JsonNode objective = doPost("/api/v1/objectives", objectivePayload(
                "Objetivo ejecucion " + SEQUENCE.incrementAndGet(),
                departmentId,
                periodId,
                goalId,
                strategicBetId,
                unitId
        ), 201);
        Long objectiveId = objective.get("id").asLong();
        Long keyResultId = objective.get("keyResults").get(0).get("id").asLong();
        Long projectId = createProject("Proyecto vinculado " + SEQUENCE.incrementAndGet(), departmentId);

        JsonNode link = doPost("/api/v1/project-key-result-links", """
                {"projectId": %d, "keyResultId": %d, "contributionWeight": 60}
                """.formatted(projectId, keyResultId), 201);
        JsonNode links = doGet("/api/v1/project-key-result-links?projectId=" + projectId);
        JsonNode chainBefore = doGet("/api/v1/projects/" + projectId + "/impact-chain");

        assertThat(link.get("overweightWarning").asBoolean()).isFalse();
        assertThat(links).hasSize(1);
        assertThat(chainBefore.get("impacts").get(0).get("appliedContribution").decimalValue()).isEqualByComparingTo("0.00");

        doPatch("/api/v1/projects/" + projectId + "/status", """
                {"status": "FINALIZADO"}
                """, 200);

        JsonNode keyResults = doGet("/api/v1/objectives/" + objectiveId + "/key-results");
        JsonNode chainAfter = doGet("/api/v1/projects/" + projectId + "/impact-chain");
        JsonNode bet = doGet("/api/v1/strategic-bets/" + strategicBetId + "?period=2026-Q2");
        JsonNode tree = doGet("/api/v1/strategic-hierarchy/tree?period=2026-Q2");

        assertThat(keyResults.get(0).get("progressPercentage").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(chainAfter.get("impacts").get(0).get("appliedContribution").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(bet.get("executionSummary").get("completedProjects").asInt()).isEqualTo(1);
        assertThat(tree.findValues("completedProjects").stream().map(JsonNode::asInt)).contains(1);
    }

    @Test
    void happyPathListsObjectiveCardsUpdatesObjectiveAndShowsHierarchyTree() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long departmentId = firstId("/api/v1/departments");
        Long strategicBetId = createStrategicBet("Apuesta cards " + SEQUENCE.incrementAndGet());
        Long goalId = createGoal("Meta cards " + SEQUENCE.incrementAndGet(), unitId);
        JsonNode objective = doPost("/api/v1/objectives", objectivePayload(
                "Objetivo cards " + SEQUENCE.incrementAndGet(),
                departmentId,
                periodId,
                goalId,
                strategicBetId,
                unitId
        ), 201);
        Long objectiveId = objective.get("id").asLong();
        Long keyResultId = objective.get("keyResults").get(0).get("id").asLong();
        createProjectLink(keyResultId, 9001L);

        JsonNode cards = doGet("/api/v1/objectives/cards?strategicBetId=" + strategicBetId);
        JsonNode updated = doPatch("/api/v1/objectives/" + objectiveId, """
                {
                  "name": "Objetivo editado",
                  "description": "Descripcion editada"
                }
                """, 200);
        JsonNode tree = doGet("/api/v1/strategic-hierarchy/tree");

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).get("lowCompletionAlert").asBoolean()).isTrue();
        assertThat(cards.get(0).get("keyResults")).hasSize(1);
        assertThat(updated.get("name").asText()).isEqualTo("Objetivo editado");
        assertThat(tree.findValues("nodeType").stream().map(JsonNode::asText)).contains("STRATEGIC_BET", "GOAL", "OBJECTIVE", "KEY_RESULT", "PROJECT");
    }

    @Test
    void sadPathsForCatalogsReturnExpectedErrors() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");

        doPost("/api/v1/measurement-units", """
                {"name": "Porcentaje", "type": "PORCENTAJE"}
                """, 409);
        doPost("/api/v1/measurement-units", """
                {"name": "", "type": "PORCENTAJE"}
                """, 400);
        doPost("/api/v1/measurement-units", """
                {"name": "Unidad sin tipo"}
                """, 400);
        doPost("/api/v1/academic-periods", """
                {"name": "Periodo malo", "startDate": "2026-06-01", "endDate": "2026-01-01", "status": "ACTIVO"}
                """, 400);
        doPost("/api/v1/academic-periods", """
                {"name": "2026-1", "startDate": "2026-01-01", "endDate": "2026-06-01", "status": "ACTIVO"}
                """, 409);
        doPost("/api/v1/goals", """
                {"name": "Meta sin unidad", "description": "x", "expectedValue": 1, "measurementUnitId": 99999}
                """, 404);

        assertThat(unitId).isPositive();
    }

    @Test
    void sadPathsForCatalogLifecycleReturnExpectedErrors() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long usedUnitId = doPost("/api/v1/measurement-units", """
                {"name": "Unidad usada %d", "type": "PORCENTAJE", "description": "Uso en meta"}
                """.formatted(SEQUENCE.incrementAndGet()), 201).get("id").asLong();
        Long usedPeriodId = doPost("/api/v1/academic-periods", """
                {"name": "2299-%d", "startDate": "2299-01-01", "endDate": "2299-06-01", "status": "FUTURO"}
                """.formatted(SEQUENCE.incrementAndGet()), 201).get("id").asLong();
        Long goalId = createGoal("Meta catalogos " + SEQUENCE.incrementAndGet(), usedUnitId);
        mockMvc.perform(post("/api/v1/goals/{id}/periods/{periodId}", goalId, usedPeriodId))
                .andExpect(status().isOk());

        doPut("/api/v1/measurement-units/99999", """
                {"name": "No existe", "type": "NUMERICA", "description": "x"}
                """, 404);
        doPut("/api/v1/measurement-units/" + usedUnitId, """
                {"name": "Porcentaje", "type": "NUMERICA", "description": "duplicada"}
                """, 409);
        doPatch("/api/v1/measurement-units/" + unitId + "/active", "{}", 400);
        doDelete("/api/v1/measurement-units/" + usedUnitId, 409);
        doDelete("/api/v1/measurement-units/99999", 404);

        doPut("/api/v1/academic-periods/99999", """
                {"name": "No existe", "startDate": "2026-01-01", "endDate": "2026-06-01", "status": "ACTIVO"}
                """, 404);
        doPut("/api/v1/academic-periods/" + usedPeriodId, """
                {"name": "Periodo fecha mala", "startDate": "2026-06-01", "endDate": "2026-01-01", "status": "ACTIVO"}
                """, 400);
        doPut("/api/v1/academic-periods/" + usedPeriodId, """
                {"name": "2026-1", "startDate": "2026-01-01", "endDate": "2026-06-01", "status": "ACTIVO"}
                """, 409);
        doPatch("/api/v1/academic-periods/" + periodId + "/status", "{}", 400);
        doDelete("/api/v1/academic-periods/" + usedPeriodId, 409);
        doDelete("/api/v1/academic-periods/99999", 404);
    }

    @Test
    void sadPathsForStrategicBetGoalAndObjectiveCreationReturnExpectedErrors() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long departmentId = firstId("/api/v1/departments");
        Long betId = createStrategicBet("Apuesta sad " + SEQUENCE.incrementAndGet());
        Long goalId = createGoal("Meta sad " + SEQUENCE.incrementAndGet(), unitId);

        doPost("/api/v1/strategic-bets", """
                {"name": "Apuesta invalida", "description": "x", "startDate": "2026-12-01", "endDate": "2026-01-01"}
                """, 400);
        doPost("/api/v1/strategic-bets", """
                {"name": "", "description": "x"}
                """, 400);
        doPost("/api/v1/goals", """
                {"name": "Meta invalida", "description": "x", "expectedValue": -1, "measurementUnitId": %d}
                """.formatted(unitId), 400);
        doPost("/api/v1/goals", """
                {"name": "Meta invalida", "description": "", "expectedValue": 1, "measurementUnitId": %d}
                """.formatted(unitId), 400);
        doPost("/api/v1/objectives", objectivePayload("Objetivo sin depto", 99999L, periodId, goalId, betId, unitId), 404);
        doPost("/api/v1/objectives", objectivePayload("Objetivo sin periodo", departmentId, 99999L, goalId, betId, unitId), 404);
        doPost("/api/v1/objectives", objectivePayload("Objetivo sin meta", departmentId, periodId, 99999L, betId, unitId), 404);
        doPost("/api/v1/objectives", objectivePayload("Objetivo sin apuesta", departmentId, periodId, goalId, 99999L, unitId), 404);
        doPost("/api/v1/objectives", """
                {
                  "name": "Objetivo sin KR",
                  "description": "x",
                  "departmentId": %d,
                  "academicPeriodId": %d,
                  "goalId": %d,
                  "strategicBetId": %d,
                  "keyResults": []
                }
                """.formatted(departmentId, periodId, goalId, betId), 400);
        doPost("/api/v1/objectives", """
                {
                  "name": "",
                  "description": "x",
                  "departmentId": %d,
                  "academicPeriodId": %d,
                  "goalId": %d,
                  "strategicBetId": %d,
                  "keyResults": [%s]
                }
                """.formatted(departmentId, periodId, goalId, betId, keyResultPayload(unitId)), 400);
    }

    @Test
    void sadPathsForKeyResultManagementReturnExpectedErrors() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long departmentId = firstId("/api/v1/departments");
        Long betId = createStrategicBet("Apuesta KR " + SEQUENCE.incrementAndGet());
        Long goalId = createGoal("Meta KR " + SEQUENCE.incrementAndGet(), unitId);
        Long objectiveId = doPost("/api/v1/objectives", objectivePayload(
                "Objetivo KR " + SEQUENCE.incrementAndGet(),
                departmentId,
                periodId,
                goalId,
                betId,
                unitId
        ), 201).get("id").asLong();

        doPost("/api/v1/objectives/99999/key-results", keyResultPayload(unitId), 404);
        doPost("/api/v1/objectives/" + objectiveId + "/key-results", """
                {"description": "", "metric": "m", "baseValue": 0, "targetValue": 1, "measurementUnitId": %d}
                """.formatted(unitId), 400);
        doPost("/api/v1/objectives/" + objectiveId + "/key-results", """
                {"description": "x", "metric": "", "baseValue": 0, "targetValue": 1, "measurementUnitId": %d}
                """.formatted(unitId), 400);
        doPost("/api/v1/objectives/" + objectiveId + "/key-results", """
                {"description": "x", "metric": "m", "baseValue": 0, "targetValue": 1, "measurementUnitId": 99999}
                """, 404);
        doPut("/api/v1/key-results/99999", keyResultPayload(unitId), 404);
        doPatch("/api/v1/key-results/99999/current-value", """
                {"currentValue": 10}
                """, 404);
        doPatch("/api/v1/key-results/1/current-value", """
                {"currentValue": null}
                """, 400);
        mockMvc.perform(delete("/api/v1/key-results/{id}", 99999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void sadPathsForObjectiveCardsUpdateAndTreeBlockReturnExpectedErrors() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long departmentId = firstId("/api/v1/departments");
        Long strategicBetId = createStrategicBet("Apuesta update " + SEQUENCE.incrementAndGet());
        Long goalId = createGoal("Meta update " + SEQUENCE.incrementAndGet(), unitId);
        Long objectiveId = doPost("/api/v1/objectives", objectivePayload(
                "Objetivo update " + SEQUENCE.incrementAndGet(),
                departmentId,
                periodId,
                goalId,
                strategicBetId,
                unitId
        ), 201).get("id").asLong();

        doPatch("/api/v1/objectives/99999", """
                {"name": "x", "description": "x"}
                """, 404);
        doPatch("/api/v1/objectives/" + objectiveId, """
                {"name": "", "description": "x"}
                """, 400);
        doPatch("/api/v1/objectives/" + objectiveId, """
                {"name": "x", "description": ""}
                """, 400);
        doPatch("/api/v1/objectives/" + objectiveId, """
                {"description": "x"}
                """, 400);
        doPatch("/api/v1/objectives/" + objectiveId, """
                {"name": "x"}
                """, 400);
        mockMvc.perform(get("/api/v1/objectives/cards")
                        .param("strategicBetId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicHealthAndProtectedEndpointsRespectAuthModeContract() throws Exception {
        assertThat(doGet("/api/v1/health").get("status").asText()).isEqualTo("UP");
        assertThat(doGet("/api/v1/departments")).isNotEmpty();
    }

    private Long createStrategicBet(String name) throws Exception {
        return doPost("/api/v1/strategic-bets", """
                {
                  "name": "%s",
                  "description": "Impulsar iniciativas academicas con trazabilidad de impacto.",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(name), 201).get("id").asLong();
    }

    private Long createGoal(String name, Long unitId) throws Exception {
        return doPost("/api/v1/goals", """
                {
                  "name": "%s",
                  "description": "Medir el aporte de los proyectos a indicadores estrategicos.",
                  "referenceIndicator": "Indice de impacto",
                  "expectedValue": 80,
                  "measurementUnitId": %d,
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(name, unitId), 201).get("id").asLong();
    }

    private Long createProject(String name, Long departmentId) throws Exception {
        return doPost("/api/v1/projects", """
                {
                  "name": "%s",
                  "description": "Proyecto usado para medir avance de KR por ejecucion.",
                  "type": "INVESTIGACION",
                  "departmentId": %d,
                  "status": "ACTIVO",
                  "startPeriod": "2026-Q1",
                  "endPeriod": "2026-Q2",
                  "startDate": "2026-01-15",
                  "endDate": "2026-06-30",
                  "tutors": ["Tutora A"]
                }
                """.formatted(name, departmentId), 201).get("id").asLong();
    }

    private String objectivePayload(String name, Long departmentId, Long periodId, Long goalId, Long strategicBetId, Long unitId) {
        return """
                {
                  "name": "%s",
                  "description": "Objetivo operativo para tablero directivo.",
                  "departmentId": %d,
                  "academicPeriodId": %d,
                  "goalId": %d,
                  "strategicBetId": %d,
                  "keyResults": [%s]
                }
                """.formatted(name, departmentId, periodId, goalId, strategicBetId, keyResultPayload(unitId));
    }

    private String keyResultPayload(Long unitId) {
        return keyResultPayloadWithCurrent(unitId, 25);
    }

    private String keyResultPayloadWithCurrent(Long unitId, int currentValue) {
        return """
                {
                  "description": "Lograr trazabilidad de proyectos activos",
                  "metric": "Porcentaje de proyectos vinculados",
                  "baseValue": 0,
                  "targetValue": 100,
                  "currentValue": %d,
                  "measurementUnitId": %d
                }
                """.formatted(currentValue, unitId);
    }

    private Long firstId(String url) throws Exception {
        return doGet(url).get(0).get("id").asLong();
    }

    private JsonNode doGet(String url) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode doPost(String url, String payload, int expectedStatus) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode doPut(String url, String payload, int expectedStatus) throws Exception {
        return objectMapper.readTree(mockMvc.perform(put(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode doPatch(String url, String payload, int expectedStatus) throws Exception {
        return objectMapper.readTree(mockMvc.perform(patch(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private void doDelete(String url, int expectedStatus) throws Exception {
        mockMvc.perform(delete(url))
                .andExpect(status().is(expectedStatus));
    }

    private void createProjectLink(Long keyResultId, Long externalProjectId) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResultRepository.findById(keyResultId).orElseThrow());
        link.setExternalProjectId(externalProjectId);
        link.setContributionWeight(java.math.BigDecimal.valueOf(35));
        linkRepository.save(link);
    }

    private java.util.List<Long> ids(JsonNode nodes) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        nodes.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }
}
