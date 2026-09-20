package co.edu.icesi.pdg.mte.api.dto;

import java.util.List;
import java.util.Map;
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

    public record SsoConfigResponse(
            boolean enabled,
            String providerName,
            String authorizationUri,
            String clientId,
            String redirectUri,
            List<String> scopes
    ) {
    }

    public record SsoExchangeRequest(
            @NotBlank @Size(max = 2048) String code,
            @NotBlank @Size(min = 43, max = 128) @Pattern(regexp = "[A-Za-z0-9\\-._~]+") String codeVerifier
    ) {
    }

    public record SsoTokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn
    ) {
    }
}
