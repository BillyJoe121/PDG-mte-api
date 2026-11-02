package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Sign-in, sign-out and passwords against Supabase Auth, plus resolution of the MTE person
 * behind a validated access token. Roles always come from the MTE directory.
 */
@Service
public class AuthService {
    static final String INVALID_LOGIN = "No pudimos iniciar sesión con esos datos. "
            + "Después de 5 intentos fallidos la cuenta se bloquea 15 minutos.";
    static final String AUTH_UNAVAILABLE = "El servicio de autenticación no está disponible. Intenta de nuevo en unos minutos.";
    static final String INVALID_LINK = "El enlace no es válido o ya venció. Pide uno nuevo al administrador.";
    static final String NO_ACCESS = "Tu cuenta no tiene acceso al MTE. Habla con el administrador.";
    static final String PASSWORD_TOO_LONG = "La contraseña no puede superar 72 caracteres (las letras con tilde y la ñ cuentan como dos).";
    static final String LINK_SPENT = " El enlace ya se usó: pide uno nuevo al administrador.";
    /** Supabase solo admite 72 bytes de contraseña. */
    private static final int PASSWORD_MAX_BYTES = 72;

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SupabaseAuthClient supabaseAuthClient;
    private final ProfessorRepository professorRepository;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    public AuthService(
            SupabaseAuthClient supabaseAuthClient,
            ProfessorRepository professorRepository,
            LoginAttemptService loginAttemptService,
            AuditService auditService
    ) {
        this.supabaseAuthClient = supabaseAuthClient;
        this.professorRepository = professorRepository;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
    }

    public SecurityDtos.LoginResponse login(SecurityDtos.LoginRequest request) {
        String email = normalizeEmail(request.email());
        Professor professor = professorRepository.findByEmailIgnoreCase(email)
                .filter(Professor::isActive)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN));
        if (loginAttemptService.isLocked(professor)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        }

        SupabaseAuthClient.Session session;
        try {
            session = supabaseAuthClient.signInWithPassword(email, request.password());
        } catch (SupabaseAuthClient.InvalidCredentialsException exception) {
            loginAttemptService.recordFailure(professor.getId());
            throw new BusinessException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        } catch (SupabaseAuthClient.SupabaseException exception) {
            log.warn("Sign-in could not reach Supabase Auth: {}", exception.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, AUTH_UNAVAILABLE);
        }

        if (professor.getAuthUserId() != null && !professor.getAuthUserId().equals(session.userId())) {
            log.warn("Sign-in rejected: account does not match the directory person {}", professor.getId());
            throw new BusinessException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        }
        loginAttemptService.recordSuccess(professor.getId(), session.userId());
        return new SecurityDtos.LoginResponse(session.accessToken(), "Bearer", session.expiresIn(), session.expiresAt());
    }

    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            supabaseAuthClient.signOut(accessToken);
        } catch (SupabaseAuthClient.SupabaseException exception) {
            log.info("Supabase sign-out did not complete: {}", exception.getMessage());
        }
    }

    public void changePassword(ExternalUserContext user, SecurityDtos.ChangePasswordRequest request) {
        Professor professor = professorRepository.findById(user.externalUserId())
                .filter(Professor::isActive)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, NO_ACCESS));
        requireSupportedLength(request.newPassword());
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe ser distinta de la actual.");
        }
        if (loginAttemptService.isLocked(professor)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_LOGIN);
        }

        SupabaseAuthClient.Session check;
        try {
            check = supabaseAuthClient.signInWithPassword(professor.getEmail(), request.currentPassword());
        } catch (SupabaseAuthClient.InvalidCredentialsException exception) {
            loginAttemptService.recordFailure(professor.getId());
            throw new BusinessException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta.");
        } catch (SupabaseAuthClient.SupabaseException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, AUTH_UNAVAILABLE);
        }
        if (professor.getAuthUserId() == null || !professor.getAuthUserId().equals(check.userId())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, NO_ACCESS);
        }
        updatePassword(check.userId(), request.newPassword());
        logout(check.accessToken());
        auditService.record(AuditAction.PASSWORD_CHANGED, "PROFESSOR", professor.getId(),
                "Contraseña cambiada por " + professor.getEmail(), null, null);
    }

    public void setPassword(SecurityDtos.SetPasswordRequest request) {
        // Verificar el enlace lo consume, así que todo lo que se pueda validar va antes.
        requireSupportedLength(request.password());
        SupabaseAuthClient.Session session;
        try {
            session = supabaseAuthClient.verifyTokenHash(request.type(), request.tokenHash());
        } catch (SupabaseAuthClient.InvalidCredentialsException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_LINK);
        } catch (SupabaseAuthClient.SupabaseException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, AUTH_UNAVAILABLE);
        }
        Professor professor = professorRepository.findByAuthUserId(session.userId())
                .or(() -> session.email() == null
                        ? java.util.Optional.empty()
                        : professorRepository.findByEmailIgnoreCase(session.email())
                                .filter(candidate -> candidate.getAuthUserId() == null))
                .filter(Professor::isActive)
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, NO_ACCESS));

        try {
            updatePassword(session.userId(), request.password());
        } catch (BusinessException exception) {
            throw new BusinessException(exception.status(), exception.getMessage() + LINK_SPENT);
        } finally {
            logout(session.accessToken());
        }
        loginAttemptService.unlock(professor.getId(), session.userId());
        auditService.record(AuditAction.PASSWORD_SET, "PROFESSOR", professor.getId(),
                "Contraseña fijada con enlace de acceso por " + professor.getEmail(), null, null);
    }

    /**
     * Maps a validated token to the MTE person. Links the account by email on first use and
     * rejects unknown or inactive people on every request.
     */
    @Transactional
    public ExternalUserContext resolve(Jwt jwt) {
        UUID authUserId;
        try {
            authUserId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new BadCredentialsException("Token sin sujeto válido.");
        }
        String email = jwt.getClaimAsString("email");
        Professor professor = professorRepository.findByAuthUserId(authUserId)
                .or(() -> linkByEmail(email, authUserId))
                .orElseThrow(() -> new DisabledException(NO_ACCESS));
        if (!professor.isActive()) {
            throw new DisabledException(NO_ACCESS);
        }
        String role = professor.getAccessRole();
        return new ExternalUserContext(
                professor.getId(),
                professor.getEmail(),
                professor.getEmail(),
                List.of(role),
                List.of(),
                professor.getId(),
                professor.getName(),
                professor.getDepartment() == null ? null : professor.getDepartment().getName()
        );
    }

    private java.util.Optional<Professor> linkByEmail(String email, UUID authUserId) {
        if (email == null || email.isBlank()) {
            return java.util.Optional.empty();
        }
        return professorRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(candidate -> candidate.getAuthUserId() == null)
                .map(candidate -> {
                    candidate.setAuthUserId(authUserId);
                    return professorRepository.save(candidate);
                });
    }

    private void updatePassword(UUID authUserId, String password) {
        try {
            supabaseAuthClient.updatePassword(authUserId, password);
        } catch (SupabaseAuthClient.RejectedException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "La contraseña no cumple las reglas. Usa al menos 10 caracteres y evita contraseñas comunes.");
        } catch (SupabaseAuthClient.SupabaseException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, AUTH_UNAVAILABLE);
        }
    }

    private static void requireSupportedLength(String password) {
        if (password != null && password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, PASSWORD_TOO_LONG);
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
