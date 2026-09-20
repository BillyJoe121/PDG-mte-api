package co.edu.icesi.pdg.mte.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DirectoryManagementE2ETest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void managesPersistedUsersFiltersAndStatus() throws Exception {
        int sequence = SEQUENCE.incrementAndGet();
        Long departmentId = doGet("/api/v1/departments", 200).get(0).get("id").asLong();
        String email = "directory." + sequence + "@icesi.edu.co";

        JsonNode created = doPost("/api/v1/directory-users", """
                {"name":"Usuario Directorio %d","email":"%s","departmentId":%d,
                 "accessRole":"CONTRIBUTOR","active":true}
                """.formatted(sequence, email, departmentId), 201);
        Long id = created.get("id").asLong();
        assertThat(created.get("accessRole").asText()).isEqualTo("CONTRIBUTOR");
        assertThat(created.get("active").asBoolean()).isTrue();

        JsonNode filtered = doGet("/api/v1/directory-users?search=" + email + "&accessRole=CONTRIBUTOR&active=true", 200);
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).get("id").asLong()).isEqualTo(id);

        JsonNode updated = doPut("/api/v1/directory-users/" + id, """
                {"name":"Gestor Actualizado %d","email":"%s","departmentId":%d,
                 "accessRole":"MANAGER","active":true}
                """.formatted(sequence, email, departmentId), 200);
        assertThat(updated.get("accessRole").asText()).isEqualTo("MANAGER");

        JsonNode inactive = doPatch("/api/v1/directory-users/" + id + "/active", "{\"active\":false}", 200);
        assertThat(inactive.get("active").asBoolean()).isFalse();

        doDelete("/api/v1/directory-users/" + id, 204);
        assertThat(doGet("/api/v1/directory-users?search=" + email, 200)).isEmpty();
    }

    @Test
    void validatesDirectoryUsersAndManagesDepartments() throws Exception {
        int sequence = SEQUENCE.incrementAndGet();
        Long departmentId = doGet("/api/v1/departments", 200).get(0).get("id").asLong();
        Long schoolId = doGet("/api/v1/schools", 200).get(0).get("id").asLong();
        String email = "duplicate." + sequence + "@icesi.edu.co";
        String payload = """
                {"name":"Usuario Unico","email":"%s","departmentId":%d,
                 "accessRole":"CONTRIBUTOR","active":true}
                """.formatted(email, departmentId);
        doPost("/api/v1/directory-users", payload, 201);
        doPost("/api/v1/directory-users", payload, 409);
        doPost("/api/v1/directory-users", """
                {"name":"Rol invalido","email":"invalid.%d@icesi.edu.co","departmentId":%d,
                 "accessRole":"SUPERUSER","active":true}
                """.formatted(sequence, departmentId), 400);

        JsonNode department = doPost("/api/v1/departments", """
                {"name":"Departamento E2E %d","description":"Creado desde directorio","schoolId":%d}
                """.formatted(sequence, schoolId), 201);
        Long createdDepartmentId = department.get("id").asLong();
        JsonNode changed = doPut("/api/v1/departments/" + createdDepartmentId, """
                {"name":"Departamento E2E actualizado %d","description":"Actualizado","schoolId":%d}
                """.formatted(sequence, schoolId), 200);
        assertThat(changed.get("name").asText()).contains("actualizado");
        doDelete("/api/v1/departments/" + createdDepartmentId, 204);
    }

    private JsonNode doGet(String url, int expectedStatus) throws Exception {
        String body = mockMvc.perform(get(url)).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode doPost(String url, String payload, int expectedStatus) throws Exception {
        String body = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode doPut(String url, String payload, int expectedStatus) throws Exception {
        String body = mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private JsonNode doPatch(String url, String payload, int expectedStatus) throws Exception {
        String body = mockMvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    private void doDelete(String url, int expectedStatus) throws Exception {
        mockMvc.perform(delete(url)).andExpect(status().is(expectedStatus));
    }
}
