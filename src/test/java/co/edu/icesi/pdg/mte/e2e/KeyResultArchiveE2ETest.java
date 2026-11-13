package co.edu.icesi.pdg.mte.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KeyResultArchiveE2ETest extends StrategicHierarchyE2ETestSupport {
    private static final String INACTIVE = "{\"active\": false}";
    private static final String ACTIVE = "{\"active\": true}";

    private record Scenario(Long unitId, Long periodId, Long betId, Long goalId, Long departmentId) {}

    private Scenario scenario() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        return new Scenario(unitId, firstId("/api/v1/academic-periods"),
                createStrategicBet("Apuesta archivo " + SEQUENCE.incrementAndGet()),
                createGoal("Meta archivo " + SEQUENCE.incrementAndGet(), unitId),
                firstId("/api/v1/departments"));
    }

    private Long newKeyResult(Scenario scenario) throws Exception {
        return createKeyResult("KR archivo " + SEQUENCE.incrementAndGet(), scenario.periodId(), scenario.goalId(),
                scenario.betId(), scenario.unitId()).get("id").asLong();
    }

    private JsonNode getAs(String url, String token, int expectedStatus) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private void setActiveAs(Long id, String body, String token, int expectedStatus) throws Exception {
        mockMvc.perform(patch("/api/v1/key-results/" + id + "/active").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expectedStatus));
    }

    private boolean listed(String url, Long id) throws Exception {
        return ids(doGet(url)).contains(id);
    }

    private boolean treeHasKeyResult(Long id) throws Exception {
        return StreamSupport.stream(doGet("/api/v1/strategic-hierarchy/tree").findParents("nodeType").spliterator(), false)
                .anyMatch(node -> "KEY_RESULT".equals(node.get("nodeType").asText())
                        && id.toString().equals(node.get("id").asText()));
    }

    private boolean consistencyMentions(Long id) throws Exception {
        return doGet("/api/v1/consistency/check").findValues("entityCode").stream()
                .anyMatch(code -> ("KR-" + id).equals(code.asText()));
    }

    private long keyResultsOnDashboard() throws Exception {
        long total = 0;
        for (JsonNode row : doGet("/api/v1/dashboard/key-results/by-status")) {
            total += row.get("count").asLong();
        }
        return total;
    }

    private boolean rankingMentions(Long id) throws Exception {
        return ids(doGet("/api/v1/reports/key-results/ranking"), "keyResultId").contains(id);
    }

    private List<Long> ids(JsonNode nodes, String field) {
        return StreamSupport.stream(nodes.spliterator(), false).map(node -> node.get(field).asLong()).toList();
    }

    @Test
    void archivingHidesTheKeyResultEverywhereAndRestoringBringsItBack() throws Exception {
        Scenario scenario = scenario();
        Long id = newKeyResult(scenario);
        long dashboardBefore = keyResultsOnDashboard();
        assertThat(listed("/api/v1/key-results", id)).isTrue();
        assertThat(treeHasKeyResult(id)).isTrue();
        assertThat(consistencyMentions(id)).isTrue();
        assertThat(rankingMentions(id)).isTrue();
        JsonNode fresh = doGet("/api/v1/key-results/" + id);
        assertThat(fresh.get("archived").asBoolean()).isFalse();
        assertThat(fresh.get("archivedAt").isNull()).isTrue();

        Instant before = Instant.now().minusSeconds(5);
        JsonNode archived = doPatch("/api/v1/key-results/" + id + "/active", INACTIVE, 200);

        assertThat(archived.get("archived").asBoolean()).isTrue();
        assertThat(Instant.parse(archived.get("archivedAt").asText())).isAfter(before);
        assertThat(listed("/api/v1/key-results", id)).isFalse();
        assertThat(listed("/api/v1/key-results?includeArchived=false", id)).isFalse();
        assertThat(listed("/api/v1/key-results?includeArchived=true", id)).isTrue();
        assertThat(doGet("/api/v1/key-results/" + id).get("archived").asBoolean()).isTrue();
        assertThat(treeHasKeyResult(id)).isFalse();
        assertThat(consistencyMentions(id)).isFalse();
        assertThat(keyResultsOnDashboard()).isEqualTo(dashboardBefore - 1);
        assertThat(rankingMentions(id)).isFalse();
        assertThat(doGet("/api/v1/audit-logs?entityType=KEY_RESULT&entityId=" + id + "&action=STATUS_CHANGE")).hasSize(1);

        JsonNode restored = doPatch("/api/v1/key-results/" + id + "/active", ACTIVE, 200);

        assertThat(restored.get("archived").asBoolean()).isFalse();
        assertThat(restored.get("archivedAt").isNull()).isTrue();
        assertThat(listed("/api/v1/key-results", id)).isTrue();
        assertThat(treeHasKeyResult(id)).isTrue();
        assertThat(keyResultsOnDashboard()).isEqualTo(dashboardBefore);
        assertThat(doGet("/api/v1/audit-logs?entityType=KEY_RESULT&entityId=" + id + "&action=STATUS_CHANGE")).hasSize(2);
        doPut("/api/v1/key-results/" + id, keyResultPayload("KR restaurado " + SEQUENCE.incrementAndGet(),
                scenario.periodId(), scenario.goalId(), scenario.betId(), scenario.unitId()), 200);
    }

    @Test
    void anArchivedKeyResultRejectsChangesAndNewLinks() throws Exception {
        Scenario scenario = scenario();
        Long id = newKeyResult(scenario);
        Long projectId = createProject("Proyecto archivo " + SEQUENCE.incrementAndGet(), scenario.departmentId());
        doPatch("/api/v1/key-results/" + id + "/active", INACTIVE, 200);

        JsonNode update = doPut("/api/v1/key-results/" + id, keyResultPayload("KR editado", scenario.periodId(),
                scenario.goalId(), scenario.betId(), scenario.unitId()), 409);
        doPatch("/api/v1/key-results/" + id + "/progress", """
                {"currentValue": 10, "progressPercentage": 10}
                """, 409);
        doPost("/api/v1/project-key-result-links", """
                {"projectId": %d, "keyResultId": %d}
                """.formatted(projectId, id), 409);
        doPost("/api/v1/projects", """
                {"name": "Proyecto sobre archivado %d", "description": "No debe crearse", "type": "INVESTIGACION",
                 "departmentId": %d, "status": "ACTIVO", "startPeriod": "2026-1", "endPeriod": "2026-1",
                 "keyResultId": %d, "linkStatus": "ACTIVO"}
                """.formatted(SEQUENCE.incrementAndGet(), scenario.departmentId(), id), 409);

        assertThat(update.get("message").asText()).contains("archivado");
        assertThat(doGet("/api/v1/key-results/" + id).get("name").asText()).startsWith("KR archivo");
    }

    @Test
    void theOldDeleteEndpointNoLongerExists() throws Exception {
        Long id = newKeyResult(scenario());

        doDelete("/api/v1/key-results/" + id, 405);

        assertThat(listed("/api/v1/key-results", id)).isTrue();
    }

    @Test
    void closedOrLinkedKeyResultsCannotBeArchived() throws Exception {
        Scenario scenario = scenario();
        Long closed = newKeyResult(scenario);
        doPatch("/api/v1/key-results/" + closed + "/status", "{\"status\": \"ACTIVO\"}", 200);
        doPatch("/api/v1/key-results/" + closed + "/status", "{\"status\": \"CERRADO\"}", 200);
        Long linked = newKeyResult(scenario);
        createProjectLink(linked, 9101L);

        JsonNode closedError = doPatch("/api/v1/key-results/" + closed + "/active", INACTIVE, 409);
        JsonNode linkedError = doPatch("/api/v1/key-results/" + linked + "/active", INACTIVE, 409);

        assertThat(closedError.get("message").asText()).contains("cerrado");
        assertThat(linkedError.get("message").asText()).contains("proyectos vinculados");
        assertThat(doGet("/api/v1/key-results/" + closed).get("archived").asBoolean()).isFalse();
        assertThat(doGet("/api/v1/key-results/" + linked).get("archived").asBoolean()).isFalse();
    }

    @Test
    void aKeyResultUsedAsAProjectMainKeyResultCannotBeArchived() throws Exception {
        Scenario scenario = scenario();
        Long id = newKeyResult(scenario);
        doPost("/api/v1/projects", """
                {"name": "Proyecto con KR principal %d", "description": "Usa el KR como principal", "type": "INVESTIGACION",
                 "departmentId": %d, "status": "BORRADOR", "startPeriod": "2026-1", "endPeriod": "2026-1",
                 "keyResultId": %d}
                """.formatted(SEQUENCE.incrementAndGet(), scenario.departmentId(), id), 201);

        JsonNode error = doPatch("/api/v1/key-results/" + id + "/active", INACTIVE, 409);

        assertThat(error.get("message").asText()).contains("proyectos vinculados");
        assertThat(doGet("/api/v1/key-results/" + id).get("archived").asBoolean()).isFalse();
    }

    @Test
    void anEmptyActiveStateBodyIsRejectedInsteadOfArchiving() throws Exception {
        Scenario scenario = scenario();
        Long id = newKeyResult(scenario);

        doPatch("/api/v1/key-results/" + id + "/active", "{}", 400);
        doPatch("/api/v1/goals/" + scenario.goalId() + "/active", "{}", 400);
        doPatch("/api/v1/strategic-bets/" + scenario.betId() + "/active", "{\"active\": null}", 400);

        assertThat(doGet("/api/v1/key-results/" + id).get("archived").asBoolean()).isFalse();
    }

    @Test
    void onlyAdminsAndManagersCanArchiveButEveryoneCanRead() throws Exception {
        Long id = newKeyResult(scenario());

        setActiveAs(id, INACTIVE, "test-visitor", 403);
        setActiveAs(id, INACTIVE, "test-contributor", 403);
        setActiveAs(id, INACTIVE, "test-manager", 200);
        assertThat(ids(getAs("/api/v1/key-results?includeArchived=true", "test-visitor", 200), "id")).contains(id);
        assertThat(ids(getAs("/api/v1/key-results", "test-visitor", 200), "id")).doesNotContain(id);
        setActiveAs(id, ACTIVE, "test-visitor", 403);
        setActiveAs(id, ACTIVE, "test-manager", 200);

        assertThat(ids(getAs("/api/v1/key-results", "test-visitor", 200), "id")).contains(id);
        setActiveAs(99999L, INACTIVE, "test-admin", 404);
    }
}
