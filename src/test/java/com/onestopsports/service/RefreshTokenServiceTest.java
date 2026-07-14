package com.onestopsports.service;

import com.onestopsports.model.RefreshToken;
import com.onestopsports.model.UserAccount;
import com.onestopsports.repository.RefreshTokenRepository;
import com.onestopsports.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Unit tests for RefreshTokenService — the issue / rotate / revoke logic that backs the
// httpOnly refresh cookie. Uses Mockito fakes for the repositories so no database is needed.
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private RefreshTokenService service;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        // @Value fields aren't populated by Mockito, so set the refresh lifetime by hand.
        // 30 days in ms — the same default the service declares.
        ReflectionTestUtils.setField(service, "refreshExpirationMs", 2_592_000_000L);
        user = UserAccount.builder().id(1L).username("james").build();
    }

    // Mirrors the service's private hashing so a test can predict the stored hash for a raw
    // token and stub findByTokenHash accordingly.
    private static String sha256Hex(String raw) throws Exception {
        byte[] hashed = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashed);
    }

    // ── issue() ────────────────────────────────────────────────────────────────

    @Test
    void issue_savesTokenAndReturnsRawValue() {
        when(userRepository.findByUsername("james")).thenReturn(Optional.of(user));

        String raw = service.issue("james");

        // The returned raw token is a real value, not empty.
        assertThat(raw).isNotBlank();

        // A token row was saved with a hash (NOT the raw value), the right user, a future
        // expiry, and revoked = false.
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(raw); // SHA-256 hex, not the raw token
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void issue_unknownUser_throws401() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue("ghost"))
                .isInstanceOf(ResponseStatusException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    // ── rotate() ───────────────────────────────────────────────────────────────

    @Test
    void rotate_validToken_revokesOldAndIssuesNew() throws Exception {
        String raw = "current-raw-token";
        RefreshToken stored = RefreshToken.builder()
                .tokenHash(sha256Hex(raw))
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600)) // active: future expiry
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(stored));

        RefreshTokenService.Rotation rotation = service.rotate(raw);

        // Reports the owning user and hands back a DIFFERENT raw token (rotation).
        assertThat(rotation.username()).isEqualTo("james");
        assertThat(rotation.newRawToken()).isNotBlank().isNotEqualTo(raw);

        // The presented token is now revoked, and a new token row was saved.
        assertThat(stored.isRevoked()).isTrue();
        // save() is called twice: once to persist the revoked old token, once for the new one.
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(any(RefreshToken.class));
    }

    @Test
    void rotate_missingToken_throws401() {
        assertThatThrownBy(() -> service.rotate(null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.rotate("  "))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rotate_unknownToken_throws401() throws Exception {
        when(refreshTokenRepository.findByTokenHash(sha256Hex("nope"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rotate_alreadyRevokedToken_revokesAllUserTokensAndThrows() throws Exception {
        String raw = "reused-token";
        RefreshToken stored = RefreshToken.builder()
                .tokenHash(sha256Hex(raw))
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true) // already used/rotated — presenting it again is suspicious
                .build();
        when(refreshTokenRepository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.rotate(raw))
                .isInstanceOf(ResponseStatusException.class);

        // Reuse detection: all of the user's tokens get revoked as a precaution.
        verify(refreshTokenRepository).revokeAllForUser(user);
    }

    @Test
    void rotate_expiredToken_throws401() throws Exception {
        String raw = "expired-token";
        RefreshToken stored = RefreshToken.builder()
                .tokenHash(sha256Hex(raw))
                .user(user)
                .expiresAt(Instant.now().minusSeconds(1)) // already past expiry
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.rotate(raw))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── revoke() ─────────────────────────────────────────────────────────────

    @Test
    void revoke_existingToken_marksRevoked() throws Exception {
        String raw = "live-token";
        RefreshToken stored = RefreshToken.builder()
                .tokenHash(sha256Hex(raw))
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(stored));

        service.revoke(raw);

        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void revoke_nullToken_isNoOp() {
        service.revoke(null);
        // Nothing to revoke → the repository is never touched.
        verifyNoInteractions(refreshTokenRepository);
    }
}
