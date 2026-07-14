package com.onestopsports.controller;

import com.onestopsports.dto.AuthRequest;
import com.onestopsports.dto.AuthResponse;
import com.onestopsports.dto.RegisterRequest;
import com.onestopsports.security.AuthRateLimiter;
import com.onestopsports.security.JwtUtil;
import com.onestopsports.service.AuthService;
import com.onestopsports.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

// Handles registration, login, refresh and logout.
//
// Rate limiting (QA finding S1): register and login are public, so they're the natural
// target for brute-force and credential-stuffing. Every request is first run through
// AuthRateLimiter (per client IP, plus per username on login). Going over the limit throws
// RateLimitExceededException, which GlobalExceptionHandler turns into an HTTP 429 with a
// Retry-After header.
//
// Token model (QA finding S3 hardening):
//   • ACCESS token — a short-lived JWT returned in the JSON body. The frontend keeps it
//     in memory only and sends it as an Authorization: Bearer header. Because it's never
//     written to localStorage, an XSS payload can't steal it from storage.
//   • REFRESH token — a long-lived random token we set in an httpOnly cookie (JavaScript
//     can't read httpOnly cookies). /refresh trades a valid cookie for a new access token
//     (and rotates the cookie); /logout revokes it. The refresh token is what survives a
//     page reload, so the durable credential is the one kept out of JavaScript's reach.
//
// CSRF: the state-changing API calls are authorised by the Bearer header (which a malicious
// cross-site page can't read or set), so they're not CSRF-able. The only cookie-authorised
// endpoints are /refresh and /logout; the cookie is SameSite (see the cookie builder below),
// so a cross-site page can't get the browser to send it. That's why CSRF stays disabled.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;

    // ── Refresh-cookie settings (overridable per environment) ──────────────────
    // The cookie's name. Kept configurable so it can be changed without code edits.
    @Value("${app.auth.cookie.name:refreshToken}")
    private String cookieName;

    // Secure = only send the cookie over HTTPS. Defaults to false so it works on
    // http://localhost in dev; set app.auth.cookie.secure=true in production (see prod yml).
    @Value("${app.auth.cookie.secure:false}")
    private boolean cookieSecure;

    // SameSite policy. "Strict" means the browser only sends the cookie for requests that
    // originate from our own site — which is all we need (our SPA calls /refresh + /logout
    // same-origin), and it blocks cross-site requests, giving us CSRF protection for free.
    @Value("${app.auth.cookie.same-site:Strict}")
    private String cookieSameSite;

    // How long the refresh cookie lives, in milliseconds. Matches the refresh token's own
    // expiry so the cookie and the server-side record disappear together. Default: 30 days.
    @Value("${app.auth.refresh.expiration-ms:2592000000}")
    private long refreshExpirationMs;

    // The cookie is scoped to /api/auth so the browser only attaches it to the auth
    // endpoints (refresh/logout) — it's never sent on ordinary API calls, limiting exposure.
    private static final String COOKIE_PATH = "/api/auth";

    // Four collaborators: the auth service (register/login), the rate limiter (S1 throttling),
    // the refresh-token service (issue/rotate/revoke, S3), and JwtUtil (mint access tokens).
    public AuthController(AuthService authService,
                          AuthRateLimiter rateLimiter,
                          RefreshTokenService refreshTokenService,
                          JwtUtil jwtUtil) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.refreshTokenService = refreshTokenService;
        this.jwtUtil = jwtUtil;
    }

    // POST /api/auth/register
    // Creates a new user, returns an access token in the body, and sets the refresh cookie.
    // 201 Created because a new resource (the user) was created.
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        // Throttle registrations per IP so a script can't mass-create accounts (S1).
        rateLimiter.checkRateLimit("register:ip:" + clientIp(httpRequest));

        // Create the user + access token, then issue a refresh token and set it as an httpOnly cookie (S3).
        AuthResponse auth = authService.register(request);
        String refreshToken = refreshTokenService.issue(auth.username());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken).toString())
                .body(auth);
    }

    // POST /api/auth/login
    // Verifies credentials, returns an access token in the body, sets the refresh cookie.
    // 200 OK, or 401 (via GlobalExceptionHandler) if the credentials are wrong.
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request,
                                              HttpServletRequest httpRequest) {
        // Throttle by BOTH the caller's IP and the targeted username (S1):
        //   • per-IP  — stops one machine hammering many different accounts.
        //   • per-user — stops a distributed attack (many IPs) guessing one account's password.
        rateLimiter.checkRateLimit("login:ip:" + clientIp(httpRequest));
        rateLimiter.checkRateLimit("login:user:" + request.username());

        // Verify credentials + mint the access token, then issue + set the refresh cookie (S3).
        AuthResponse auth = authService.login(request);
        String refreshToken = refreshTokenService.issue(auth.username());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken).toString())
                .body(auth);
    }

    // POST /api/auth/refresh
    // Reads the refresh cookie, rotates it, and returns a fresh access token. This is how the
    // frontend recovers after its in-memory access token expires (and how it restores a
    // session after a page reload). Returns 401 if the cookie is missing or no longer valid.
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "${app.auth.cookie.name:refreshToken}", required = false) String refreshCookie) {

        // Validate + rotate the presented token (throws 401 if invalid/expired/revoked).
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshCookie);

        // Mint a new short-lived access token for the same user.
        String accessToken = jwtUtil.generateToken(rotation.username());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(rotation.newRawToken()).toString())
                .body(new AuthResponse(accessToken, rotation.username()));
    }

    // POST /api/auth/logout
    // Revokes the refresh token server-side and clears the cookie in the browser, so a
    // stolen-but-logged-out token can no longer be refreshed. Always returns 204 (idempotent —
    // logging out when already logged out is fine).
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${app.auth.cookie.name:refreshToken}", required = false) String refreshCookie) {

        refreshTokenService.revoke(refreshCookie); // no-op if the cookie is absent/unknown
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, buildClearingCookie().toString())
                .build();
    }

    // ── cookie builders ────────────────────────────────────────────────────────

    // Builds the Set-Cookie for a freshly issued refresh token. httpOnly keeps it out of
    // JavaScript's reach (XSS can't read it); Secure/SameSite/Path are as configured above.
    private ResponseCookie buildRefreshCookie(String rawToken) {
        return ResponseCookie.from(cookieName, rawToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(COOKIE_PATH)
                .maxAge(Duration.ofMillis(refreshExpirationMs))
                .build();
    }

    // Builds a Set-Cookie that immediately expires the refresh cookie (maxAge 0 = delete now).
    // Same name/path/attributes so the browser matches and removes the existing cookie.
    private ResponseCookie buildClearingCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    // Works out the caller's real IP address (used for rate-limit keys — S1).
    //
    // In production the app sits behind proxies (Vercel → Render), so the direct
    // socket address (getRemoteAddr) is the proxy, not the user. The real client IP
    // is in the "X-Forwarded-For" header, which is a comma-separated chain where the
    // FIRST entry is the original client. We fall back to getRemoteAddr for local dev
    // (no proxy) where the header is absent.
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Take the first IP in the "client, proxy1, proxy2" chain and trim whitespace.
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
