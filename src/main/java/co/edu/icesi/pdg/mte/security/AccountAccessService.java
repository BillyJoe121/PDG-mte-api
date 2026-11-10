package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import co.edu.icesi.pdg.mte.security.credentials.CredentialService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Issues a single-use link so a person can set their password. The administrator delivers the
 * link; nothing is emailed and no temporary password exists.
 */
@Service
public class AccountAccessService {
    static final String INVITE = "invite";
    static final String RECOVERY = "recovery";

    private final ProfessorRepository professorRepository;
    private final CredentialService credentialService;
    private final AuthProperties properties;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    public AccountAccessService(
            ProfessorRepository professorRepository,
            CredentialService credentialService,
            AuthProperties properties,
            LoginAttemptService loginAttemptService,
            AuditService auditService
    ) {
        this.professorRepository = professorRepository;
        this.credentialService = credentialService;
        this.properties = properties;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
    }

    public SecurityDtos.AccessLinkResponse issueAccessLink(Long professorId) {
        Professor professor = professorRepository.findById(professorId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
        if (!professor.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Activa a la persona antes de generar su enlace de acceso.");
        }
        if (!properties.hasAccessLinkUrl()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Falta configurar la dirección de la página para fijar la contraseña.");
        }

        CredentialService.Account account = credentialService.ensureAccount(professor.getAuthUserId());
        String type = credentialService.hasPassword(account.userId()) ? RECOVERY : INVITE;
        // Linking first so the person behind the link is known when it is used.
        loginAttemptService.unlock(professor.getId(), account.userId());
        String token = credentialService.issueLink(account.userId(), type);

        auditService.record(AuditAction.ACCESS_LINK_CREATED, "DIRECTORY_USER", professor.getId(),
                "Enlace de acceso generado para " + professor.getEmail()
                        + (account.created() ? " (cuenta nueva)" : ""), null, null);
        return new SecurityDtos.AccessLinkResponse(professor.getId(), professor.getEmail(), url(token, type), account.created());
    }

    private String url(String token, String type) {
        String base = properties.accessLinkUrl().trim();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator
                + "token_hash=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&type=" + URLEncoder.encode(type, StandardCharsets.UTF_8);
    }
}
