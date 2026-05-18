package co.edu.icesi.pdg.mte.api.dto;

import java.util.List;
import java.util.Map;

public final class SecurityDtos {
    private SecurityDtos() {
    }

    public record AuthMeResponse(
            UserContextResponse user,
            List<String> supportedRoles,
            Map<String, Boolean> capabilities
    ) {
    }

    public record UserContextResponse(
            Long externalUserId,
            String username,
            String email,
            List<String> roles,
            List<String> permissions,
            Long externalProfessorId,
            String professorName,
            String departmentName
    ) {
    }
}
