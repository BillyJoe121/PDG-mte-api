package co.edu.icesi.pdg.mte.e2e;

import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "mte.auth.access-link-url=https://mte.example.edu/establecer-contrasena")
@AutoConfigureMockMvc
class AuthFlowE2ETest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String PASSWORD = "Trazabilidad-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProfessorRepository professors;
    @Autowired private DepartmentRepository departments;

    @Test
    void invitedPersonSetsAPasswordSignsInUsesTheApiAndSignsOut() throws Exception {
        Professor professor = person();
        JsonNode link = issueLink(professor);
        assertThat(link.get("accountCreated").asBoolean()).isTrue();
        Map<String, String> query = query(link.get("url").asText());
        assertThat(link.get("url").asText()).startsWith("https://mte.example.edu/establecer-contrasena?token_hash=");
        assertThat(query.get("type")).isEqualTo("invite");

        setPassword(query.get("token_hash"), "invite", PASSWORD, 204);
        JsonNode session = login(professor.getEmail(), PASSWORD, 200);

        assertThat(session.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(session.get("expiresIn").asLong()).isEqualTo(3600);
        String token = session.get("accessToken").asText();
        mockMvc.perform(get("/api/v1/departments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(professors.findById(professor.getId()).orElseThrow().getLastAccessAt()).isNotNull();

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/departments").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aLinkWorksOnceAndAWeakPasswordDoesNotSpendIt() throws Exception {
        Professor professor = person();
        String token = query(issueLink(professor).get("url").asText()).get("token_hash");

        setPassword(token, "invite", "contrasena", 400);
        setPassword(token, "recovery", PASSWORD, 400);
        setPassword(token, "invite", PASSWORD, 204);
        setPassword(token, "invite", "Otra-clave-segura-1", 400);

        login(professor.getEmail(), PASSWORD, 200);
    }

    @Test
    void aNewLinkReplacesTheOldOneAndIsARecoveryOnceAPasswordExists() throws Exception {
        Professor professor = person();
        String first = query(issueLink(professor).get("url").asText()).get("token_hash");
        Map<String, String> second = query(issueLink(professor).get("url").asText());

        setPassword(first, "invite", PASSWORD, 400);
        setPassword(second.get("token_hash"), "invite", PASSWORD, 204);

        JsonNode recovery = issueLink(professor);
        assertThat(recovery.get("accountCreated").asBoolean()).isFalse();
        Map<String, String> query = query(recovery.get("url").asText());
        assertThat(query.get("type")).isEqualTo("recovery");
        setPassword(query.get("token_hash"), "recovery", "Nueva-clave-2026", 204);

        login(professor.getEmail(), PASSWORD, 401);
        login(professor.getEmail(), "Nueva-clave-2026", 200);
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() throws Exception {
        Professor professor = personWithPassword();

        for (int attempt = 1; attempt <= 5; attempt++) {
            JsonNode error = login(professor.getEmail(), "clave-mala-" + attempt, 401);
            assertThat(error.toString()).contains("5 intentos fallidos");
        }
        login(professor.getEmail(), PASSWORD, 401);

        Professor stored = professors.findById(professor.getId()).orElseThrow();
        assertThat(stored.getLockedUntil()).isNotNull();
        assertThat(stored.getFailedLoginAttempts()).isEqualTo(5);
    }

    @Test
    void unknownEmailsAndPeopleWithoutPasswordGetTheSameAnswer() throws Exception {
        JsonNode unknown = login("no.existe." + SEQUENCE.incrementAndGet() + "@icesi.edu.co", PASSWORD, 401);
        JsonNode noPassword = login(person().getEmail(), PASSWORD, 401);

        assertThat(noPassword.get("message")).isEqualTo(unknown.get("message"));
    }

    @Test
    void setPasswordValidatesTheRequest() throws Exception {
        setPassword("abc", "invite", "corta", 400);
        setPassword("abc", "phone_change", PASSWORD, 400);
        setPassword("no-existe", "invite", PASSWORD, 400);
    }

    @Test
    void onlyAdministratorsCanIssueAccessLinks() throws Exception {
        Professor professor = person();

        mockMvc.perform(post("/api/v1/directory-users/" + professor.getId() + "/access-link")
                        .header("Authorization", "Bearer test-manager"))
                .andExpect(status().isForbidden());
        issueLink(professor);
    }

    @Test
    void changingThePasswordChecksTheCurrentOneAndKeepsTheSession() throws Exception {
        Professor professor = personWithPassword();
        String token = login(professor.getEmail(), PASSWORD, 200).get("accessToken").asText();

        changePassword(token, "clave-equivocada", "Nueva-clave-2026", 400);
        changePassword(token, PASSWORD, "Nueva-clave-2026", 204);

        mockMvc.perform(get("/api/v1/departments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        login(professor.getEmail(), PASSWORD, 401);
        login(professor.getEmail(), "Nueva-clave-2026", 200);
    }

    @Test
    void changingThePasswordNeedsASessionAndALongEnoughPassword() throws Exception {
        changePassword("", "clave-actual-1", "clave-nueva-12", 401);
        mockMvc.perform(post("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"clave-actual-1","newPassword":"corta-123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgedTokensAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/departments").header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.firma"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode issueLink(Professor professor) throws Exception {
        String body = mockMvc.perform(post("/api/v1/directory-users/" + professor.getId() + "/access-link"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void setPassword(String token, String type, String password, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/set-password").header("Authorization", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tokenHash", token, "type", type, "password", password))))
                .andExpect(status().is(expectedStatus));
    }

    private void changePassword(String token, String current, String next, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/password")
                        .header("Authorization", token.isEmpty() ? "" : "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", current, "newPassword", next))))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode login(String email, String password, int expectedStatus) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").header("Authorization", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static Map<String, String> query(String url) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().toSingleValueMap();
    }

    private Professor personWithPassword() throws Exception {
        Professor professor = person();
        setPassword(query(issueLink(professor).get("url").asText()).get("token_hash"), "invite", PASSWORD, 204);
        return professor;
    }

    private Professor person() {
        Professor professor = new Professor();
        professor.setName("Persona flujo " + SEQUENCE.incrementAndGet());
        professor.setEmail("flujo." + SEQUENCE.get() + "." + UUID.randomUUID().toString().substring(0, 8) + "@icesi.edu.co");
        professor.setAccessRole("CONTRIBUTOR");
        professor.setDepartment(departments.findAll().get(0));
        return professors.save(professor);
    }
}
