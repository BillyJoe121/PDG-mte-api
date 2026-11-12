package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import co.edu.icesi.pdg.mte.security.credentials.CredentialService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthFlowBranchesTest {
    private static final UUID AUTH_ID = UUID.fromString("8f9c3a4e-2b1d-4c5e-9f00-112233445566");
    private static final String EMAIL = "persona@icesi.edu.co";
    private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");

    private final CredentialService credentials = mock(CredentialService.class);
    private final ProfessorRepository professors = mock(ProfessorRepository.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final AuthService service =
            new AuthService(credentials, mock(AccessTokenService.class), professors, attempts, mock(AuditService.class));

    @Test
    void loginWithoutEmailIsTreatedAsUnknownAccount() {
        when(professors.findByEmailIgnoreCase("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new SecurityDtos.LoginRequest(null, "Trazabilidad-2026")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(professors).findByEmailIgnoreCase("");
    }

    @Test
    void setPasswordForAccountWithoutCredentialsIsForbidden() {
        Professor professor = professor(AUTH_ID);
        when(credentials.consumeLink("invite", "token")).thenReturn(AUTH_ID);
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(professor));
        doThrow(new CredentialService.InvalidCredentialsException()).when(credentials)
                .setPassword(AUTH_ID, "Trazabilidad-2026", EMAIL);

        assertThatThrownBy(() -> service.setPassword(new SecurityDtos.SetPasswordRequest("token", "invite", "Trazabilidad-2026")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getMessage()).isEqualTo(AuthService.NO_ACCESS);
                });
    }

    @Test
    void changePasswordRequiresAnExternalPrincipal() {
        AuthController controller = new AuthController(service);
        var request = new SecurityDtos.ChangePasswordRequest("actual-clave-1", "Nueva-clave-2026");

        assertThatThrownBy(() -> controller.changePassword(null, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> controller.changePassword(
                new UsernamePasswordAuthenticationToken("plain-user", null, List.of()), request))
                .isInstanceOf(BusinessException.class).hasMessage(AuthService.NO_ACCESS);
    }

    @Test
    void successKeepsTheExistingAccountLinkAndIgnoresMissingAccountIds() {
        LoginAttemptService real = new LoginAttemptService(professors, mock(AuditService.class), Clock.fixed(NOW, ZoneOffset.UTC));
        UUID existing = UUID.randomUUID();
        Professor linked = professor(existing);
        Professor unlinked = professor(null);
        unlinked.setId(2L);
        when(professors.findById(1L)).thenReturn(Optional.of(linked));
        when(professors.findById(2L)).thenReturn(Optional.of(unlinked));
        when(professors.findById(3L)).thenReturn(Optional.empty());

        real.recordSuccess(1L, UUID.randomUUID());
        real.recordSuccess(2L, null);
        real.recordSuccess(3L, UUID.randomUUID());

        assertThat(linked.getAuthUserId()).isEqualTo(existing);
        assertThat(linked.getLastAccessAt()).isEqualTo(NOW);
        assertThat(unlinked.getAuthUserId()).isNull();
        verify(professors).save(linked);
        verify(professors).save(unlinked);
    }

    private static Professor professor(UUID authUserId) {
        Professor professor = new Professor();
        professor.setId(1L);
        professor.setName("Persona");
        professor.setEmail(EMAIL);
        professor.setAccessRole("CONTRIBUTOR");
        professor.setAuthUserId(authUserId);
        return professor;
    }
}
