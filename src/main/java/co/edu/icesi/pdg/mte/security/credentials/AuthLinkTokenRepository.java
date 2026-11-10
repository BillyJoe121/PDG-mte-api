package co.edu.icesi.pdg.mte.security.credentials;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthLinkTokenRepository extends JpaRepository<AuthLinkToken, Long> {

    Optional<AuthLinkToken> findByTokenHash(String tokenHash);

    /** Marks a link as used only if nobody used it first; returns 1 when this call won. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuthLinkToken link set link.usedAt = :now where link.id = :id and link.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    /** A new link replaces the person's earlier unused links. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuthLinkToken link set link.usedAt = :now where link.userId = :userId and link.usedAt is null")
    int supersedeUnused(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AuthLinkToken link where link.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
