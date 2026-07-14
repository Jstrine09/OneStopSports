package com.onestopsports.repository;

import com.onestopsports.model.RefreshToken;
import com.onestopsports.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

// Database operations for the refresh_token table.
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Look up a stored token by its SHA-256 hash. The refresh flow hashes the raw cookie
    // value and calls this — we never search by the raw token (we don't store it).
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Revoke EVERY active token for a user in one statement. Used when we detect a stolen
    // token being reused (revoke all their sessions as a precaution). @Modifying tells
    // Spring Data this query changes rows rather than reading them.
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user = :user AND r.revoked = false")
    void revokeAllForUser(@Param("user") UserAccount user);

    // Housekeeping: delete tokens that expired before the given instant, so the table
    // doesn't grow forever. Called periodically by RefreshTokenService.
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff")
    void deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
