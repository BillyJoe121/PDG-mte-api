package co.edu.icesi.pdg.mte.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class PlannerLinkE2ETest extends StrategicHierarchyE2ETestSupport {
    private static final String PLAN = "https://tasks.office.com/icesi.edu.co/Home/PlanViews/abc123";
    private static final String ERROR_PREFIX = "El enlace de Planner no es valido";

    @Test
    void keyResultStoresClearsAndRejectsPlannerLinks() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long betId = createStrategicBet("Apuesta planner " + SEQUENCE.incrementAndGet());
        String payload = keyResultPayload("KR planner " + SEQUENCE.incrementAndGet(), periodId, null, betId, unitId);

        JsonNode created = doPost("/api/v1/key-results", withPlanner(payload, PLAN), 201);
        Long id = created.get("id").asLong();
        assertThat(created.get("plannerUrl").asText()).isEqualTo(PLAN);
        assertThat(doGet("/api/v1/key-results/" + id).get("plannerUrl").asText()).isEqualTo(PLAN);

        JsonNode rejectedOnCreate = doPost("/api/v1/key-results", withPlanner(payload, "http://tasks.office.com/plan"), 400);
        assertThat(rejectedOnCreate.get("message").asText()).startsWith(ERROR_PREFIX);
        JsonNode rejectedOnUpdate = doPut("/api/v1/key-results/" + id, withPlanner(payload, "https://evil.example/plan"), 400);
        assertThat(rejectedOnUpdate.get("message").asText()).startsWith(ERROR_PREFIX);
        assertThat(doGet("/api/v1/key-results/" + id).get("plannerUrl").asText()).isEqualTo(PLAN);

        JsonNode cleared = doPut("/api/v1/key-results/" + id, withPlanner(payload, "  "), 200);
        assertThat(cleared.get("plannerUrl").isNull()).isTrue();
    }

    @Test
    void projectStoresClearsAndRejectsPlannerLinks() throws Exception {
        Long departmentId = firstId("/api/v1/departments");
        Long keyResultId = keyResultRepository.findAll().stream().findFirst().orElseThrow().getId();
        String name = "Proyecto planner " + SEQUENCE.incrementAndGet();

        JsonNode created = doPost("/api/v1/projects", projectPayload(name, departmentId, keyResultId, PLAN), 201);
        Long id = created.get("id").asLong();
        assertThat(created.get("plannerUrl").asText()).isEqualTo(PLAN);
        assertThat(doGet("/api/v1/projects/" + id).get("plannerUrl").asText()).isEqualTo(PLAN);

        JsonNode rejectedOnCreate = doPost("/api/v1/projects",
                projectPayload(name, departmentId, keyResultId, "https://user@tasks.office.com/plan"), 400);
        assertThat(rejectedOnCreate.get("message").asText()).startsWith(ERROR_PREFIX);
        JsonNode rejectedOnUpdate = doPut("/api/v1/projects/" + id,
                projectPayload(name, departmentId, keyResultId, "https://tasks.office.com:8443/plan"), 400);
        assertThat(rejectedOnUpdate.get("message").asText()).startsWith(ERROR_PREFIX);

        JsonNode cleared = doPut("/api/v1/projects/" + id, projectPayload(name, departmentId, keyResultId, ""), 200);
        assertThat(cleared.get("plannerUrl").isNull()).isTrue();
    }

    private String withPlanner(String payload, String plannerUrl) throws Exception {
        ObjectNode node = (ObjectNode) objectMapper.readTree(payload);
        node.put("plannerUrl", plannerUrl);
        return objectMapper.writeValueAsString(node);
    }

    private String projectPayload(String name, Long departmentId, Long keyResultId, String plannerUrl) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("description", "Proyecto con enlace de Planner.");
        node.put("type", "INVESTIGACION");
        node.put("departmentId", departmentId);
        node.put("status", "ACTIVO");
        node.put("startPeriod", "2026-Q1");
        node.put("endPeriod", "2026-Q2");
        node.put("keyResultId", keyResultId);
        node.put("linkStatus", "ACTIVO");
        node.put("plannerUrl", plannerUrl);
        node.putArray("tutors").add("Tutora A");
        return objectMapper.writeValueAsString(node);
    }
}
