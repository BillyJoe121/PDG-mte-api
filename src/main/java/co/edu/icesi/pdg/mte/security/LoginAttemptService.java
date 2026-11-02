package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Counts consecutive failed sign-ins per account. Each method commits on its own so a failure
 * is stored even though the sign-in request ends with an error.
 */
@Service
public class LoginAttemptService {
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final ProfessorRepository professorRepository;
    private final AuditService auditService;
    private final Clock clock;

    public LoginAttemptService(ProfessorRepository professorRepository, AuditService auditService, Clock clock) {
        this.professorRepository = professorRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    public boolean isLocked(Professor professor) {
        return professor.getLockedUntil() != null && professor.getLockedUntil().isAfter(clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long professorId) {
        Professor professor = professorRepository.findById(professorId).orElse(null);
        if (professor == null) {
            return;
        }
        Instant now = clock.instant();
        if (professor.getLockedUntil() != null && !professor.getLockedUntil().isAfter(now)) {
            professor.setLockedUntil(null);
            professor.setFailedLoginAttempts(0);
        }
        int attempts = professor.getFailedLoginAttempts() + 1;
        professor.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS && professor.getLockedUntil() == null) {
            professor.setLockedUntil(now.plus(LOCK_DURATION));
            auditService.record(
                    AuditAction.ACCOUNT_LOCKED,
                    "PROFESSOR",
                    professor.getId(),
                    "Cuenta bloqueada " + LOCK_DURATION.toMinutes() + " minutos tras " + attempts + " intentos fallidos",
                    null,
                    Map.of("email", professor.getEmail(), "lockedUntil", professor.getLockedUntil().toString())
            );
        }
        professorRepository.save(professor);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long professorId, UUID authUserId) {
        reset(professorId, authUserId, true);
    }

    /** Clears the lock after an administrator issues an access link or a password is set with one. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void unlock(Long professorId, UUID authUserId) {
        reset(professorId, authUserId, false);
    }

    private void reset(Long professorId, UUID authUserId, boolean signedIn) {
        professorRepository.findById(professorId).ifPresent(professor -> {
            professor.setFailedLoginAttempts(0);
            professor.setLockedUntil(null);
            if (signedIn) {
                professor.setLastAccessAt(clock.instant());
            }
            if (professor.getAuthUserId() == null && authUserId != null) {
                professor.setAuthUserId(authUserId);
            }
            professorRepository.save(professor);
        });
    }
}
