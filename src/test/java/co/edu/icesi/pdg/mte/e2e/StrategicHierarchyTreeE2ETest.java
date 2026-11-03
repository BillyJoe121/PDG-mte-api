package co.edu.icesi.pdg.mte.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StrategicHierarchyTreeE2ETest extends StrategicHierarchyE2ETestSupport {
    @Test
    void happyPathShowsKeyResultsDirectlyUnderBetsAndGoalsInHierarchyTree() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long strategicBetId = createStrategicBet("Apuesta arbol " + SEQUENCE.incrementAndGet());
        Long goalId = createGoal("Meta arbol " + SEQUENCE.incrementAndGet(), unitId);
        Long keyResultId = createKeyResult("KR arbol " + SEQUENCE.incrementAndGet(), periodId, goalId, strategicBetId, unitId)
                .get("id").asLong();
        createProjectLink(keyResultId, 9001L);

        JsonNode tree = doGet("/api/v1/strategic-hierarchy/tree");

        assertThat(tree.findValues("nodeType").stream().map(JsonNode::asText))
                .contains("STRATEGIC_BET", "GOAL", "KEY_RESULT", "PROJECT")
                .doesNotContain("OBJECTIVE");
        for (String rootType : java.util.List.of("STRATEGIC_BET", "GOAL")) {
            Long ownerId = "GOAL".equals(rootType) ? goalId : strategicBetId;
            JsonNode root = java.util.stream.StreamSupport.stream(tree.spliterator(), false)
                    .filter(node -> rootType.equals(node.get("nodeType").asText()))
                    .filter(node -> ownerId.toString().equals(node.get("id").asText()))
                    .findFirst()
                    .orElseThrow();
            JsonNode keyResultNode = root.get("children").get(0);
            assertThat(keyResultNode.get("nodeType").asText()).isEqualTo("KEY_RESULT");
            assertThat(keyResultNode.get("id").asText()).isEqualTo(keyResultId.toString());
            assertThat(keyResultNode.get("children").get(0).get("nodeType").asText()).isEqualTo("PROJECT");
        }
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
                {"name": "2026-2", "startDate": "2026-06-01", "endDate": "2026-01-01", "status": "ACTIVO"}
                """, 400);
        for (String invalidName : java.util.List.of("Periodo 2026", "2026-3", "26-1", "2026-Q1")) {
            JsonNode error = doPost("/api/v1/academic-periods", """
                    {"name": "%s", "startDate": "2031-01-01", "endDate": "2031-06-01", "status": "PLANIFICACION"}
                    """.formatted(invalidName), 400);
            assertThat(error.get("details").get(0).asText())
                    .isEqualTo("name: El nombre del periodo debe tener formato AAAA-1 o AAAA-2.");
        }
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
                {"name": "%d-1", "startDate": "2299-01-01", "endDate": "2299-06-01", "status": "PLANIFICACION"}
                """.formatted(4000 + SEQUENCE.incrementAndGet()), 201).get("id").asLong();
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
                {"name": "2026-2", "startDate": "2026-01-01", "endDate": "2026-06-01", "status": "ACTIVO"}
                """, 404);
        doPut("/api/v1/academic-periods/" + usedPeriodId, """
                {"name": "2026-2", "startDate": "2026-06-01", "endDate": "2026-01-01", "status": "ACTIVO"}
                """, 400);
        JsonNode renameError = doPut("/api/v1/academic-periods/" + usedPeriodId, """
                {"name": "Periodo renombrado", "startDate": "2299-01-01", "endDate": "2299-06-01", "status": "PLANIFICACION"}
                """, 400);
        assertThat(renameError.get("details").get(0).asText())
                .isEqualTo("name: El nombre del periodo debe tener formato AAAA-1 o AAAA-2.");
        doPut("/api/v1/academic-periods/" + usedPeriodId, """
                {"name": "2026-1", "startDate": "2026-01-01", "endDate": "2026-06-01", "status": "ACTIVO"}
                """, 409);
        doPatch("/api/v1/academic-periods/" + periodId + "/status", "{}", 400);
        doPatch("/api/v1/academic-periods/" + periodId + "/active", "{}", 400);
        doDelete("/api/v1/academic-periods/" + usedPeriodId, 409);
        doDelete("/api/v1/academic-periods/99999", 404);
    }

}
