package com.onestopsports.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

// A refresh token is a long-lived credential we hand the browser in an httpOnly cookie.
// When the short-lived access token (JWT) expires, the frontend calls /api/auth/refresh and
// the browser automatically sends this cookie; we look the token up here to mint a fresh
// access token. Keeping these in the database (rather than making the refresh token a
// self-contained JWT) is what lets us REVOKE them — for logout, rotation, or theft detection.
//
// Security: we never store the raw token, only its SHA-256 hash (see RefreshTokenService).
// So even if this table leaked, the stored value can't be replayed as a cookie.
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The SHA-256 hash (as hex) of the raw token that lives in the user's cookie.
    // Unique because every incoming cookie is looked up by hashing it and matching here.
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    // Which user this token authenticates. LAZY (like every other @ManyToOne in this app)
    // so we don't pull the whole user row unless we actually need it.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    // Absolute expiry — after this instant the token is rejected even if not revoked.
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // Flipped to true on logout, when the token is rotated (the old one), or if we detect
    // an already-used token being replayed (possible theft).
    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // Convenience check used by the service: a token is usable only if it hasn't been
    // revoked and hasn't passed its expiry.
    public boolean isActive(Instant now) {
        return !revoked && expiresAt.isAfter(now);
    }
}
