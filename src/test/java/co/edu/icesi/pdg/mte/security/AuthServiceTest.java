package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import co.edu.icesi.pdg.mte.security.credentials.CredentialService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private static final UUID AUTH_ID = UUID.fromString("8f9c3a4e-2b1d-4c5e-9f00-112233445566");
    private static final String EMAIL = "persona@icesi.edu.co";
    private static final String PASSWORD = "Trazabilidad-2026";

    private final CredentialService credentials = mock(CredentialService.class);
    private final AccessTokenService tokens = mock(AccessTokenService.class);
    private final ProfessorRepository professors = mock(ProfessorRepository.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final AuditService audit = mock(AuditService.class);
    private final AuthService service = new AuthService(credentials, tokens, professors, attempts, audit);

    @Test
    void loginChecksThePasswordAndIssuesAToken() {
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(professor(AUTH_ID)));
        when(tokens.issue(AUTH_ID, EMAIL)).thenReturn(new AccessTokenService.IssuedToken("jwt", 3600, 1900000000L));

        SecurityDtos.LoginResponse response = service.login(new SecurityDtos.LoginRequest(" Persona@Icesi.edu.co ", PASSWORD));

        assertThat(response).isEqualTo(new SecurityDtos.LoginResponse("jwt", "Bearer", 3600, 1900000000L));
        verify(credentials).verifyPassword(AUTH_ID, PASSWORD);
        verify(attempts).recordSuccess(1L, AUTH_ID);
    }

    @Test
    void unknownOrInactivePeopleGetTheGenericErrorAfterTheSamePasswordCheck() {
        doThrow(new CredentialService.InvalidCredentialsException()).when(credentials).verifyPassword(null, PASSWORD);
        when(professors.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        assertUnauthorized(() -> service.login(new SecurityDtos.LoginRequest("nadie@icesi.edu.co", PASSWORD)));

        Professor inactive = professor(AUTH_ID);
        inactive.setActive(false);
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(inactive));
        assertUnauthorized(() -> service.login(new SecurityDtos.LoginRequest(EMAIL, PASSWORD)));

        // Timing: a missing account still pays for one password check, against no stored hash.
        verify(credentials, times(2)).verifyPassword(null, PASSWORD);
        verify(credentials, never()).verifyPassword(eq(AUTH_ID), anyString());
        verify(attempts, never()).recordFailure(anyLong());
        verifyNoInteractions(tokens);
    }

    @Test
    void lockedAccountsAreRefusedWithoutCheckingTheirOwnPassword() {
        Professor professor = professor(AUTH_ID);
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(professor));
        when(attempts.isLocked(professor)).thenReturn(true);

        assertUnauthorized(() -> service.login(new SecurityDtos.LoginRequest(EMAIL, PASSWORD)));
        verify(credentials).verifyPassword(null, PASSWORD);
        verify(credentials, never()).verifyPassword(eq(AUTH_ID), anyString());
        verify(attempts, never()).recordFailure(anyLong());
        verifyNoInteractions(tokens);
    }

    @Test
    void wrongPasswordsAreCountedAndGetTheGenericError() {
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(professor(null)));
        doThrow(new CredentialService.InvalidCredentialsException()).when(credentials).verifyPassword(null, "mala");

        assertUnauthorized(() -> service.login(new SecurityDtos.LoginRequest(EMAIL, "mala")));
        verify(attempts).recordFailure(1L);
        verifyNoInteractions(tokens);
    }

    @Test
    void setPasswordUsesTheLinkAndStoresThePassword() {
        when(credentials.consumeLink("invite", "token")).thenReturn(AUTH_ID);
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(professor(AUTH_ID)));

        service.setPassword(new SecurityDtos.SetPasswordRequest("token", "invite", PASSWORD));

        verify(credentials).setPassword(AUTH_ID, PASSWORD, EMAIL);
        verify(attempts).unlock(1L, AUTH_ID);
        verify(audit).record(eq(AuditAction.PASSWORD_SET), eq("PROFESSOR"), eq(1L), anyString(), any(), any());
    }

    @Test
    void setPasswordRefusesInvalidLinksAndPeopleWithoutAccess() {
        when(credentials.consumeLink("recovery", "usado")).thenThrow(new CredentialService.InvalidCredentialsException());
        assertStatus(() -> service.setPassword(new SecurityDtos.SetPasswordRequest("usado", "recovery", PASSWORD)),
                HttpStatus.BAD_REQUEST, AuthService.INVALID_LINK);

        Professor inactive = professor(AUTH_ID);
        inactive.setActive(false);
        when(credentials.consumeLink("invite", "ajeno")).thenReturn(AUTH_ID);
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(inactive));
        assertStatus(() -> service.setPassword(new SecurityDtos.SetPasswordRequest("ajeno", "invite", PASSWORD)),
                HttpStatus.FORBIDDEN, AuthService.NO_ACCESS);

        verify(credentials, never()).setPassword(any(), anyString(), anyString());
    }

    @Test
    void setPasswordExplainsWeakAndTooLongPasswords() {
        when(credentials.consumeLink("invite", "token")).thenReturn(AUTH_ID);
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(professor(AUTH_ID)));
        doThrow(new CredentialService.RejectedException()).when(credentials).setPassword(AUTH_ID, "contrasena", EMAIL);

        assertStatus(() -> service.setPassword(new SecurityDtos.SetPasswordRequest("token", "invite", "contrasena")),
                HttpStatus.BAD_REQUEST, AuthService.WEAK_PASSWORD);
        assertStatus(() -> service.setPassword(new SecurityDtos.SetPasswordRequest("token", "invite", "ñ".repeat(37))),
                HttpStatus.BAD_REQUEST, AuthService.PASSWORD_TOO_LONG);
        verify(attempts, never()).unlock(any(), any());
    }

    @Test
    void changePasswordChecksTheCurrentPasswordFirst() {
        when(professors.findById(1L)).thenReturn(Optional.of(professor(AUTH_ID)));
        doThrow(new CredentialService.InvalidCredentialsException()).when(credentials).verifyPassword(AUTH_ID, "mala-actual");

        assertStatus(() -> service.changePassword(user(), new SecurityDtos.ChangePasswordRequest("mala-actual", "Nueva-clave-2026")),
                HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta.");
        verify(attempts).recordFailure(1L);
        verify(credentials, never()).setPassword(any(), anyString(), anyString());
    }

    @Test
    void changePasswordStoresTheNewPassword() {
        when(professors.findById(1L)).thenReturn(Optional.of(professor(AUTH_ID)));

        service.changePassword(user(), new SecurityDtos.ChangePasswordRequest(PASSWORD, "Nueva-clave-2026"));

        verify(credentials).setPassword(AUTH_ID, "Nueva-clave-2026", EMAIL);
        verify(audit).record(eq(AuditAction.PASSWORD_CHANGED), eq("PROFESSOR"), eq(1L), anyString(), any(), any());
    }

    @Test
    void changePasswordRejectsReuseLockedAccountsAndPeopleWithoutAccount() {
        Professor professor = professor(AUTH_ID);
        when(professors.findById(1L)).thenReturn(Optional.of(professor));
        assertThatThrownBy(() -> service.changePassword(user(), new SecurityDtos.ChangePasswordRequest(PASSWORD, PASSWORD)))
                .isInstanceOf(BusinessException.class);

        when(attempts.isLocked(professor)).thenReturn(true);
        assertStatus(() -> service.changePassword(user(), new SecurityDtos.ChangePasswordRequest(PASSWORD, "Nueva-clave-2026")),
                HttpStatus.BAD_REQUEST, AuthService.INVALID_LOGIN);

        when(professors.findById(1L)).thenReturn(Optional.of(professor(null)));
        assertStatus(() -> service.changePassword(user(), new SecurityDtos.ChangePasswordRequest(PASSWORD, "Nueva-clave-2026")),
                HttpStatus.UNAUTHORIZED, AuthService.NO_ACCESS);

        when(professors.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> service.changePassword(user(), new SecurityDtos.ChangePasswordRequest(PASSWORD, "Nueva-clave-2026")),
                HttpStatus.UNAUTHORIZED, AuthService.NO_ACCESS);
        verifyNoInteractions(credentials);
    }

    @Test
    void logoutClosesTheTokenAndIgnoresMissingOnes() {
        service.logout(null);
        service.logout(" ");
        verifyNoInteractions(tokens);

        service.logout("jwt");
        verify(tokens).revoke("jwt");
    }

    @Test
    void resolveFindsThePersonByAccountId() {
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(professor(AUTH_ID)));

        ExternalUserContext context = service.resolve(jwt(AUTH_ID.toString()));

        assertThat(context.externalUserId()).isEqualTo(1L);
        assertThat(context.roles()).containsExactly("CONTRIBUTOR");
    }

    @Test
    void resolveRejectsBadSubjectsUnknownAndInactivePeople() {
        assertThatThrownBy(() -> service.resolve(jwt("no-es-uuid"))).isInstanceOf(BadCredentialsException.class);

        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolve(jwt(AUTH_ID.toString()))).isInstanceOf(DisabledException.class);

        Professor inactive = professor(AUTH_ID);
        inactive.setActive(false);
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.resolve(jwt(AUTH_ID.toString()))).isInstanceOf(DisabledException.class);
    }

    private static void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertStatus(call, HttpStatus.UNAUTHORIZED, AuthService.INVALID_LOGIN);
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.status()).isEqualTo(status);
            assertThat(exception.getMessage()).isEqualTo(message);
        });
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

    private static ExternalUserContext user() {
        return new ExternalUserContext(1L, EMAIL, EMAIL, List.of("CONTRIBUTOR"), List.of(), 1L, "Persona", null);
    }

    private static Jwt jwt(String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(subject).claim("email", EMAIL)
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
    }
}
