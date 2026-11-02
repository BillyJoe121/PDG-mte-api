package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LoginAttemptServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");

    private final ProfessorRepository professors = mock(ProfessorRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final LoginAttemptService service =
            new LoginAttemptService(professors, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    private Professor professor;

    @BeforeEach
    void setUp() {
        professor = new Professor();
        professor.setId(3L);
        professor.setEmail("persona@icesi.edu.co");
        when(professors.findById(3L)).thenReturn(Optional.of(professor));
    }

    @Test
    void fifthConsecutiveFailureLocksTheAccountForFifteenMinutesAndAuditsOnce() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            service.recordFailure(3L);
        }
        assertThat(professor.getFailedLoginAttempts()).isEqualTo(4);
        assertThat(service.isLocked(professor)).isFalse();

        service.recordFailure(3L);
        service.recordFailure(3L);

        assertThat(professor.getLockedUntil()).isEqualTo(NOW.plusSeconds(15 * 60));
        assertThat(service.isLocked(professor)).isTrue();
        verify(audit, times(1)).record(eq(AuditAction.ACCOUNT_LOCKED), eq("PROFESSOR"), eq(3L), anyString(), any(), any());
    }

    @Test
    void expiredLockStartsTheCountAgain() {
        professor.setFailedLoginAttempts(5);
        professor.setLockedUntil(NOW.minusSeconds(1));
        assertThat(service.isLocked(professor)).isFalse();

        service.recordFailure(3L);

        assertThat(professor.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(professor.getLockedUntil()).isNull();
        verifyNoInteractions(audit);
    }

    @Test
    void successClearsTheCountRecordsAccessAndLinksTheAccount() {
        UUID authUserId = UUID.randomUUID();
        professor.setFailedLoginAttempts(3);

        service.recordSuccess(3L, authUserId);

        assertThat(professor.getFailedLoginAttempts()).isZero();
        assertThat(professor.getLastAccessAt()).isEqualTo(NOW);
        assertThat(professor.getAuthUserId()).isEqualTo(authUserId);
    }

    @Test
    void unlockClearsTheLockWithoutTouchingLastAccessOrAnExistingLink() {
        UUID existing = UUID.randomUUID();
        professor.setAuthUserId(existing);
        professor.setFailedLoginAttempts(5);
        professor.setLockedUntil(NOW.plusSeconds(600));

        service.unlock(3L, UUID.randomUUID());

        assertThat(professor.getLockedUntil()).isNull();
        assertThat(professor.getFailedLoginAttempts()).isZero();
        assertThat(professor.getLastAccessAt()).isNull();
        assertThat(professor.getAuthUserId()).isEqualTo(existing);
    }

    @Test
    void unknownPersonIsIgnored() {
        when(professors.findById(99L)).thenReturn(Optional.empty());

        service.recordFailure(99L);

        verify(professors, never()).save(any());
    }
}
