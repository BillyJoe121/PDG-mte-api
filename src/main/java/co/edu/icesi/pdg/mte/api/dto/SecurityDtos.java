package co.edu.icesi.pdg.mte.api.dto;

import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

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

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password
    ) {
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            long expiresAt
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 10, max = 72) String newPassword
    ) {
    }

    public record SetPasswordRequest(
            @NotBlank @Size(max = 512) @Pattern(regexp = "[A-Za-z0-9_-]+") String tokenHash,
            @NotBlank @Pattern(regexp = "invite|recovery") String type,
            @NotBlank @Size(min = 10, max = 72) String password
    ) {
    }

    public record AccessLinkResponse(
            Long professorId,
            String email,
            String url,
            boolean accountCreated
    ) {
    }
}
