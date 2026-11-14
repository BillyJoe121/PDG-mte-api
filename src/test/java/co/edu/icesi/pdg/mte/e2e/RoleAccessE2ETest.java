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

@SpringBootTest
@AutoConfigureMockMvc
class RoleAccessE2ETest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void managerCanManageStrategyProjectsAndViewReports() throws Exception {
        Scenario scenario = createScenarioAsAdmin();
        JsonNode me = getJson("/api/v1/auth/me", "test-manager", 200);
        JsonNode updated = putJson("/api/v1/key-results/" + scenario.keyResultId(),
                keyResultPayload("KR actualizado por manager", scenario), "test-manager", 200);
        JsonNode created = postJson("/api/v1/key-results", keyResultPayload("KR creado por manager", scenario),
                "test-manager", 201);
        getJson("/api/v1/objectives", "test-manager", 404);
        getJson("/api/v1/reports/general", "test-manager", 200);
        getJson("/api/v1/reports/key-results/ranking", "test-manager", 200);
        getJson("/api/v1/reports/objectives/ranking", "test-manager", 404);
        getJson("/api/v1/presentation", "test-manager", 200);

        assertThat(updated.get("name").asText()).startsWith("KR actualizado por manager");
        assertThat(created.get("goals").get(0).get("id").asLong()).isEqualTo(scenario.goalId());
        assertThat(created.get("strategicBets").get(0).get("id").asLong()).isEqualTo(scenario.betId());
        assertThat(me.get("user").get("roles").toString()).contains("MANAGER");
        assertThat(me.get("capabilities").has("manageObjectives")).isFalse();
        assertThat(me.get("capabilities").get("manageKeyResults").asBoolean()).isTrue();
        assertThat(me.get("capabilities").get("viewReports").asBoolean()).isTrue();
    }

    @Test
    void managerCannotManagePlatformAdministration() throws Exception {
        Long departmentId = firstId("/api/v1/departments", "test-admin");
        postJson("/api/v1/measurement-units", """
                {"name":"Unidad bloqueada %d","type":"NUMERICA"}
                """.formatted(SEQUENCE.incrementAndGet()), "test-manager", 403);
        postJson("/api/v1/strategic-bets", """
                {"name":"Apuesta manager %d","description":"Autorizada"}
                """.formatted(SEQUENCE.incrementAndGet()), "test-manager", 201);
        postJson("/api/v1/projects", projectPayload(departmentId, createScenarioAsAdmin().keyResultId()), "test-manager", 201);
        getJson("/api/v1/audit-logs", "test-manager", 403);
    }

    @Test
    void contributorCanConsultAndRegisterProgressButCannotManage() throws Exception {
        Scenario scenario = createScenarioAsAdmin();
        JsonNode me = getJson("/api/v1/auth/me", "test-contributor", 200);

        getJson("/api/v1/projects", "test-contributor", 200);
        postJson("/api/v1/projects/" + scenario.projectId() + "/progress", """
                {"progressPercent":25,"comment":"Avance con evidencia","milestones":"Evidencia cargada"}
                """, "test-contributor", 201);
        putJson("/api/v1/key-results/" + scenario.keyResultId(), keyResultPayload("KR bloqueado", scenario),
                "test-contributor", 403);
        postJson("/api/v1/key-results", keyResultPayload("KR no autorizado", scenario), "test-contributor", 403);
        getJson("/api/v1/reports/general", "test-contributor", 403);
        getJson("/api/v1/reports", "test-contributor", 200);
        getJson("/api/v1/presentation", "test-contributor", 200);

        Long departmentId = firstId("/api/v1/departments", "test-admin");
        JsonNode created = postJson("/api/v1/projects", projectPayload(departmentId, scenario.keyResultId()), "test-contributor", 201);
        Long createdId = created.get("id").asLong();
        JsonNode edited = putJson("/api/v1/projects/" + createdId, """
                {"name":"Iniciativa editada %d","description":"Colaborador","type":"INVESTIGACION","departmentId":%d,
                 "keyResultId":%d,"startPeriod":"2026-1","endPeriod":"2026-2"}
                """.formatted(SEQUENCE.incrementAndGet(), departmentId, scenario.keyResultId()), "test-contributor", 200);
        Long otherKeyResultId = postJson("/api/v1/key-results", keyResultPayload("KR para vincular", scenario), "test-admin", 201)
                .get("id").asLong();
        JsonNode link = postJson("/api/v1/project-key-result-links", """
                {"projectId":%d,"keyResultId":%d}
                """.formatted(createdId, otherKeyResultId), "test-contributor", 201);
        deleteRequest("/api/v1/project-key-result-links/" + link.get("id").asLong(), "test-contributor", 204);
        patchJson("/api/v1/projects/" + createdId + "/status", """
                {"status":"ACTIVO"}
                """, "test-contributor", 403);
        postJson("/api/v1/projects/sync/trayectoria", "{}", "test-contributor", 403);

        assertThat(me.get("user").get("roles").toString()).contains("CONTRIBUTOR");
        assertThat(me.get("capabilities").get("registerProjectProgress").asBoolean()).isTrue();
        assertThat(me.get("capabilities").get("manageKeyResults").asBoolean()).isFalse();
        assertThat(me.get("capabilities").has("manageObjectives")).isFalse();
        assertThat(me.get("capabilities").get("createProjects").asBoolean()).isTrue();
        assertThat(me.get("capabilities").get("updateProjects").asBoolean()).isTrue();
        assertThat(me.get("capabilities").get("linkProjectsToKeyResults").asBoolean()).isTrue();
        assertThat(me.get("capabilities").get("changeProjectStatus").asBoolean()).isFalse();
        // The requested ACTIVO status is ignored: only status managers choose it.
        assertThat(created.get("status").asText()).isEqualTo("BORRADOR");
        assertThat(edited.get("name").asText()).startsWith("Iniciativa editada");
    }

    @Test
    void adminCanManageCatalogsProjectsStrategyAndAudit() throws Exception {
        Long departmentId = firstId("/api/v1/departments", "test-admin");
        JsonNode unit = postJson("/api/v1/measurement-units", """
                {"name":"Unidad admin %d","type":"NUMERICA"}
                """.formatted(SEQUENCE.incrementAndGet()), "test-admin", 201);
        JsonNode period = postJson("/api/v1/academic-periods", """
                {"name":"%d-1","startDate":"2030-01-01","endDate":"2030-06-30","status":"PLANIFICACION"}
                """.formatted(5000 + SEQUENCE.incrementAndGet()), "test-admin", 201);
        JsonNode project = postJson("/api/v1/projects", projectPayload(departmentId, createScenarioAsAdmin().keyResultId()),
                "test-admin", 201);
        JsonNode bet = postJson("/api/v1/strategic-bets", """
                {"name":"Apuesta admin %d","description":"Administrada"}
                """.formatted(SEQUENCE.incrementAndGet()), "test-admin", 201);
        getJson("/api/v1/audit-logs", "test-admin", 200);
        assertThat(unit.get("id").asLong()).isPositive();
        assertThat(period.get("id").asLong()).isPositive();
        assertThat(project.get("id").asLong()).isPositive();
        assertThat(bet.get("id").asLong()).isPositive();
    }

    @Test
    void visitorCanOnlyRead() throws Exception {
        Scenario scenario = createScenarioAsAdmin();
        JsonNode me = getJson("/api/v1/auth/me", "test-visitor", 200);

        getJson("/api/v1/projects", "test-visitor", 200);
        getJson("/api/v1/key-results/" + scenario.keyResultId(), "test-visitor", 200);
        postJson("/api/v1/projects/" + scenario.projectId() + "/progress", """
                {"progressPercent":25,"comment":"Intento de visitante","milestones":"Ninguno"}
                """, "test-visitor", 403);
        putJson("/api/v1/key-results/" + scenario.keyResultId(), keyResultPayload("KR visitante", scenario),
                "test-visitor", 403);
        patchJson("/api/v1/projects/" + scenario.projectId() + "/status", """
                {"status":"SUSPENDIDO"}
                """, "test-visitor", 403);
        postJson("/api/v1/projects", projectPayload(firstId("/api/v1/departments", "test-admin"), scenario.keyResultId()),
                "test-visitor", 403);
        getJson("/api/v1/reports", "test-visitor", 200);
        getJson("/api/v1/reports/general", "test-visitor", 403);

        assertThat(me.get("user").get("roles").toString()).isEqualTo("[\"VISITOR\"]");
        assertThat(me.get("capabilities").get("registerProjectProgress").asBoolean()).isFalse();
        assertThat(me.get("capabilities").get("manageKeyResults").asBoolean()).isFalse();
    }

    @Test
    void requestsWithoutValidSessionAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/projects").header("Authorization", ""))
                .andExpect(status().isUnauthorized());
        getJson("/api/v1/projects", "token-desconocido", 401);
        mockMvc.perform(get("/api/v1/health").header("Authorization", ""))
                .andExpect(status().isOk());
    }

    private Scenario createScenarioAsAdmin() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units", "test-admin");
        Long periodId = firstId("/api/v1/academic-periods", "test-admin");
        Long departmentId = firstId("/api/v1/departments", "test-admin");
        Long betId = postJson("/api/v1/strategic-bets", """
                {"name":"Apuesta rol %d","description":"Base"}
                """.formatted(SEQUENCE.incrementAndGet()), "test-admin", 201).get("id").asLong();
        Long goalId = postJson("/api/v1/goals", """
                {"name":"Meta rol %d","description":"Base","expectedValue":100,"measurementUnitId":%d}
                """.formatted(SEQUENCE.incrementAndGet(), unitId), "test-admin", 201).get("id").asLong();
        Scenario scenario = new Scenario(null, unitId, periodId, goalId, betId, null);
        Long keyResultId = postJson("/api/v1/key-results", keyResultPayload("KR rol inicial", scenario), "test-admin", 201)
                .get("id").asLong();
        Long projectId = postJson("/api/v1/projects", projectPayload(departmentId, keyResultId), "test-admin", 201)
                .get("id").asLong();
        return new Scenario(keyResultId, unitId, periodId, goalId, betId, projectId);
    }

    private String keyResultPayload(String name, Scenario scenario) {
        return """
                {"name":"%s %d","description":"Base","metric":"Cobertura","baseValue":0,"targetValue":100,
                 "measurementUnitId":%d,"academicPeriodId":%d,"goalIds":[%d],"strategicBetIds":[%d]}
                """.formatted(name, SEQUENCE.incrementAndGet(), scenario.unitId(), scenario.periodId(),
                scenario.goalId(), scenario.betId());
    }

    private String projectPayload(Long departmentId, Long keyResultId) {
        return """
                {"name":"Proyecto rol %d","description":"Permisos","type":"INVESTIGACION","departmentId":%d,
                 "keyResultId":%d,"status":"ACTIVO","startPeriod":"2026-1","endPeriod":"2026-1"}
                """.formatted(SEQUENCE.incrementAndGet(), departmentId, keyResultId);
    }

    private Long firstId(String url, String token) throws Exception {
        return getJson(url, token, 200).get(0).get("id").asLong();
    }

    private JsonNode getJson(String url, String token, int expectedStatus) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode postJson(String url, String payload, String token, int expectedStatus) throws Exception {
        String body = mockMvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode putJson(String url, String payload, String token, int expectedStatus) throws Exception {
        String body = mockMvc.perform(put(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode patchJson(String url, String payload, String token, int expectedStatus) throws Exception {
        String body = mockMvc.perform(patch(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private void deleteRequest(String url, String token, int expectedStatus) throws Exception {
        mockMvc.perform(delete(url).header("Authorization", "Bearer " + token)).andExpect(status().is(expectedStatus));
    }

    private record Scenario(Long keyResultId, Long unitId, Long periodId, Long goalId, Long betId, Long projectId) {}
}
