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
                {"name": "2299-%d", "startDate": "2299-01-01", "endDate": "2299-06-01", "status": "PLANIFICACION"}
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
        doPatch("/api/v1/academic-periods/" + periodId + "/active", "{}", 400);
        doDelete("/api/v1/academic-periods/" + usedPeriodId, 409);
        doDelete("/api/v1/academic-periods/99999", 404);
    }

}
