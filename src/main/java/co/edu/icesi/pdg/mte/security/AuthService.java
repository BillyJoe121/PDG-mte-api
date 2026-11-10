package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import co.edu.icesi.pdg.mte.security.credentials.CredentialService;
import co.edu.icesi.pdg.mte.security.credentials.PasswordPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Sign-in, sign-out and passwords against the MTE's own accounts, plus resolution of the MTE
 * person behind a validated access token. Roles always come from the MTE directory.
 */
@Service
public class AuthService {
    static final String INVALID_LOGIN = "No pudimos iniciar sesión con esos datos. "
            + "Después de 5 intentos fallidos la cuenta se bloquea 15 minutos.";
    static final String INVALID_LINK = "El enlace no es válido o ya venció. Pide uno nuevo al administrador.";
    static final String NO_ACCESS = "Tu cuenta no tiene acceso al MTE. Habla con el administrador.";
    static final String PASSWORD_TOO_LONG = "La contraseña no puede superar 72 caracteres (las letras con tilde y la ñ cuentan como dos).";
    static final String WEAK_PASSWORD = "La contraseña no cumple las reglas. Usa al menos 10 caracteres, "
            + "evita contraseñas comunes y no uses tu correo.";

    private final CredentialService credentialService;
    private final AccessTokenService accessTokenService;
    private final ProfessorRepository professorRepository;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    public AuthService(
            CredentialService credentialService,
            AccessTokenService accessTokenService,
            ProfessorRepository professorRepository,
            LoginAttemptService loginAttemptService,
            AuditService auditService
    ) {
        this.credentialService = credentialService;
        this.accessTokenService = accessTokenService;
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
        try {
            credentialService.verifyPassword(professor.getAuthUserId(), request.password());
        } catch (CredentialService.InvalidCredentialsException exception) {
            loginAttemptService.recordFailure(professor.getId());
            throw new BusinessException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        }
        loginAttemptService.recordSuccess(professor.getId(), professor.getAuthUserId());
        AccessTokenService.IssuedToken token = accessTokenService.issue(professor.getAuthUserId(), professor.getEmail());
        return new SecurityDtos.LoginResponse(token.accessToken(), "Bearer", token.expiresIn(), token.expiresAt());
    }

    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        accessTokenService.revoke(accessToken);
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
        if (professor.getAuthUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, NO_ACCESS);
        }
        try {
            credentialService.verifyPassword(professor.getAuthUserId(), request.currentPassword());
        } catch (CredentialService.InvalidCredentialsException exception) {
            loginAttemptService.recordFailure(professor.getId());
            throw new BusinessException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta.");
        }
        storePassword(professor, request.newPassword());
        auditService.record(AuditAction.PASSWORD_CHANGED, "PROFESSOR", professor.getId(),
                "Contraseña cambiada por " + professor.getEmail(), null, null);
    }

    /**
     * Sets a password with an access link. Everything runs in one transaction: if the password is
     * refused the link is not spent and the person can try again with the same link.
     */
    @Transactional
    public void setPassword(SecurityDtos.SetPasswordRequest request) {
        requireSupportedLength(request.password());
        UUID userId;
        try {
            userId = credentialService.consumeLink(request.type(), request.tokenHash());
        } catch (CredentialService.InvalidCredentialsException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_LINK);
        }
        Professor professor = professorRepository.findByAuthUserId(userId)
                .filter(Professor::isActive)
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, NO_ACCESS));
        storePassword(professor, request.password());
        loginAttemptService.unlock(professor.getId(), userId);
        auditService.record(AuditAction.PASSWORD_SET, "PROFESSOR", professor.getId(),
                "Contraseña fijada con enlace de acceso por " + professor.getEmail(), null, null);
    }

    /** Maps a validated token to the MTE person and rejects unknown or inactive people on every request. */
    @Transactional(readOnly = true)
    public ExternalUserContext resolve(Jwt jwt) {
        UUID authUserId;
        try {
            authUserId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new BadCredentialsException("Token sin sujeto válido.");
        }
        Professor professor = professorRepository.findByAuthUserId(authUserId)
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

    private void storePassword(Professor professor, String password) {
        try {
            credentialService.setPassword(professor.getAuthUserId(), password, professor.getEmail());
        } catch (CredentialService.RejectedException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, WEAK_PASSWORD);
        } catch (CredentialService.InvalidCredentialsException exception) {
            throw new BusinessException(HttpStatus.FORBIDDEN, NO_ACCESS);
        }
    }

    private static void requireSupportedLength(String password) {
        if (PasswordPolicy.exceedsMaxBytes(password)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, PASSWORD_TOO_LONG);
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
