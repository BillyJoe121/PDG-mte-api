package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthContextController {
    private final AccessControlService accessControlService;

    public AuthContextController(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    @GetMapping("/me")
    public SecurityDtos.AuthMeResponse me(Authentication authentication) {
        ExternalUserContext context = authentication != null && authentication.getPrincipal() instanceof ExternalUserContext external
                ? external
                : anonymousContext();
        var normalizedRoles = accessControlService.normalizeToList(context.roles());
        return new SecurityDtos.AuthMeResponse(
                new SecurityDtos.UserContextResponse(
                        context.externalUserId(),
                        context.username(),
                        context.email(),
                        normalizedRoles,
                        context.permissions(),
                        context.externalProfessorId(),
                        context.professorName(),
                        context.departmentName()
                ),
                AccessControlService.SUPPORTED_ROLES,
                accessControlService.capabilities(normalizedRoles)
        );
    }

    private ExternalUserContext anonymousContext() {
        return new ExternalUserContext(null, null, null, java.util.List.of(), java.util.List.of(), null, null, null);
    }
}
