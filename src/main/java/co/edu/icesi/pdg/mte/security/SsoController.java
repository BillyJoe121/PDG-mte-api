package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth/sso")
public class SsoController {
    private final SsoProperties properties;
    private final AuthProperties authProperties;
    private final SsoTokenClient tokenClient;

    public SsoController(SsoProperties properties, AuthProperties authProperties, SsoTokenClient tokenClient) {
        this.properties = properties;
        this.authProperties = authProperties;
        this.tokenClient = tokenClient;
    }

    @GetMapping("/config")
    public SecurityDtos.SsoConfigResponse config() {
        boolean enabled = available();
        return new SecurityDtos.SsoConfigResponse(
                enabled,
                properties.displayName(),
                enabled ? properties.authorizationUri() : null,
                enabled ? properties.clientId() : null,
                enabled ? properties.redirectUri() : null,
                enabled ? properties.safeScopes() : java.util.List.of()
        );
    }

    @PostMapping("/exchange")
    public SecurityDtos.SsoTokenResponse exchange(@Valid @RequestBody SecurityDtos.SsoExchangeRequest request) {
        if (!available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El SSO institucional no está configurado.");
        }
        try {
            return tokenClient.exchange(request.code(), request.codeVerifier());
        } catch (BadCredentialsException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El código SSO no es válido o ya fue utilizado.");
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No fue posible completar el inicio de sesión institucional.");
        }
    }

    private boolean available() {
        return authProperties.externalMode() && properties.configured();
    }
}
