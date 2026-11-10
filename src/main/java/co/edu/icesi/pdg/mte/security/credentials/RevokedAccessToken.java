package co.edu.icesi.pdg.mte.security.credentials;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Access token closed with sign-out. Kept only until the token would have expired anyway. */
@Entity
@Table(name = "auth_revoked_token")
public class RevokedAccessToken {

    @Id
    @Column(name = "token_id", nullable = false, length = 64)
    private String tokenId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected RevokedAccessToken() {
    }

    public RevokedAccessToken(String tokenId, Instant expiresAt) {
        this.tokenId = tokenId;
        this.expiresAt = expiresAt;
    }

    public String getTokenId() {
        return tokenId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
