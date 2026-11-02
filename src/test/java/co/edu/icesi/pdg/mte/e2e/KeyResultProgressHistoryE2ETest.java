package co.edu.icesi.pdg.mte.e2e;

import co.edu.icesi.pdg.mte.strategy.KeyResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KeyResultProgressHistoryE2ETest extends StrategicHierarchyE2ETestSupport {
    private static final List<String> FIELDS = List.of("id", "keyResultId", "previousValue", "currentValue",
            "previousPercentage", "progressPercentage", "authorName", "authorEmail", "recordedAt");

    private Long newKeyResult() throws Exception {
        Long unitId = firstId("/api/v1/measurement-units");
        return createKeyResult("KR historial " + SEQUENCE.incrementAndGet(), firstId("/api/v1/academic-periods"),
                createGoal("Meta historial " + SEQUENCE.incrementAndGet(), unitId),
                createStrategicBet("Apuesta historial " + SEQUENCE.incrementAndGet()), unitId).get("id").asLong();
    }

    private JsonNode records(Long id, String token, int expectedStatus) throws Exception {
        String body = mockMvc.perform(get("/api/v1/key-results/" + id + "/progress-records")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private void progressAs(Long id, String payload, String token, int expectedStatus) throws Exception {
        mockMvc.perform(patch("/api/v1/key-results/" + id + "/progress").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().is(expectedStatus));
    }

    private static String progress(int value) {
        return "{\"currentValue\": %d, \"progressPercentage\": %d}".formatted(value, value);
    }

    @Test
    void everyRegisteredProgressIsKeptNewestFirstWithItsAuthor() throws Exception {
        Long id = newKeyResult();
        assertThat(records(id, "test-visitor", 200).isArray()).isTrue();
        assertThat(records(id, "test-visitor", 200)).isEmpty();

        progressAs(id, progress(20), "test-admin", 200);
        progressAs(id, progress(55), "test-manager", 200);

        JsonNode history = records(id, "test-visitor", 200);
        assertThat(history).hasSize(2);
        JsonNode latest = history.get(0);
        JsonNode first = history.get(1);
        for (String field : FIELDS) {
            assertThat(latest.has(field)).as(field).isTrue();
        }
        assertThat(latest.get("keyResultId").asLong()).isEqualTo(id);
        assertThat(latest.get("previousValue").decimalValue()).isEqualByComparingTo("20");
        assertThat(latest.get("previousPercentage").decimalValue()).isEqualByComparingTo("20");
        assertThat(latest.get("currentValue").decimalValue()).isEqualByComparingTo("55");
        assertThat(latest.get("progressPercentage").decimalValue()).isEqualByComparingTo("55");
        assertThat(latest.get("authorEmail").asText()).isEqualTo("test.manager@icesi.edu.co");
        assertThat(latest.get("authorName").asText()).isNotBlank();
        assertThat(first.get("previousValue").decimalValue()).isEqualByComparingTo("0");
        assertThat(first.get("currentValue").decimalValue()).isEqualByComparingTo("20");
        assertThat(first.get("authorEmail").asText()).isEqualTo("test.admin@icesi.edu.co");
        assertThat(latest.get("id").asLong()).isGreaterThan(first.get("id").asLong());
        assertThat(Instant.parse(latest.get("recordedAt").asText()))
                .isAfterOrEqualTo(Instant.parse(first.get("recordedAt").asText()));
    }

    @Test
    void unknownPreviousValuesAreSerializedAsNull() throws Exception {
        Long id = newKeyResult();
        KeyResult stored = keyResultRepository.findById(id).orElseThrow();
        stored.setCurrentValue(null);
        stored.setProgressPercentage(null);
        keyResultRepository.save(stored);

        progressAs(id, progress(10), "test-admin", 200);

        JsonNode record = records(id, "test-admin", 200).get(0);
        assertThat(record.has("previousValue")).isTrue();
        assertThat(record.get("previousValue").isNull()).isTrue();
        assertThat(record.has("previousPercentage")).isTrue();
        assertThat(record.get("previousPercentage").isNull()).isTrue();
        assertThat(record.get("currentValue").decimalValue()).isEqualByComparingTo("10");
    }

    @Test
    void onlyRegisteredProgressCreatesRecords() throws Exception {
        Long id = newKeyResult();

        progressAs(id, progress(150), "test-admin", 400);
        progressAs(id, progress(30), "test-visitor", 403);
        progressAs(id, progress(30), "test-contributor", 403);
        doPatch("/api/v1/key-results/" + id + "/status", "{\"status\": \"ACTIVO\"}", 200);
        assertThat(records(id, "test-admin", 200)).isEmpty();

        progressAs(id, progress(30), "test-admin", 200);
        doPatch("/api/v1/key-results/" + id + "/active", "{\"active\": false}", 200);
        progressAs(id, progress(40), "test-admin", 409);
        doPatch("/api/v1/key-results/" + id + "/active", "{\"active\": true}", 200);

        assertThat(records(id, "test-admin", 200)).hasSize(1);
    }

    @Test
    void anArchivedKeyResultKeepsItsHistoryReadable() throws Exception {
        Long id = newKeyResult();
        progressAs(id, progress(30), "test-admin", 200);
        doPatch("/api/v1/key-results/" + id + "/active", "{\"active\": false}", 200);

        assertThat(records(id, "test-visitor", 200)).hasSize(1);
    }

    @Test
    void unknownKeyResultsAndMissingSessionsAreRejected() throws Exception {
        records(99999L, "test-admin", 404);
        mockMvc.perform(get("/api/v1/key-results/1/progress-records").header("Authorization", ""))
                .andExpect(status().isUnauthorized());
    }
}
