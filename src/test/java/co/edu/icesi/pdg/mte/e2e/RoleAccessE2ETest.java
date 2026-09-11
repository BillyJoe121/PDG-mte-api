package co.edu.icesi.pdg.mte.e2e;

import co.edu.icesi.pdg.mte.security.ExternalAuthClient;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "mte.auth.mode=external")
@AutoConfigureMockMvc
class RoleAccessE2ETest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private ExternalAuthClient externalAuthClient;

    @BeforeEach
    void setUp() {
        when(externalAuthClient.introspect(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token.contains("admin")) return user("ADMIN");
            if (token.contains("legacy")) return user("PROFESOR");
            return user("USER");
        });
    }

    @Test
    void userCanManageObjectivesAndKeyResultsAndViewReports() throws Exception {
        Scenario scenario = createScenarioAsAdmin();
        JsonNode me = getJson("/api/v1/auth/me", "user-token", 200);
        patchJson("/api/v1/objectives/" + scenario.objectiveId(),
                "{\"name\":\"Objetivo actualizado por user\",\"description\":\"Edición permitida\"}",
                "user-token", 200);
        postJson("/api/v1/objectives/" + scenario.objectiveId() + "/key-results", """
                {"name":"KR creado por user","description":"Gestión permitida","metric":"Cobertura",
                 "baseValue":0,"targetValue":10,"measurementUnitId":%d}
                """.formatted(scenario.unitId()), "user-token", 201);
        getJson("/api/v1/reports/general", "user-token", 200);
        getJson("/api/v1/presentation", "user-token", 200);

        assertThat(me.get("user").get("roles").toString()).contains("USER");
        assertThat(me.get("capabilities").get("manageObjectives").asBoolean()).isTrue();
        assertThat(me.get("capabilities").get("manageKeyResults").asBoolean()).isTrue();
        assertThat(me.get("capabilities").get("viewReports").asBoolean()).isTrue();
    }

    @Test
    void userCannotManageAdminOnlyResources() throws Exception {
        Long departmentId = firstId("/api/v1/departments", "admin-token");
        postJson("/api/v1/measurement-units", """
                {"name":"Unidad bloqueada %d","type":"NUMERICA"}
                """.formatted(SEQUENCE.incrementAndGet()), "user-token", 403);
        postJson("/api/v1/strategic-bets", """
                {"name":"Apuesta bloqueada %d","description":"No autorizada"}
                """.formatted(SEQUENCE.incrementAndGet()), "user-token", 403);
        postJson("/api/v1/projects", projectPayload(departmentId), "user-token", 403);
        getJson("/api/v1/audit-logs", "user-token", 403);
    }

    @Test
    void adminCanManageCatalogsProjectsStrategyAndAudit() throws Exception {
        Long departmentId = firstId("/api/v1/departments", "admin-token");
        JsonNode unit = postJson("/api/v1/measurement-units", """
                {"name":"Unidad admin %d","type":"NUMERICA"}
                """.formatted(SEQUENCE.incrementAndGet()), "admin-token", 201);
        JsonNode period = postJson("/api/v1/academic-periods", """
                {"name":"Periodo admin %d","startDate":"2030-01-01","endDate":"2030-06-30","status":"PLANIFICACION"}
                """.formatted(SEQUENCE.incrementAndGet()), "admin-token", 201);
        JsonNode project = postJson("/api/v1/projects", projectPayload(departmentId), "admin-token", 201);
        JsonNode bet = postJson("/api/v1/strategic-bets", """
                {"name":"Apuesta admin %d","description":"Administrada"}
                """.formatted(SEQUENCE.incrementAndGet()), "admin-token", 201);
        getJson("/api/v1/audit-logs", "admin-token", 200);
        assertThat(unit.get("id").asLong()).isPositive();
        assertThat(period.get("id").asLong()).isPositive();
        assertThat(project.get("id").asLong()).isPositive();
        assertThat(bet.get("id").asLong()).isPositive();
    }

    @Test
    void legacyInstitutionalRolesCollapseToUser() throws Exception {
        JsonNode me = getJson("/api/v1/auth/me", "legacy-token", 200);
        assertThat(me.get("user").get("roles").toString()).isEqualTo("[\"USER\"]");
        assertThat(me.get("capabilities").get("manageObjectives").asBoolean()).isTrue();
        assertThat(me.get("capabilities").get("manageCatalogs").asBoolean()).isFalse();
    }

    private Scenario createScenarioAsAdmin() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units", "admin-token");
        Long periodId = firstId("/api/v1/academic-periods", "admin-token");
        Long departmentId = firstId("/api/v1/departments", "admin-token");
        Long betId = postJson("/api/v1/strategic-bets", """
                {"name":"Apuesta rol %d","description":"Base"}
                """.formatted(SEQUENCE.incrementAndGet()), "admin-token", 201).get("id").asLong();
        Long goalId = postJson("/api/v1/goals", """
                {"name":"Meta rol %d","description":"Base","expectedValue":100,"measurementUnitId":%d}
                """.formatted(SEQUENCE.incrementAndGet(), unitId), "admin-token", 201).get("id").asLong();
        JsonNode objective = postJson("/api/v1/objectives", """
                {"name":"Objetivo rol %d","description":"Base","departmentId":%d,"academicPeriodId":%d,
                 "goalId":%d,"strategicBetId":%d,"keyResults":[{"name":"KR inicial","description":"Base",
                 "metric":"Cobertura","baseValue":0,"targetValue":100,"measurementUnitId":%d}]}
                """.formatted(SEQUENCE.incrementAndGet(), departmentId, periodId, goalId, betId, unitId), "admin-token", 201);
        return new Scenario(objective.get("id").asLong(), unitId);
    }

    private String projectPayload(Long departmentId) {
        return """
                {"name":"Proyecto rol %d","description":"Permisos","type":"INVESTIGACION","departmentId":%d,
                 "status":"ACTIVO","startPeriod":"2026-1","endPeriod":"2026-1"}
                """.formatted(SEQUENCE.incrementAndGet(), departmentId);
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

    private JsonNode patchJson(String url, String payload, String token, int expectedStatus) throws Exception {
        String body = mockMvc.perform(patch(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private ExternalUserContext user(String role) {
        return new ExternalUserContext((long) Math.abs(role.hashCode()), role.toLowerCase(),
                role.toLowerCase() + "@icesi.edu.co", List.of(role), List.of("MTE_READ"),
                99L, "Usuario " + role, "DCSI");
    }

    private record Scenario(Long objectiveId, Long unitId) {}
}
