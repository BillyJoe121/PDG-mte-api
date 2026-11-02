package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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

    private final SupabaseAuthClient supabase = mock(SupabaseAuthClient.class);
    private final ProfessorRepository professors = mock(ProfessorRepository.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final AuditService audit = mock(AuditService.class);
    private final AuthService service = new AuthService(supabase, professors, attempts, audit);

    @Test
    void loginReturnsTheSupabaseTokenAndRecordsSuccess() {
        Professor professor = professor(null);
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(professor));
        when(supabase.signInWithPassword(EMAIL, "clave-segura-1")).thenReturn(session("jwt"));

        SecurityDtos.LoginResponse response = service.login(new SecurityDtos.LoginRequest(" Persona@Icesi.edu.co ", "clave-segura-1"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(attempts).recordSuccess(1L, AUTH_ID);
    }

    @Test
    void unknownOrInactivePeopleGetTheGenericErrorWithoutCallingSupabase() {
        when(professors.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        assertUnauthorized(() -> service.login(new SecurityDtos.LoginRequest("nadie@icesi.edu.co", "clave-segura-1")));

        Professor inactive = professor(AUTH_ID);
        inactive.setActive(false);
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(inactive));
        assertUnauthorized(() -> service.login(new SecurityDtos.LoginRequest(EMAIL, "clave-segura-1")));

        verifyNoInteractions(supabase);
    }

    @Test
    void lockedAccountIsRejectedEvenWithTheRightPassword() {
        Professor professor = professor(AUTH_ID);
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(professor));
        when(attempts.isLocked(professor)).thenReturn(true);

        assertUnauthorized(() -> service.login(new SecurityDtos.LoginRequest(EMAIL, "clave-segura-1")));
        verifyNoInteractions(supabase);
    }

    @Test
    void wrongPasswordCountsAFailure() {
        Professor professor = professor(AUTH_ID);
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(professor));
        when(supabase.signInWithPassword(anyString(), anyString()))
                .thenThrow(new SupabaseAuthClient.InvalidCredentialsException("invalid_credentials"));

        assertUnauthorized(() -> service.login(new SecurityDtos.LoginRequest(EMAIL, "clave-mala-123")));
        verify(attempts).recordFailure(1L);
    }

    @Test
    void unavailableSupabaseIsNotCountedAsAFailure() {
        Professor professor = professor(AUTH_ID);
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(professor));
        when(supabase.signInWithPassword(anyString(), anyString()))
                .thenThrow(new SupabaseAuthClient.UnavailableException("down"));

        assertThatThrownBy(() -> service.login(new SecurityDtos.LoginRequest(EMAIL, "clave-segura-1")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(attempts, never()).recordFailure(any());
    }

    @Test
    void accountOfAnotherPersonCannotSignInForThisEmail() {
        Professor professor = professor(UUID.randomUUID());
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(professor));
        when(supabase.signInWithPassword(anyString(), anyString())).thenReturn(session("jwt"));

        assertUnauthorized(() -> service.login(new SecurityDtos.LoginRequest(EMAIL, "clave-segura-1")));
        verify(attempts, never()).recordSuccess(any(), any());
    }

    @Test
    void resolveUsesTheRoleStoredInTheDirectory() {
        Professor professor = professor(AUTH_ID);
        professor.setAccessRole("VISITOR");
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(professor));

        ExternalUserContext user = service.resolve(jwt(AUTH_ID.toString(), EMAIL));

        assertThat(user.roles()).containsExactly("VISITOR");
        assertThat(user.externalUserId()).isEqualTo(1L);
    }

    @Test
    void resolveLinksThePersonByEmailOnFirstUse() {
        Professor professor = professor(null);
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.empty());
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(professor));
        when(professors.save(professor)).thenReturn(professor);

        service.resolve(jwt(AUTH_ID.toString(), "PERSONA@icesi.edu.co"));

        assertThat(professor.getAuthUserId()).isEqualTo(AUTH_ID);
        verify(professors).save(professor);
    }

    @Test
    void resolveRejectsEmailsAlreadyLinkedToAnotherAccountAndInactivePeople() {
        Professor linkedElsewhere = professor(UUID.randomUUID());
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.empty());
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(linkedElsewhere));
        assertThatThrownBy(() -> service.resolve(jwt(AUTH_ID.toString(), EMAIL))).isInstanceOf(DisabledException.class);

        Professor inactive = professor(AUTH_ID);
        inactive.setActive(false);
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.resolve(jwt(AUTH_ID.toString(), EMAIL))).isInstanceOf(DisabledException.class);

        assertThatThrownBy(() -> service.resolve(jwt("no-es-uuid", EMAIL)))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }

    @Test
    void setPasswordWithAValidLinkUpdatesUnlocksAndAudits() {
        Professor professor = professor(null);
        when(supabase.verifyTokenHash("invite", "hash")).thenReturn(session("link-jwt"));
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.empty());
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(professor));

        service.setPassword(new SecurityDtos.SetPasswordRequest("hash", "invite", "clave-segura-1"));

        verify(supabase).updatePassword(AUTH_ID, "clave-segura-1");
        verify(supabase).signOut("link-jwt");
        verify(attempts).unlock(1L, AUTH_ID);
        verify(audit).record(eq(AuditAction.PASSWORD_SET), eq("PROFESSOR"), eq(1L), anyString(), any(), any());
    }

    @Test
    void setPasswordWithAnInvalidLinkOrAPersonOutsideTheDirectoryFails() {
        when(supabase.verifyTokenHash("recovery", "usado"))
                .thenThrow(new SupabaseAuthClient.InvalidCredentialsException("otp_expired"));
        assertThatThrownBy(() -> service.setPassword(new SecurityDtos.SetPasswordRequest("usado", "recovery", "clave-segura-1")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));

        when(supabase.verifyTokenHash("recovery", "ajeno")).thenReturn(session("jwt"));
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.empty());
        when(professors.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setPassword(new SecurityDtos.SetPasswordRequest("ajeno", "recovery", "clave-segura-1")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(supabase, never()).updatePassword(any(), anyString());
    }

    @Test
    void setPasswordRejectsOverlongPasswordsBeforeSpendingTheLink() {
        String password = "ñ".repeat(37);

        assertThatThrownBy(() -> service.setPassword(new SecurityDtos.SetPasswordRequest("hash", "invite", password)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo(AuthService.PASSWORD_TOO_LONG);
                });
        verifyNoInteractions(supabase);
    }

    @Test
    void setPasswordRejectedBySupabaseSaysTheLinkIsSpentAndClosesTheLinkSession() {
        when(supabase.verifyTokenHash("invite", "hash")).thenReturn(session("link-jwt"));
        when(professors.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(professor(AUTH_ID)));
        doThrow(new SupabaseAuthClient.RejectedException("weak_password")).when(supabase).updatePassword(AUTH_ID, "clave-segura-1");

        assertThatThrownBy(() -> service.setPassword(new SecurityDtos.SetPasswordRequest("hash", "invite", "clave-segura-1")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).endsWith(AuthService.LINK_SPENT);
                });
        verify(supabase).signOut("link-jwt");
        verify(attempts, never()).unlock(any(), any());
    }

    @Test
    void changePasswordChecksTheCurrentPasswordFirst() {
        Professor professor = professor(AUTH_ID);
        when(professors.findById(1L)).thenReturn(Optional.of(professor));
        when(supabase.signInWithPassword(EMAIL, "clave-actual-1"))
                .thenThrow(new SupabaseAuthClient.InvalidCredentialsException("invalid_credentials"));

        assertThatThrownBy(() -> service.changePassword(user(), new SecurityDtos.ChangePasswordRequest("clave-actual-1", "clave-nueva-12")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(attempts).recordFailure(1L);
        verify(supabase, never()).updatePassword(any(), anyString());
    }

    @Test
    void changePasswordUpdatesAndAudits() {
        Professor professor = professor(AUTH_ID);
        when(professors.findById(1L)).thenReturn(Optional.of(professor));
        when(supabase.signInWithPassword(EMAIL, "clave-actual-1")).thenReturn(session("check"));

        service.changePassword(user(), new SecurityDtos.ChangePasswordRequest("clave-actual-1", "clave-nueva-12"));

        verify(supabase).updatePassword(AUTH_ID, "clave-nueva-12");
        verify(audit).record(eq(AuditAction.PASSWORD_CHANGED), eq("PROFESSOR"), eq(1L), anyString(), any(), any());
    }

    @Test
    void changePasswordRejectsReusingTheCurrentPassword() {
        when(professors.findById(1L)).thenReturn(Optional.of(professor(AUTH_ID)));

        assertThatThrownBy(() -> service.changePassword(user(), new SecurityDtos.ChangePasswordRequest("clave-igual-1", "clave-igual-1")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(supabase);
    }

    @Test
    void logoutIgnoresMissingTokensAndSupabaseFailures() {
        service.logout(null);
        verifyNoInteractions(supabase);

        doThrow(new SupabaseAuthClient.UnavailableException("down")).when(supabase).signOut("jwt");
        service.logout("jwt");
        verify(supabase).signOut("jwt");
    }

    private static void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(exception.getMessage()).isEqualTo(AuthService.INVALID_LOGIN);
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

    private static SupabaseAuthClient.Session session(String token) {
        return new SupabaseAuthClient.Session(token, 3600, 1900000000L, AUTH_ID, EMAIL);
    }

    private static ExternalUserContext user() {
        return new ExternalUserContext(1L, EMAIL, EMAIL, List.of("CONTRIBUTOR"), List.of(), 1L, "Persona", null);
    }

    private static Jwt jwt(String subject, String email) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token").header("alg", "ES256").subject(subject).claim("email", email)
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
    }
}
