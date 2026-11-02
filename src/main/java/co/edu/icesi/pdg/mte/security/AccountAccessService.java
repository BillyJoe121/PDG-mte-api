package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
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
    private final ProfessorRepository professorRepository;
    private final SupabaseAuthClient supabaseAuthClient;
    private final SupabaseProperties properties;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    public AccountAccessService(
            ProfessorRepository professorRepository,
            SupabaseAuthClient supabaseAuthClient,
            SupabaseProperties properties,
            LoginAttemptService loginAttemptService,
            AuditService auditService
    ) {
        this.professorRepository = professorRepository;
        this.supabaseAuthClient = supabaseAuthClient;
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

        boolean created = false;
        SupabaseAuthClient.GeneratedLink link;
        try {
            if (professor.getAuthUserId() == null) {
                try {
                    link = supabaseAuthClient.generateLink("invite", professor.getEmail());
                    created = true;
                } catch (SupabaseAuthClient.EmailExistsException exception) {
                    link = supabaseAuthClient.generateLink("recovery", professor.getEmail());
                }
            } else {
                link = supabaseAuthClient.generateLink("recovery", professor.getEmail());
            }
        } catch (SupabaseAuthClient.SupabaseException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No fue posible generar el enlace con el servicio de autenticación.");
        }
        if (professor.getAuthUserId() != null && !professor.getAuthUserId().equals(link.userId())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "La cuenta de acceso de ese correo no corresponde a esta persona del directorio.");
        }

        loginAttemptService.unlock(professor.getId(), link.userId());
        auditService.record(AuditAction.ACCESS_LINK_CREATED, "DIRECTORY_USER", professor.getId(),
                "Enlace de acceso generado para " + professor.getEmail()
                        + (created ? " (cuenta nueva)" : ""), null, null);
        return new SecurityDtos.AccessLinkResponse(professor.getId(), professor.getEmail(), url(link), created);
    }

    private String url(SupabaseAuthClient.GeneratedLink link) {
        String base = properties.accessLinkUrl().trim();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator
                + "token_hash=" + URLEncoder.encode(link.hashedToken(), StandardCharsets.UTF_8)
                + "&type=" + URLEncoder.encode(link.verificationType(), StandardCharsets.UTF_8);
    }
}
