package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.security.credentials.RevokedAccessTokenRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

/**
 * Signs and validates the API's access tokens (HS256) with the secret in
 * {@code MTE_AUTH_JWT_SECRET}: signature, expiry, issuer, audience and sign-out.
 */
@Configuration
public class AccessTokenConfig {
    static final String AUDIENCE = "mte";
    private static final String ALGORITHM = "HmacSHA256";
    private static final Logger log = LoggerFactory.getLogger(AccessTokenConfig.class);

    @Bean
    AccessTokenKey accessTokenKey(AuthProperties properties) {
        if (properties.hasJwtSecret()) {
            byte[] secret = properties.jwtSecret().trim().getBytes(StandardCharsets.UTF_8);
            if (secret.length < AuthProperties.MIN_SECRET_BYTES) {
                throw new IllegalStateException("MTE_AUTH_JWT_SECRET must have at least "
                        + AuthProperties.MIN_SECRET_BYTES + " bytes.");
            }
            return new AccessTokenKey(new SecretKeySpec(secret, ALGORITHM));
        }
        if (properties.requireJwtSecret()) {
            throw new IllegalStateException("MTE_AUTH_JWT_SECRET is required in this environment.");
        }
        log.warn("MTE_AUTH_JWT_SECRET is not set: using a temporary key, sessions end when the API restarts.");
        byte[] secret = new byte[AuthProperties.MIN_SECRET_BYTES * 2];
        new SecureRandom().nextBytes(secret);
        return new AccessTokenKey(new SecretKeySpec(secret, ALGORITHM));
    }

    @Bean
    JwtEncoder jwtEncoder(AccessTokenKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key.secretKey()));
    }

    @Bean
    JwtDecoder jwtDecoder(AccessTokenKey key, AuthProperties properties, RevokedAccessTokenRepository revoked) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                audienceValidator(),
                notRevoked(revoked)
        ));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> audienceValidator() {
        return jwt -> {
            List<String> audience = jwt.getAudience();
            return audience != null && audience.contains(AUDIENCE)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unexpected audience.", null));
        };
    }

    static OAuth2TokenValidator<Jwt> notRevoked(RevokedAccessTokenRepository revoked) {
        return jwt -> jwt.getId() == null || revoked.existsById(jwt.getId())
                ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Session closed.", null))
                : OAuth2TokenValidatorResult.success();
    }

    /** Holder so the key is injected by type without exposing a bare {@link SecretKey} bean. */
    public record AccessTokenKey(SecretKey secretKey) {
    }
}
