package co.edu.icesi.pdg.mte.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the API's own sign-in. The signing secret has no default value and never leaves
 * the server; when {@code requireJwtSecret} is set the application refuses to start without it.
 */
@ConfigurationProperties(prefix = "mte.auth")
public record AuthProperties(
        String jwtSecret,
        String issuer,
        Duration accessTokenTtl,
        Duration accessLinkTtl,
        String accessLinkUrl,
        boolean requireJwtSecret
) {
    public static final String DEFAULT_ISSUER = "mte-api";
    /** HS256 needs a key of at least 256 bits. */
    public static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        issuer = hasText(issuer) ? issuer.trim() : DEFAULT_ISSUER;
        accessTokenTtl = accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                ? Duration.ofHours(1) : accessTokenTtl;
        accessLinkTtl = accessLinkTtl == null || accessLinkTtl.isNegative() || accessLinkTtl.isZero()
                ? Duration.ofHours(72) : accessLinkTtl;
    }

    public boolean hasJwtSecret() {
        return hasText(jwtSecret);
    }

    public boolean hasAccessLinkUrl() {
        return hasText(accessLinkUrl);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
