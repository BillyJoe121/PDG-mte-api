package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.security.credentials.RevokedAccessToken;
import co.edu.icesi.pdg.mte.security.credentials.RevokedAccessTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccessTokenServiceTest {
    private static final String SECRET = "una-clave-de-prueba-con-mas-de-32-bytes";
    private static final UUID USER = UUID.fromString("8f9c3a4e-2b1d-4c5e-9f00-112233445566");
    private static final String EMAIL = "persona@icesi.edu.co";

    private final AccessTokenConfig config = new AccessTokenConfig();
    private final RevokedAccessTokenRepository revoked = mock(RevokedAccessTokenRepository.class);

    @Test
    void issuedTokensCarryTheAccountAndAreAccepted() {
        AuthProperties properties = properties(SECRET, "mte-api");
        JwtDecoder decoder = decoder(properties);
        AccessTokenService service = service(properties, decoder);

        AccessTokenService.IssuedToken issued = service.issue(USER, EMAIL);

        var jwt = decoder.decode(issued.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(USER.toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(EMAIL);
        assertThat(jwt.getAudience()).containsExactly("mte");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(issued.expiresIn()).isEqualTo(3600);
        assertThat(issued.expiresAt()).isEqualTo(jwt.getExpiresAt().getEpochSecond());
    }

    @Test
    void revokedTokensAreStoredAndRefused() {
        AuthProperties properties = properties(SECRET, "mte-api");
        JwtDecoder decoder = decoder(properties);
        AccessTokenService service = service(properties, decoder);
        String token = service.issue(USER, EMAIL).accessToken();

        service.revoke(token);

        ArgumentCaptor<RevokedAccessToken> saved = ArgumentCaptor.forClass(RevokedAccessToken.class);
        verify(revoked).save(saved.capture());
        String id = saved.getValue().getTokenId();
        when(revoked.existsById(id)).thenReturn(true);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void invalidTokensAreIgnoredOnSignOut() {
        AuthProperties properties = properties(SECRET, "mte-api");
        service(properties, decoder(properties)).revoke("no-es-un-token");
        verify(revoked, never()).save(any());
    }

    @Test
    void tokensFromAnotherKeyOrIssuerAreRefused() {
        AuthProperties properties = properties(SECRET, "mte-api");
        String token = service(properties, decoder(properties)).issue(USER, EMAIL).accessToken();

        JwtDecoder otherKey = decoder(properties("otra-clave-de-prueba-con-mas-de-32-bytes", "mte-api"));
        JwtDecoder otherIssuer = decoder(properties(SECRET, "otro-emisor"));
        assertThatThrownBy(() -> otherKey.decode(token)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> otherIssuer.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void theSecretIsRequiredWhereConfiguredAndMustBeLongEnough() {
        assertThatThrownBy(() -> config.accessTokenKey(new AuthProperties(null, null, null, null, null, true)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.accessTokenKey(properties("corta", "mte-api")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(config.accessTokenKey(new AuthProperties(null, null, null, null, null, false))).isNotNull();
    }

    private static AuthProperties properties(String secret, String issuer) {
        return new AuthProperties(secret, issuer, Duration.ofHours(1), null, null, false);
    }

    private JwtDecoder decoder(AuthProperties properties) {
        return config.jwtDecoder(config.accessTokenKey(properties), properties, revoked);
    }

    private AccessTokenService service(AuthProperties properties, JwtDecoder decoder) {
        var key = config.accessTokenKey(properties);
        return new AccessTokenService(config.jwtEncoder(key), decoder, revoked, properties, Clock.systemUTC());
    }
}
