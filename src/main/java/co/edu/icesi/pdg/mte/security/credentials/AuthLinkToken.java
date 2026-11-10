package co.edu.icesi.pdg.mte.security.credentials;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Single-use access link. Only the SHA-256 of the token is stored, so a database copy cannot be
 * used to set anyone's password.
 */
@Entity
@Table(name = "auth_link_token")
public class AuthLinkToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "link_type", nullable = false, length = 16)
    private String linkType;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuthLinkToken() {
    }

    public AuthLinkToken(String tokenHash, UUID userId, String linkType, Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.linkType = linkType;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getLinkType() {
        return linkType;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
