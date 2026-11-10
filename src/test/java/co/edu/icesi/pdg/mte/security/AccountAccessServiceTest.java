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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AccountAccessServiceTest {
    private static final String EMAIL = "persona@icesi.edu.co";
    private static final String PAGE = "https://mte.example.edu/establecer-contrasena";
    private static final UUID AUTH_ID = UUID.fromString("8f9c3a4e-2b1d-4c5e-9f00-112233445566");

    private final ProfessorRepository professors = mock(ProfessorRepository.class);
    private final CredentialService credentials = mock(CredentialService.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final AuditService audit = mock(AuditService.class);

    @Test
    void newPersonGetsAnInviteLinkPointingToTheMtePage() {
        when(professors.findById(5L)).thenReturn(Optional.of(professor(null)));
        when(credentials.ensureAccount(null)).thenReturn(new CredentialService.Account(AUTH_ID, true));
        when(credentials.issueLink(AUTH_ID, "invite")).thenReturn("a-b_c");

        SecurityDtos.AccessLinkResponse response = service(PAGE).issueAccessLink(5L);

        assertThat(response.accountCreated()).isTrue();
        assertThat(response.url()).isEqualTo(PAGE + "?token_hash=a-b_c&type=invite");
        verify(attempts).unlock(5L, AUTH_ID);
        verify(audit).record(eq(AuditAction.ACCESS_LINK_CREATED), eq("DIRECTORY_USER"), eq(5L), anyString(), any(), any());
    }

    @Test
    void personWithAPasswordGetsARecoveryLinkForTheSameAccount() {
        when(professors.findById(5L)).thenReturn(Optional.of(professor(AUTH_ID)));
        when(credentials.ensureAccount(AUTH_ID)).thenReturn(new CredentialService.Account(AUTH_ID, false));
        when(credentials.hasPassword(AUTH_ID)).thenReturn(true);
        when(credentials.issueLink(AUTH_ID, "recovery")).thenReturn("token");

        SecurityDtos.AccessLinkResponse response = service(PAGE + "?from=admin").issueAccessLink(5L);

        assertThat(response.accountCreated()).isFalse();
        assertThat(response.url()).isEqualTo(PAGE + "?from=admin&token_hash=token&type=recovery");
    }

    @Test
    void inactivePeopleMissingConfigurationAndUnknownPeopleAreReported() {
        Professor inactive = professor(null);
        inactive.setActive(false);
        when(professors.findById(5L)).thenReturn(Optional.of(inactive));
        assertStatus(() -> service(PAGE).issueAccessLink(5L), HttpStatus.CONFLICT);

        when(professors.findById(6L)).thenReturn(Optional.of(professor(null)));
        assertStatus(() -> service("").issueAccessLink(6L), HttpStatus.SERVICE_UNAVAILABLE);

        when(professors.findById(7L)).thenReturn(Optional.empty());
        assertStatus(() -> service(PAGE).issueAccessLink(7L), HttpStatus.NOT_FOUND);
        verifyNoInteractions(credentials, attempts, audit);
    }

    private AccountAccessService service(String accessLinkUrl) {
        AuthProperties properties = new AuthProperties(null, null, null, null, accessLinkUrl, false);
        return new AccountAccessService(professors, credentials, properties, attempts, audit);
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.status()).isEqualTo(status));
    }

    private static Professor professor(UUID authUserId) {
        Professor professor = new Professor();
        professor.setId(5L);
        professor.setName("Persona");
        professor.setEmail(EMAIL);
        professor.setAccessRole("CONTRIBUTOR");
        professor.setAuthUserId(authUserId);
        return professor;
    }
}
