package co.edu.icesi.pdg.mte.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Supabase Auth settings. The server key never leaves the API and has no default value.
 */
@ConfigurationProperties(prefix = "mte.supabase")
public record SupabaseProperties(String url, String serverKey, String accessLinkUrl) {

    public boolean configured() {
        return hasText(url) && hasText(serverKey);
    }

    public String authBaseUrl() {
        return trimTrailingSlash(url) + "/auth/v1";
    }

    public String issuer() {
        return authBaseUrl();
    }

    public String jwksUri() {
        return authBaseUrl() + "/.well-known/jwks.json";
    }

    public boolean hasAccessLinkUrl() {
        return hasText(accessLinkUrl);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
