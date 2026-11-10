package co.edu.icesi.pdg.mte.support;

import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Test-only authentication: accepts the tokens {@code test-admin}, {@code test-manager},
 * {@code test-contributor} and {@code test-visitor} in addition to the real access tokens from
 * {@code /auth/login}, and makes every MockMvc request act as {@code test-admin} unless it sends its own Authorization header.
 * Send an empty Authorization header to call the API without a session.
 */
@Configuration
public class TestAuthConfig {
    public static final String ADMIN_TOKEN = "test-admin";
    private static final String PREFIX = "test-";
    private static final Set<String> ROLES = Set.of("ADMIN", "MANAGER", "CONTRIBUTOR", "VISITOR");

    @Bean
    @Primary
    JwtDecoder testJwtDecoder(
            ProfessorRepository professors,
            DepartmentRepository departments,
            @Qualifier("jwtDecoder") JwtDecoder realDecoder
    ) {
        return token -> {
            if (!token.startsWith(PREFIX)) {
                return realDecoder.decode(token);
            }
            String role = token.substring(PREFIX.length()).toUpperCase(Locale.ROOT);
            if (!ROLES.contains(role)) {
                throw new BadJwtException("Unknown test token.");
            }
            String email = "test." + role.toLowerCase(Locale.ROOT) + "@icesi.edu.co";
            UUID subject = UUID.nameUUIDFromBytes(token.getBytes(StandardCharsets.UTF_8));
            ensurePerson(professors, departments, email, role, subject);
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(subject.toString())
                    .claim("email", email)
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(3600))
                    .build();
        };
    }

    @Bean
    MockMvcBuilderCustomizer defaultTestSession() {
        return builder -> builder.defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN));
    }

    private static synchronized void ensurePerson(
            ProfessorRepository professors,
            DepartmentRepository departments,
            String email,
            String role,
            UUID subject
    ) {
        Professor professor = professors.findByEmailIgnoreCase(email).orElse(null);
        if (professor != null && subject.equals(professor.getAuthUserId())) {
            return;
        }
        if (professor == null) {
            professor = new Professor();
            professor.setName("Persona de prueba " + role);
            professor.setEmail(email);
            professor.setAccessRole(role);
            professor.setDepartment(departments.findAll().get(0));
        }
        professor.setAuthUserId(subject);
        professors.save(professor);
    }
}
