package com.onestopsports.service;

import com.onestopsports.model.RefreshToken;
import com.onestopsports.model.UserAccount;
import com.onestopsports.repository.RefreshTokenRepository;
import com.onestopsports.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

// Issues, validates, rotates and revokes refresh tokens — the long-lived credential that
// lets a user stay signed in without keeping a stealable token in the browser's storage.
//
// The raw token is a big random string handed to the browser in an httpOnly cookie.
// We store only its SHA-256 hash in the database, so a leaked table can't be replayed.
// Every use ROTATES the token (old one revoked, new one issued) so a token is valid once.
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    // How long a refresh token stays valid, in milliseconds. Defaults to 30 days.
    // Overridable via app.auth.refresh.expiration-ms (e.g. per environment).
    @Value("${app.auth.refresh.expiration-ms:2592000000}")
    private long refreshExpirationMs;

    // Cryptographically strong randomness for the token value. SecureRandom (not Random)
    // because these tokens are credentials — they must be unguessable.
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    // Mints a brand-new refresh token for the given username and returns the RAW value
    // (the caller puts this in the httpOnly cookie). Called on register and login.
    @Transactional
    public String issue(String username) {
        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return issueForUser(user);
    }

    // Validates the raw token from the cookie, then ROTATES it: the presented token is
    // revoked and a fresh one is issued and returned (raw). Throws 401 if the token is
    // missing, unknown, expired, or already revoked.
    //
    // Reuse detection: if a token that was ALREADY revoked is presented, that's a red flag
    // (a rotated-away token shouldn't be used again — it may have been stolen), so we revoke
    // every one of that user's tokens to force a fresh login everywhere.
    //
    // noRollbackFor = ResponseStatusException is LOAD-BEARING: the reuse branch below revokes the
    // whole token family and THEN throws a 401. @Transactional rolls back on RuntimeException by
    // default, which would undo the very revocation we just made — silently defeating reuse
    // detection (verified: without this, a replayed token leaves the live session active). Every
    // throw here is either after the write we want to keep (reuse) or before any write (missing /
    // invalid / expired), so opting these exceptions out of rollback is safe.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh token");
        }

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        Instant now = Instant.now();

        // Already revoked but presented again → likely a replay of a stolen/rotated token.
        // Defensively kill all of this user's sessions.
        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllForUser(stored.getUser());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token already used");
        }

        if (!stored.isActive(now)) { // expired (not-revoked case handled above)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        // Rotate: retire the token we just used, issue a fresh one for the same user.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        UserAccount user = stored.getUser();
        String newRawToken = issueForUser(user);
        return new Rotation(user.getUsername(), newRawToken);
    }

    // Revokes the token in the cookie (used on logout). Idempotent: if the token is unknown
    // or already gone we simply do nothing, so a double logout can't error.
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    // The result of a rotation: who the token belongs to (so the caller can mint a matching
    // access token) plus the new raw refresh token to set in the cookie.
    public record Rotation(String username, String newRawToken) {}

    // ── internals ────────────────────────────────────────────────────────────

    // Creates and persists a token row for a user and returns the raw value.
    private String issueForUser(UserAccount user) {
        String rawToken = generateRawToken();
        RefreshToken token = RefreshToken.builder()
                .tokenHash(hash(rawToken))
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();
        refreshTokenRepository.save(token);
        return rawToken;
    }

    // 32 random bytes (256 bits) encoded URL-safe so it's cookie-friendly. This is the value
    // the browser holds; it's never stored server-side in this form.
    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 of the raw token, as lowercase hex. This is what we store and compare against,
    // so the raw credential never touches the database (same idea as hashing passwords).
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to exist on every JVM, so this can't actually happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
