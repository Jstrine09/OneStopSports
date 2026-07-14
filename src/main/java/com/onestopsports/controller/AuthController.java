package com.onestopsports.controller;

import com.onestopsports.dto.AuthRequest;
import com.onestopsports.dto.AuthResponse;
import com.onestopsports.dto.RegisterRequest;
import com.onestopsports.security.AuthRateLimiter;
import com.onestopsports.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Handles user registration and login.
// Both endpoints are public — no JWT token required.
//
// Because they're public they're the natural target for brute-force and
// credential-stuffing attacks, so every request is first run through
// AuthRateLimiter (per client IP, plus per username on login). Going over the
// limit throws RateLimitExceededException, which GlobalExceptionHandler turns
// into an HTTP 429 with a Retry-After header.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;

    public AuthController(AuthService authService, AuthRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    // POST /api/auth/register
    // Creates a new user account. Returns a JWT token so the user is immediately logged in.
    // @Valid triggers the validation rules on RegisterRequest (e.g. email format, min password length).
    // Returns HTTP 201 Created (not 200 OK) because a new resource was created.
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        // Throttle registrations per IP so a script can't mass-create accounts.
        rateLimiter.checkRateLimit("register:ip:" + clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    // POST /api/auth/login
    // Verifies the username + password and returns a JWT token on success.
    // Returns HTTP 200 OK with the token, or 401 Unauthorized if credentials are wrong.
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request,
                                              HttpServletRequest httpRequest) {
        // Throttle by BOTH the caller's IP and the targeted username:
        //   • per-IP  — stops one machine hammering many different accounts.
        //   • per-user — stops a distributed attack (many IPs) guessing one account's password.
        rateLimiter.checkRateLimit("login:ip:" + clientIp(httpRequest));
        rateLimiter.checkRateLimit("login:user:" + request.username());
        return ResponseEntity.ok(authService.login(request));
    }

    // Works out the caller's real IP address.
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
