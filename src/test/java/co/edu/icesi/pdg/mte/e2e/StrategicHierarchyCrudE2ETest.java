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
class StrategicHierarchyCrudE2ETest extends StrategicHierarchyE2ETestSupport {
    @Test
    void seedDataContainsRealStrategicBetsAndProjects() throws Exception {
        JsonNode bets = doGet("/api/v1/strategic-bets");
        JsonNode goals = doGet("/api/v1/goals");
        JsonNode departments = doGet("/api/v1/departments");
        JsonNode projects = doGet("/api/v1/projects");

        assertThat(bets.findValues("name").stream().map(JsonNode::asText))
                .contains(
                        "Atraer, acompanar y formar",
                        "Ofrecer experiencias formativas memorables e innovadoras",
                        "Atraer a los mejores profesores y colaboradores",
                        "Desarrollar alianzas estrategicas",
                        "Adaptar y extender nuestra comunidad",
                        "Ofrecer experiencias extraordinarias para nuestra comunidad"
                );
        assertThat(goals).hasSizeGreaterThanOrEqualTo(6);
        assertThat(departments.findValues("name").stream().map(JsonNode::asText))
                .contains(
                        "Departamento de Computaci\u00f3n y Sistemas inteligentes.",
                        "Departamento de Dise\u00f1o e Innovaci\u00f3n",
                        "Departamento de Ciencias F\u00edsicas y Exactas."
                );
        assertThat(projects.findValues("name").stream().map(JsonNode::asText))
                .contains(
                        "Plataforma de gestion de la oferta de formacion posgradual de la Universidad Icesi",
                        "VISTA: VISualizador y Tutor Interactivo e Inteligente de Estructuras Discretas",
                        "Diseno de un modelo de anonimizacion de datos clinicos para investigacion en entornos hospitalarios"
                );
    }

    @Test
    void updateStrategicBetAndGoalExposeFrontendEditingContract() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        Long worldId = firstId("/api/v1/worlds");
        Long strategicBetId = createStrategicBet("Apuesta editable " + SEQUENCE.incrementAndGet());
        Long goalId = createGoal("Meta editable " + SEQUENCE.incrementAndGet(), unitId);

        JsonNode updatedBet = doPut("/api/v1/strategic-bets/" + strategicBetId, """
                {
                  "name": "Apuesta actualizada %d",
                  "description": "Cambios guardados desde el formulario del frontend.",
                  "worldId": %d,
                  "startDate": "2026-02-01",
                  "endDate": "2026-11-30"
                }
                """.formatted(SEQUENCE.incrementAndGet(), worldId), 200);

        JsonNode updatedGoal = doPut("/api/v1/goals/" + goalId, """
                {
                  "name": "Meta actualizada %d",
                  "description": "Datos macro editados desde el frontend.",
                  "referenceIndicator": "Indicador actualizado",
                  "expectedValue": 90,
                  "measurementUnitId": %d,
                  "startDate": "2026-02-01",
                  "endDate": "2026-11-30"
                }
                """.formatted(SEQUENCE.incrementAndGet(), unitId), 200);

        assertThat(updatedBet.get("id").asLong()).isEqualTo(strategicBetId);
        assertThat(updatedBet.get("description").asText()).contains("frontend");
        assertThat(updatedBet.get("worldId").asLong()).isEqualTo(worldId);
        assertThat(updatedBet.get("startDate").asText()).isEqualTo("2026-02-01");
        assertThat(updatedBet.get("endDate").asText()).isEqualTo("2026-11-30");

        assertThat(updatedGoal.get("id").asLong()).isEqualTo(goalId);
        assertThat(updatedGoal.get("referenceIndicator").asText()).isEqualTo("Indicador actualizado");
        assertThat(updatedGoal.get("expectedValue").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(updatedGoal.get("measurementUnitId").asLong()).isEqualTo(unitId);
        assertThat(updatedGoal.get("startDate").asText()).isEqualTo("2026-02-01");
        assertThat(updatedGoal.get("endDate").asText()).isEqualTo("2026-11-30");
    }

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
        assertThat(doGet("/api/v1/objectives/" + objectiveId + "/coverage-trend")).isNotEmpty();
        assertThat(doGet("/api/v1/strategic-bets")).isNotEmpty();
        assertThat(doGet("/api/v1/goals")).isNotEmpty();

        JsonNode createdUnit = doPost("/api/v1/measurement-units", """
                {"name": "Horas %d", "type": "NUMERICA", "description": "Unidad e2e"}
                """.formatted(SEQUENCE.incrementAndGet()), 201);
        JsonNode createdPeriod = doPost("/api/v1/academic-periods", """
                {"name": "2099-%d", "startDate": "2099-01-01", "endDate": "2099-06-01", "status": "PLANIFICACION"}
                """.formatted(SEQUENCE.incrementAndGet()), 201);
        assertThat(createdUnit.get("id").asLong()).isPositive();
        assertThat(createdPeriod.get("id").asLong()).isPositive();

        JsonNode addedKr = doPost("/api/v1/objectives/" + objectiveId + "/key-results", keyResultPayload(unitId), 201);
        JsonNode updatedKr = doPut("/api/v1/key-results/" + addedKr.get("id").asLong(), keyResultPayload(unitId), 200);

        assertThat(updatedKr.get("progressPercentage").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(updatedKr.get("currentValue").decimalValue()).isEqualByComparingTo("0.00");
        doPatch("/api/v1/key-results/" + keyResultId + "/current-value", """
                {"currentValue": 50}
                """, 404);

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
                {"name": "2199-%d", "startDate": "2199-01-01", "endDate": "2199-06-01", "status": "PLANIFICACION"}
                """.formatted(SEQUENCE.incrementAndGet()), 201);
        Long periodId = period.get("id").asLong();

        JsonNode updatedPeriod = doPut("/api/v1/academic-periods/" + periodId, """
                {"name": "2199-%d", "startDate": "2199-07-01", "endDate": "2199-12-01", "status": "PLANIFICACION"}
                """.formatted(SEQUENCE.incrementAndGet()), 200);
        JsonNode closedPeriod = doPatch("/api/v1/academic-periods/" + periodId + "/status", """
                {"status": "CERRADO"}
                """, 200);
        JsonNode activePeriod = doPatch("/api/v1/academic-periods/" + periodId + "/active", """
                {"active": true}
                """, 200);
        JsonNode inactivePeriod = doPatch("/api/v1/academic-periods/" + periodId + "/active", """
                {"active": false}
                """, 200);

        assertThat(updatedPeriod.get("startDate").asText()).isEqualTo("2199-07-01");
        assertThat(closedPeriod.get("status").asText()).isEqualTo("CERRADO");
        assertThat(activePeriod.get("status").asText()).isEqualTo("ACTIVO");
        assertThat(inactivePeriod.get("status").asText()).isEqualTo("CERRADO");
        doDelete("/api/v1/academic-periods/" + periodId, 204);
    }

    @Test
    void happyPathLinksProjectToKeyResultAndProgressDrivesStrategicExecution() throws Exception {
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
                {"projectId": %d, "keyResultId": %d, "contributionWeight": 60, "contributionType": "DIRECTA"}
                """.formatted(projectId, keyResultId), 201);
        JsonNode links = doGet("/api/v1/project-key-result-links?projectId=" + projectId);
        JsonNode chainBefore = doGet("/api/v1/projects/" + projectId + "/impact-chain");

        assertThat(link.get("overweightWarning").asBoolean()).isFalse();
        assertThat(links).hasSize(1);
        assertThat(chainBefore.get("impacts").get(0).get("appliedContribution").decimalValue()).isEqualByComparingTo("0.00");
        JsonNode linkAudit = doGet("/api/v1/audit-logs?entityType=PROJECT_KEY_RESULT_LINK&entityId=" + link.get("id").asLong());
        assertThat(linkAudit.findValues("action").stream().map(JsonNode::asText)).contains("LINK_CREATED");

        doPost("/api/v1/projects/" + projectId + "/progress", """
                {
                  "progressPercent": 100,
                  "comment": "Avance suficiente para aportar al KR"
                }
                """, 201);
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

}
