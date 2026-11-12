package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.security.credentials.RevokedAccessTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityConfigurationBranchesTest {

    @Test
    void authPropertiesFallBackToDefaultsForMissingOrNonPositiveValues() {
        var defaults = new AuthProperties(null, "  ", null, null, null, false);
        assertThat(defaults.issuer()).isEqualTo(AuthProperties.DEFAULT_ISSUER);
        assertThat(defaults.accessTokenTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(defaults.accessLinkTtl()).isEqualTo(Duration.ofHours(72));

        var zero = new AuthProperties("s", "emisor", Duration.ZERO, Duration.ZERO, null, false);
        assertThat(zero.accessTokenTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(zero.accessLinkTtl()).isEqualTo(Duration.ofHours(72));

        var negative = new AuthProperties("s", " emisor ", Duration.ofMinutes(-5), Duration.ofSeconds(-1), null, false);
        assertThat(negative.issuer()).isEqualTo("emisor");
        assertThat(negative.accessTokenTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(negative.accessLinkTtl()).isEqualTo(Duration.ofHours(72));

        var custom = new AuthProperties("s", null, Duration.ofMinutes(5), Duration.ofHours(1), "http://x", false);
        assertThat(custom.accessTokenTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(custom.accessLinkTtl()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void authPropertiesReportWhetherSecretAndLinkUrlAreConfigured() {
        assertThat(new AuthProperties(null, null, null, null, null, false).hasJwtSecret()).isFalse();
        assertThat(new AuthProperties("   ", null, null, null, "  ", false).hasJwtSecret()).isFalse();
        assertThat(new AuthProperties("   ", null, null, null, "  ", false).hasAccessLinkUrl()).isFalse();
        assertThat(new AuthProperties("secret", null, null, null, "http://acceso", false).hasJwtSecret()).isTrue();
        assertThat(new AuthProperties("secret", null, null, null, "http://acceso", false).hasAccessLinkUrl()).isTrue();
    }

    @Test
    void corsOriginsDefaultToLocalDevelopmentOrigins() {
        assertThat(new CorsProperties(null).origins()).hasSize(3).allMatch(origin -> origin.startsWith("http://localhost"));
        assertThat(new CorsProperties(List.of()).origins()).hasSize(3);
        assertThat(new CorsProperties(Arrays.asList(" https://app.icesi.edu.co ", null, "  ", "https://otra.icesi.edu.co")).origins())
                .containsExactly("https://app.icesi.edu.co", "https://otra.icesi.edu.co");
    }

    @Test
    void roleNormalizationIgnoresBlanksAndStripsPrefixesAndAliases() {
        AccessControlService access = new AccessControlService();

        assertThat(access.normalizeToList(null)).isEmpty();
        assertThat(access.normalizeToList(Arrays.asList(null, " ", "role_admin", "usuario", "tutor", "visitante", "CUSTOM")))
                .containsExactly("ADMIN", "MANAGER", "CONTRIBUTOR", "VISITOR", "CUSTOM");
        assertThat(access.normalizeToList(List.of("ADMINISTRADOR", "decano", "profesor"))).containsExactly("ADMIN", "MANAGER", "CONTRIBUTOR");
    }

    @Test
    void audienceValidatorRequiresTheApiAudience() {
        var validator = AccessTokenConfig.audienceValidator();

        assertThat(validator.validate(jwtBuilder().audience(List.of("mte", "otra")).build()).hasErrors()).isFalse();
        assertThat(validator.validate(jwtBuilder().audience(List.of("otra")).build()).hasErrors()).isTrue();
        assertThat(validator.validate(jwtBuilder().build()).hasErrors()).isTrue();
    }

    @Test
    void notRevokedValidatorRefusesTokensWithoutIdOrAlreadyClosed() {
        RevokedAccessTokenRepository revoked = mock(RevokedAccessTokenRepository.class);
        when(revoked.existsById("cerrado")).thenReturn(true);
        var validator = AccessTokenConfig.notRevoked(revoked);

        assertThat(validator.validate(jwtBuilder().build()).hasErrors()).isTrue();
        assertThat(validator.validate(jwtBuilder().jti("cerrado").build()).hasErrors()).isTrue();
        assertThat(validator.validate(jwtBuilder().jti("vigente").build()).hasErrors()).isFalse();
    }

    @Test
    void signOutIgnoresTokensWithoutIdentifierOrExpiry() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        RevokedAccessTokenRepository revoked = mock(RevokedAccessTokenRepository.class);
        var properties = new AuthProperties(null, null, null, null, null, false);
        var service = new AccessTokenService(null, decoder, revoked, properties, Clock.systemUTC());
        when(decoder.decode("sin-id")).thenReturn(jwtBuilder().build());
        when(decoder.decode("sin-exp")).thenReturn(jwtBuilder().jti("abc").build());
        when(decoder.decode("completo")).thenReturn(jwtBuilder().jti("abc").expiresAt(Instant.now().plusSeconds(60)).build());

        service.revoke("sin-id");
        service.revoke("sin-exp");
        verify(revoked, never()).save(any());

        service.revoke("completo");
        verify(revoked).save(any());
    }

    private static Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject("persona");
    }
}
