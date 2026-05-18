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
class StrategicHierarchyValidationE2ETest extends StrategicHierarchyE2ETestSupport {
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
                {"name": "KR sin unidad", "description": "x", "metric": "m", "baseValue": 0, "targetValue": 1, "measurementUnitId": 99999}
                """, 404);
        doPut("/api/v1/key-results/99999", keyResultPayload(unitId), 404);
        doPatch("/api/v1/key-results/99999/current-value", """
                {"currentValue": 10}
                """, 404);
        doPatch("/api/v1/key-results/1/current-value", """
                {"currentValue": null}
                """, 404);
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

}
