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
    void sadPathsForStrategicBetGoalAndKeyResultCreationReturnExpectedErrors() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
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
        doPost("/api/v1/key-results", keyResultPayload("KR sin periodo", 99999L, goalId, betId, unitId), 404);
        doPost("/api/v1/key-results", keyResultPayload("KR sin meta", periodId, 99999L, betId, unitId), 404);
        doPost("/api/v1/key-results", keyResultPayload("KR sin apuesta", periodId, goalId, 99999L, unitId), 404);
        doPost("/api/v1/key-results", keyResultPayload("KR sin unidad", periodId, goalId, betId, 99999L), 404);
        JsonNode unlinked = doPost("/api/v1/key-results", keyResultPayload("KR sin vinculos", periodId, null, null, unitId), 400);
        assertThat(unlinked.get("message").asText()).contains("Meta o a una Apuesta");
        doPost("/api/v1/key-results", keyResultPayload("", periodId, goalId, betId, unitId), 400);
        doPost("/api/v1/key-results", """
                {"name": "KR sin periodo", "description": "x", "metric": "m", "baseValue": 0, "targetValue": 1,
                 "measurementUnitId": %d, "goalIds": [%d]}
                """.formatted(unitId, goalId), 400);
    }

    @Test
    void sadPathsForKeyResultManagementReturnExpectedErrors() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long periodId = firstId("/api/v1/academic-periods");
        Long betId = createStrategicBet("Apuesta KR " + SEQUENCE.incrementAndGet());
        Long goalId = createGoal("Meta KR " + SEQUENCE.incrementAndGet(), unitId);
        Long keyResultId = createKeyResult("KR gestion " + SEQUENCE.incrementAndGet(), periodId, goalId, betId, unitId)
                .get("id").asLong();

        doPut("/api/v1/key-results/" + keyResultId, """
                {"description": "", "metric": "m", "baseValue": 0, "targetValue": 1, "measurementUnitId": %d,
                 "academicPeriodId": %d, "goalIds": [%d]}
                """.formatted(unitId, periodId, goalId), 400);
        doPut("/api/v1/key-results/" + keyResultId, keyResultPayload("KR sin vinculos", periodId, null, null, unitId), 400);
        doPut("/api/v1/key-results/99999", keyResultPayload("KR inexistente", periodId, goalId, betId, unitId), 404);
        doGet("/api/v1/key-results/" + keyResultId);
        mockMvc.perform(get("/api/v1/key-results/{id}", 99999L))
                .andExpect(status().isNotFound());
        doPatch("/api/v1/key-results/99999/current-value", """
                {"currentValue": 10}
                """, 404);
        doPatch("/api/v1/key-results/1/current-value", """
                {"currentValue": null}
                """, 400);
        doPatch("/api/v1/key-results/" + keyResultId + "/status", """
                {"status": "CERRADO"}
                """, 409);
        mockMvc.perform(get("/api/v1/key-results").param("goalId", "abc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/key-results/{id}", 99999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicHealthAndProtectedEndpointsRespectAuthModeContract() throws Exception {
        assertThat(doGet("/api/v1/health").get("status").asText()).isEqualTo("UP");
        assertThat(doGet("/api/v1/departments")).isNotEmpty();
    }

}
