package co.edu.icesi.pdg.mte.security.credentials;

import co.edu.icesi.pdg.mte.security.AuthProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Passwords and single-use access links stored in the MTE database. Passwords and raw link
 * tokens are never logged, stored in clear or echoed in errors.
 */
@Service
public class CredentialService {
    public static final int BCRYPT_STRENGTH = 12;
    private static final int LINK_TOKEN_BYTES = 32;

    private final AuthCredentialRepository credentials;
    private final AuthLinkTokenRepository links;
    private final AuthProperties properties;
    private final Clock clock;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    private final SecureRandom random = new SecureRandom();
    /** Compared against when there is no stored hash, so unknown accounts take as long as known ones. */
    private final String placeholderHash;

    public CredentialService(
            AuthCredentialRepository credentials,
            AuthLinkTokenRepository links,
            AuthProperties properties,
            Clock clock
    ) {
        this.credentials = credentials;
        this.links = links;
        this.properties = properties;
        this.clock = clock;
        this.placeholderHash = encoder.encode(UUID.randomUUID().toString());
    }

    /** Returns normally only when the account exists, has a password and it matches. */
    @Transactional(readOnly = true)
    public void verifyPassword(UUID userId, String password) {
        String hash = userId == null ? null : credentials.findById(userId)
                .map(AuthCredential::getPasswordHash)
                .orElse(null);
        if (password == null || PasswordPolicy.exceedsMaxBytes(password)) {
            encoder.matches("", placeholderHash);
            throw new InvalidCredentialsException();
        }
        boolean matches = encoder.matches(password, hash == null ? placeholderHash : hash);
        if (hash == null || !matches) {
            throw new InvalidCredentialsException();
        }
    }

    /** Stores a new password for an existing account after checking the policy. */
    @Transactional
    public void setPassword(UUID userId, String password, String email) {
        if (!PasswordPolicy.accepts(password, email)) {
            throw new RejectedException();
        }
        AuthCredential credential = credentials.findById(userId).orElseThrow(InvalidCredentialsException::new);
        credential.setPasswordHash(encoder.encode(password), clock.instant());
        credentials.save(credential);
    }

    /**
     * Returns the account of a directory person, creating it when it does not exist yet. A known
     * id (from a previous account) is kept so existing links stay valid.
     */
    @Transactional
    public Account ensureAccount(UUID knownUserId) {
        if (knownUserId != null) {
            if (credentials.existsById(knownUserId)) {
                return new Account(knownUserId, false);
            }
            credentials.save(new AuthCredential(knownUserId, clock.instant()));
            return new Account(knownUserId, true);
        }
        UUID userId = UUID.randomUUID();
        credentials.save(new AuthCredential(userId, clock.instant()));
        return new Account(userId, true);
    }

    @Transactional(readOnly = true)
    public boolean hasPassword(UUID userId) {
        return userId != null && credentials.findById(userId).map(c -> c.getPasswordHash() != null).orElse(false);
    }

    /** Issues a link and returns the raw token; earlier unused links of the account stop working. */
    @Transactional
    public String issueLink(UUID userId, String type) {
        Instant now = clock.instant();
        links.deleteExpiredBefore(now);
        links.supersedeUnused(userId, now);
        byte[] bytes = new byte[LINK_TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        links.save(new AuthLinkToken(sha256(token), userId, type, now, now.plus(properties.accessLinkTtl())));
        return token;
    }

    /**
     * Uses a link once and returns its account. Unknown, expired, already used or mismatched
     * links all fail the same way.
     */
    @Transactional
    public UUID consumeLink(String type, String token) {
        Instant now = clock.instant();
        AuthLinkToken link = token == null ? null : links.findByTokenHash(sha256(token)).orElse(null);
        if (link == null
                || !link.getLinkType().equals(type)
                || link.getUsedAt() != null
                || !link.getExpiresAt().isAfter(now)
                || links.markUsed(link.getId(), now) != 1) {
            throw new InvalidCredentialsException();
        }
        return link.getUserId();
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    public record Account(UUID userId, boolean created) {
    }

    public static class CredentialException extends RuntimeException {
        CredentialException(String message) {
            super(message);
        }
    }

    /** Wrong password, unknown account, or an invalid, used or expired link. */
    public static class InvalidCredentialsException extends CredentialException {
        public InvalidCredentialsException() {
            super("invalid_credentials");
        }
    }

    /** The new password does not meet {@link PasswordPolicy}. */
    public static class RejectedException extends CredentialException {
        public RejectedException() {
            super("weak_password");
        }
    }
}
