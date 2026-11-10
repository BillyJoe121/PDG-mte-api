package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.security.credentials.RevokedAccessToken;
import co.edu.icesi.pdg.mte.security.credentials.RevokedAccessTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Issues access tokens at sign-in and closes them at sign-out. */
@Service
public class AccessTokenService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final RevokedAccessTokenRepository revoked;
    private final AuthProperties properties;
    private final Clock clock;

    public AccessTokenService(
            JwtEncoder encoder,
            @Qualifier("jwtDecoder") JwtDecoder decoder,
            RevokedAccessTokenRepository revoked,
            AuthProperties properties,
            Clock clock
    ) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.revoked = revoked;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(UUID userId, String email) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(AccessTokenConfig.AUDIENCE))
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, properties.accessTokenTtl().toSeconds(), expiresAt.getEpochSecond());
    }

    /** Closes a token so it is refused from now on. Invalid or already closed tokens are ignored. */
    @Transactional
    public void revoke(String token) {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException exception) {
            return;
        }
        if (jwt.getId() == null || jwt.getExpiresAt() == null) {
            return;
        }
        revoked.deleteExpiredBefore(clock.instant());
        revoked.save(new RevokedAccessToken(jwt.getId(), jwt.getExpiresAt()));
    }

    public record IssuedToken(String accessToken, long expiresIn, long expiresAt) {
    }
}
