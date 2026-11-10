package co.edu.icesi.pdg.mte.security.credentials;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RevokedAccessToken token where token.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
