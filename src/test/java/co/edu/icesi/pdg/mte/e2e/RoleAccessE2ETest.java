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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExternalAuthClient externalAuthClient;

    @BeforeEach
    void setUp() {
        when(externalAuthClient.introspect(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (token.contains("prefixed-admin")) {
                return user("ROLE_ADMIN");
            }
            if (token.contains("admin")) {
                return user("ADMIN");
            }
            if (token.contains("decano")) {
                return user("DECANO");
            }
            if (token.contains("director")) {
                return user("DIRECTOR_ESCUELA");
            }
            if (token.contains("jefe")) {
                return user("JEFE_DPTO");
            }
            if (token.contains("unknown")) {
                return user("INVITADO");
            }
            return user("PROFESOR");
        });
    }

    @Test
    void professorCanSeeContextDashboardAndCreateProjectsButCannotAdministerOrReport() throws Exception {
        Long departmentId = firstId("/api/v1/departments", "profesor-token");

        JsonNode me = getJson("/api/v1/auth/me", "profesor-token", 200);
        getJson("/api/v1/dashboard/summary", "profesor-token", 200);
        JsonNode created = postJson("/api/v1/projects", projectPayload(departmentId), "profesor-token", 201);

        assertThat(me.get("user").get("roles").toString()).contains("PROFESOR");
        assertThat(me.get("capabilities").get("createProjects").asBoolean()).isTrue();
        assertThat(me.get("capabilities").get("manageCatalogs").asBoolean()).isFalse();
        assertThat(me.get("capabilities").get("viewReports").asBoolean()).isFalse();
        assertThat(created.get("id").asLong()).isPositive();

        postJson("/api/v1/measurement-units", """
                {"name": "Unidad profesor %d", "type": "NUMERICA"}
                """.formatted(SEQUENCE.incrementAndGet()), "profesor-token", 403);
        getJson("/api/v1/reports/general", "profesor-token", 403);
        getJson("/api/v1/presentation", "profesor-token", 403);
    }

    @Test
    void professorCannotMutateStrategyOrCreateStrategicLinks() throws Exception {
        Scenario scenario = createScenario("director-token");

        postJson("/api/v1/strategic-bets", """
                {
                  "name": "Apuesta profesor bloqueada %d",
                  "description": "No debe crearse"
                }
                """.formatted(SEQUENCE.incrementAndGet()), "profesor-token", 403);
        postJson("/api/v1/goals", """
                {
                  "name": "Meta profesor bloqueada %d",
                  "description": "No debe crearse",
                  "expectedValue": 100,
                  "measurementUnitId": %d
                }
                """.formatted(SEQUENCE.incrementAndGet(), firstId("/api/v1/measurement-units", "director-token")), "profesor-token", 403);
        patchJson("/api/v1/objectives/" + scenario.objectiveId(), """
                {"name": "Cambio profesor", "description": "No autorizado"}
                """, "profesor-token", 403);
        postJson("/api/v1/objectives/" + scenario.objectiveId() + "/key-results", """
                {
                  "name": "KR profesor bloqueado",
                  "description": "No autorizado",
                  "metric": "Cobertura",
                  "baseValue": 0,
                  "targetValue": 10,
                  "measurementUnitId": %d
                }
                """.formatted(firstId("/api/v1/measurement-units", "director-token")), "profesor-token", 403);
        postJson("/api/v1/project-key-result-links", """
                {"projectId": %d, "keyResultId": %d, "contributionWeight": 40, "contributionType": "DIRECTA"}
                """.formatted(scenario.projectId(), scenario.keyResultId()), "profesor-token", 403);
    }

    @Test
    void jefeCanLinkProjectsToKeyResultsAndViewReportsButCannotChangeStatusOrPresent() throws Exception {
        Scenario scenario = createScenario("director-token");

        JsonNode link = postJson("/api/v1/project-key-result-links", """
                {"projectId": %d, "keyResultId": %d, "contributionWeight": 40, "contributionType": "DIRECTA"}
                """.formatted(scenario.projectId(), scenario.keyResultId()), "jefe-token", 201);
        getJson("/api/v1/reports/general?period=2026-1", "jefe-token", 200);
        patchJson("/api/v1/projects/" + scenario.projectId() + "/status", """
                {"status": "FINALIZADO"}
                """, "jefe-token", 403);
        getJson("/api/v1/presentation", "jefe-token", 403);
        postJson("/api/v1/goals", """
                {
                  "name": "Meta jefe bloqueada %d",
                  "description": "No debe crearse",
                  "expectedValue": 100,
                  "measurementUnitId": %d
                }
                """.formatted(SEQUENCE.incrementAndGet(), firstId("/api/v1/measurement-units", "director-token")), "jefe-token", 403);

        assertThat(link.get("contributionWeight").decimalValue()).isEqualByComparingTo("40");
    }

    @Test
    void decanoCanUseExecutiveOperationsButCannotManageCatalogsOrAuditLogs() throws Exception {
        Long departmentId = firstId("/api/v1/departments", "decano-token");
        JsonNode bet = postJson("/api/v1/strategic-bets", """
                {
                  "name": "Apuesta decano %d",
                  "description": "Decision directiva",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(SEQUENCE.incrementAndGet()), "decano-token", 201);
        Scenario scenario = createScenario("decano-token");

        patchJson("/api/v1/projects/" + scenario.projectId() + "/status", """
                {"status": "FINALIZADO"}
                """, "decano-token", 200);
        patchJson("/api/v1/projects/" + scenario.projectId() + "/status", """
                {"status": "ACTIVO"}
                """, "decano-token", 403);
        getJson("/api/v1/reports/general", "decano-token", 200);
        getJson("/api/v1/presentation", "decano-token", 200);
        getJson("/api/v1/audit-logs", "decano-token", 403);
        postJson("/api/v1/measurement-units", """
                {"name": "Unidad decano %d", "type": "NUMERICA"}
                """.formatted(SEQUENCE.incrementAndGet()), "decano-token", 403);

        assertThat(bet.get("id").asLong()).isPositive();
    }

    @Test
    void directorCanUpdateStrategicBet() throws Exception {
        Long betId = postJson("/api/v1/strategic-bets", """
                {
                  "name": "Apuesta director editable %d",
                  "description": "Decision directiva",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(SEQUENCE.incrementAndGet()), "director-token", 201).get("id").asLong();

        JsonNode updated = putJson("/api/v1/strategic-bets/" + betId, """
                {
                  "name": "Apuesta director editada %d",
                  "description": "Decision directiva actualizada",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(SEQUENCE.incrementAndGet()), "director-token", 200);

        assertThat(updated.get("id").asLong()).isEqualTo(betId);
        assertThat(updated.get("description").asText()).isEqualTo("Decision directiva actualizada");
    }

    @Test
    void adminCanManageCatalogsAndAuditLogs() throws Exception {
        JsonNode unit = postJson("/api/v1/measurement-units", """
                {"name": "Unidad admin %d", "type": "NUMERICA", "description": "Administrada"}
                """.formatted(SEQUENCE.incrementAndGet()), "admin-token", 201);
        JsonNode auditLogs = getJson("/api/v1/audit-logs", "admin-token", 200);

        assertThat(unit.get("id").asLong()).isPositive();
        assertThat(auditLogs).isNotNull();
    }

    @Test
    void prefixedAdminRoleIsAcceptedByMethodSecurityAndAuthContext() throws Exception {
        JsonNode me = getJson("/api/v1/auth/me", "prefixed-admin-token", 200);
        JsonNode unit = postJson("/api/v1/measurement-units", """
                {"name": "Unidad admin prefijo %d", "type": "NUMERICA"}
                """.formatted(SEQUENCE.incrementAndGet()), "prefixed-admin-token", 201);
        getJson("/api/v1/audit-logs/summary", "prefixed-admin-token", 200);

        assertThat(me.get("user").get("roles").toString()).contains("ADMIN");
        assertThat(me.get("capabilities").get("manageCatalogs").asBoolean()).isTrue();
        assertThat(unit.get("id").asLong()).isPositive();
    }

    @Test
    void unknownAuthenticatedRoleReceivesNoCapabilitiesAndCannotUseProtectedOperations() throws Exception {
        Long departmentId = firstId("/api/v1/departments", "director-token");

        JsonNode me = getJson("/api/v1/auth/me", "unknown-token", 200);
        getJson("/api/v1/dashboard/summary", "unknown-token", 403);
        getJson("/api/v1/reports/general", "unknown-token", 403);
        getJson("/api/v1/presentation", "unknown-token", 403);
        getJson("/api/v1/audit-logs/summary", "unknown-token", 403);
        postJson("/api/v1/projects", projectPayload(departmentId), "unknown-token", 403);
        postJson("/api/v1/measurement-units", """
                {"name": "Unidad desconocida %d", "type": "NUMERICA"}
                """.formatted(SEQUENCE.incrementAndGet()), "unknown-token", 403);

        assertThat(me.get("user").get("roles").toString()).contains("INVITADO");
        me.get("capabilities").elements().forEachRemaining(capability -> assertThat(capability.asBoolean()).isFalse());
    }

    private Scenario createScenario(String token) throws Exception {
        Long unitId = firstId("/api/v1/measurement-units", token);
        Long periodId = firstId("/api/v1/academic-periods", token);
        Long departmentId = firstId("/api/v1/departments", token);
        Long betId = postJson("/api/v1/strategic-bets", """
                {
                  "name": "Apuesta rol %d",
                  "description": "Apuesta para permisos",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(SEQUENCE.incrementAndGet()), token, 201).get("id").asLong();
        Long goalId = postJson("/api/v1/goals", """
                {
                  "name": "Meta rol %d",
                  "description": "Meta para permisos",
                  "expectedValue": 100,
                  "measurementUnitId": %d
                }
                """.formatted(SEQUENCE.incrementAndGet(), unitId), token, 201).get("id").asLong();
        JsonNode objective = postJson("/api/v1/objectives", """
                {
                  "name": "Objetivo rol %d",
                  "description": "Objetivo para permisos",
                  "departmentId": %d,
                  "academicPeriodId": %d,
                  "goalId": %d,
                  "strategicBetId": %d,
                  "keyResults": [{
                    "name": "KR permisos",
                    "description": "KR para permisos",
                    "metric": "Cobertura",
                    "baseValue": 0,
                    "targetValue": 100,
                    "measurementUnitId": %d
                  }]
                }
                """.formatted(SEQUENCE.incrementAndGet(), departmentId, periodId, goalId, betId, unitId), token, 201);
        JsonNode project = postJson("/api/v1/projects", projectPayload(departmentId), token, 201);
        return new Scenario(
                project.get("id").asLong(),
                objective.get("id").asLong(),
                objective.get("keyResults").get(0).get("id").asLong()
        );
    }

    private String projectPayload(Long departmentId) {
        return """
                {
                  "name": "Proyecto rol %d",
                  "description": "Proyecto para validar permisos",
                  "type": "INVESTIGACION",
                  "departmentId": %d,
                  "status": "ACTIVO",
                  "startPeriod": "2026-1",
                  "endPeriod": "2026-1"
                }
                """.formatted(SEQUENCE.incrementAndGet(), departmentId);
    }

    private Long firstId(String url, String token) throws Exception {
        return getJson(url, token, 200).get(0).get("id").asLong();
    }

    private JsonNode getJson(String url, String token, int status) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().is(status))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode postJson(String url, String payload, String token, int status) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(status))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode patchJson(String url, String payload, String token, int status) throws Exception {
        String body = mockMvc.perform(patch(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(status))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode putJson(String url, String payload, String token, int status) throws Exception {
        String body = mockMvc.perform(put(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(status))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private ExternalUserContext user(String role) {
        return new ExternalUserContext(
                (long) Math.abs(role.hashCode()),
                role.toLowerCase(),
                role.toLowerCase() + "@icesi.edu.co",
                List.of(role),
                List.of("MTE_READ"),
                99L,
                "Usuario " + role,
                "Departamento de Computaci\u00f3n y Sistemas inteligentes."
        );
    }

    private record Scenario(Long projectId, Long objectiveId, Long keyResultId) {
    }
}
