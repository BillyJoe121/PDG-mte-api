package co.edu.icesi.pdg.mte.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "mte.auth.sso")
public record SsoProperties(
        boolean enabled,
        String providerName,
        String authorizationUri,
        String tokenUri,
        String clientId,
        String clientSecret,
        String redirectUri,
        List<String> scopes
) {
    public boolean configured() {
        return enabled
                && hasText(authorizationUri)
                && hasText(tokenUri)
                && hasText(clientId)
                && hasText(redirectUri);
    }

    public String displayName() {
        return hasText(providerName) ? providerName.trim() : "ICESI";
    }

    public List<String> safeScopes() {
        return scopes == null || scopes.isEmpty()
                ? List.of("openid", "profile", "email")
                : scopes.stream().filter(SsoProperties::hasText).map(String::trim).distinct().toList();
    }

    public boolean hasClientSecret() {
        return hasText(clientSecret);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
