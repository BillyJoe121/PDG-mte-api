package co.edu.icesi.pdg.mte.e2e;

import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class StrategicHierarchyE2ETestSupport {
    protected static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected KeyResultRepository keyResultRepository;

    @Autowired
    protected ProjectKeyResultLinkRepository linkRepository;
    protected Long createStrategicBet(String name) throws Exception {
        return doPost("/api/v1/strategic-bets", """
                {
                  "name": "%s",
                  "description": "Impulsar iniciativas academicas con trazabilidad de impacto.",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(name), 201).get("id").asLong();
    }

    protected Long createGoal(String name, Long unitId) throws Exception {
        return doPost("/api/v1/goals", """
                {
                  "name": "%s",
                  "description": "Medir el aporte de los proyectos a indicadores estrategicos.",
                  "referenceIndicator": "Indice de impacto",
                  "expectedValue": 80,
                  "measurementUnitId": %d,
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31"
                }
                """.formatted(name, unitId), 201).get("id").asLong();
    }

    protected Long createProject(String name, Long departmentId) throws Exception {
        Long keyResultId = keyResultRepository.findAll().stream()
                .findFirst()
                .orElseThrow()
                .getId();
        return doPost("/api/v1/projects", """
                {
                  "name": "%s",
                  "description": "Proyecto usado para medir avance de KR por ejecucion.",
                  "type": "INVESTIGACION",
                  "departmentId": %d,
                  "status": "ACTIVO",
                  "startPeriod": "2026-Q1",
                  "endPeriod": "2026-Q2",
                  "startDate": "2026-01-15",
                  "endDate": "2026-06-30",
                  "keyResultId": %d,
                  "contributionWeight": 35,
                  "linkStatus": "ACTIVO",
                  "tutors": ["Tutora A"]
                }
                """.formatted(name, departmentId, keyResultId), 201).get("id").asLong();
    }

    protected String objectivePayload(String name, Long departmentId, Long periodId, Long goalId, Long strategicBetId, Long unitId) {
        return """
                {
                  "name": "%s",
                  "description": "Objetivo operativo para tablero directivo.",
                  "departmentId": %d,
                  "academicPeriodId": %d,
                  "goalId": %d,
                  "strategicBetId": %d,
                  "keyResults": [%s]
                }
                """.formatted(name, departmentId, periodId, goalId, strategicBetId, keyResultPayload(unitId));
    }

    protected String keyResultPayload(Long unitId) {
        return """
                {
                  "name": "KR operativo",
                  "description": "Lograr trazabilidad de proyectos activos",
                  "metric": "Porcentaje de proyectos vinculados",
                  "baseValue": 0,
                  "targetValue": 100,
                  "measurementUnitId": %d
                }
                """.formatted(unitId);
    }

    protected Long firstId(String url) throws Exception {
        return doGet(url).get(0).get("id").asLong();
    }

    protected JsonNode doGet(String url) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    protected JsonNode doPost(String url, String payload, int expectedStatus) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    protected JsonNode doPut(String url, String payload, int expectedStatus) throws Exception {
        return objectMapper.readTree(mockMvc.perform(put(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    protected JsonNode doPatch(String url, String payload, int expectedStatus) throws Exception {
        return objectMapper.readTree(mockMvc.perform(patch(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    protected void doDelete(String url, int expectedStatus) throws Exception {
        mockMvc.perform(delete(url))
                .andExpect(status().is(expectedStatus));
    }

    protected void createProjectLink(Long keyResultId, Long externalProjectId) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResultRepository.findById(keyResultId).orElseThrow());
        link.setExternalProjectId(externalProjectId);
        link.setContributionWeight(java.math.BigDecimal.valueOf(35));
        linkRepository.save(link);
    }

    protected java.util.List<Long> ids(JsonNode nodes) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        nodes.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }
}
