package co.edu.icesi.pdg.mte.e2e;

import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import co.edu.icesi.pdg.mte.security.SupabaseAuthClient;
import co.edu.icesi.pdg.mte.security.SupabaseErrors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "mte.supabase.access-link-url=https://mte.example.edu/establecer-contrasena")
@AutoConfigureMockMvc
class AuthFlowE2ETest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProfessorRepository professors;
    @Autowired private DepartmentRepository departments;
    @MockitoBean private SupabaseAuthClient supabase;

    @Test
    void personInTheDirectorySignsInAndGetsABearerToken() throws Exception {
        Professor professor = person();
        UUID authUserId = UUID.randomUUID();
        when(supabase.signInWithPassword(professor.getEmail(), "clave-segura-1"))
                .thenReturn(new SupabaseAuthClient.Session("jwt-real", 3600, 1900000000L, authUserId, professor.getEmail()));

        JsonNode response = login(professor.getEmail(), "clave-segura-1", 200);

        assertThat(response.get("accessToken").asText()).isEqualTo("jwt-real");
        assertThat(response.get("tokenType").asText()).isEqualTo("Bearer");
        Professor stored = professors.findById(professor.getId()).orElseThrow();
        assertThat(stored.getAuthUserId()).isEqualTo(authUserId);
        assertThat(stored.getLastAccessAt()).isNotNull();
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() throws Exception {
        Professor professor = person();
        when(supabase.signInWithPassword(eq(professor.getEmail()), anyString()))
                .thenThrow(SupabaseErrors.invalidCredentials());

        for (int attempt = 1; attempt <= 5; attempt++) {
            JsonNode error = login(professor.getEmail(), "clave-mala-" + attempt, 401);
            assertThat(error.toString()).contains("5 intentos fallidos");
        }
        login(professor.getEmail(), "clave-segura-1", 401);

        verify(supabase, times(5)).signInWithPassword(eq(professor.getEmail()), anyString());
        Professor stored = professors.findById(professor.getId()).orElseThrow();
        assertThat(stored.getLockedUntil()).isNotNull();
        assertThat(stored.getFailedLoginAttempts()).isEqualTo(5);
    }

    @Test
    void unknownEmailGetsTheSameAnswerWithoutReachingSupabase() throws Exception {
        login("no.existe." + SEQUENCE.incrementAndGet() + "@icesi.edu.co", "clave-segura-1", 401);

        verifyNoInteractions(supabase);
    }

    @Test
    void setPasswordValidatesTheRequestBeforeCallingSupabase() throws Exception {
        mockMvc.perform(post("/api/v1/auth/set-password").header("Authorization", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tokenHash":"abc","type":"invite","password":"corta"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/set-password").header("Authorization", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tokenHash":"abc","type":"phone_change","password":"clave-segura-1"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(supabase);
    }

    @Test
    void onlyAdministratorsCanIssueAccessLinks() throws Exception {
        Professor professor = person();
        UUID authUserId = UUID.randomUUID();
        when(supabase.generateLink("invite", professor.getEmail()))
                .thenReturn(new SupabaseAuthClient.GeneratedLink(authUserId, "hash123", "invite"));

        mockMvc.perform(post("/api/v1/directory-users/" + professor.getId() + "/access-link")
                        .header("Authorization", "Bearer test-manager"))
                .andExpect(status().isForbidden());
        String body = mockMvc.perform(post("/api/v1/directory-users/" + professor.getId() + "/access-link"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.get("url").asText())
                .isEqualTo("https://mte.example.edu/establecer-contrasena?token_hash=hash123&type=invite");
        assertThat(response.get("accountCreated").asBoolean()).isTrue();
        verify(supabase, times(1)).generateLink("invite", professor.getEmail());
    }

    @Test
    void changingThePasswordNeedsASession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password").header("Authorization", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"clave-actual-1","newPassword":"clave-nueva-12"}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(supabase);
    }

    @Test
    void changingThePasswordRejectsAShortNewPasswordBeforeCallingSupabase() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"clave-actual-1","newPassword":"corta-123"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(supabase);
    }

    private JsonNode login(String email, String password, int expectedStatus) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").header("Authorization", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginBody(email, password))))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Professor person() {
        Professor professor = new Professor();
        professor.setName("Persona flujo " + SEQUENCE.incrementAndGet());
        professor.setEmail("flujo." + SEQUENCE.get() + "." + UUID.randomUUID().toString().substring(0, 8) + "@icesi.edu.co");
        professor.setAccessRole("CONTRIBUTOR");
        professor.setDepartment(departments.findAll().get(0));
        return professors.save(professor);
    }

    private record LoginBody(String email, String password) {
    }
}
