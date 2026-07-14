package com.onestopsports.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onestopsports.config.SecurityConfig;
import com.onestopsports.dto.AuthRequest;
import com.onestopsports.dto.AuthResponse;
import com.onestopsports.dto.RegisterRequest;
import com.onestopsports.security.AuthRateLimiter;
import com.onestopsports.security.JwtUtil;
import com.onestopsports.security.RateLimitExceededException;
import com.onestopsports.service.AuthService;
import com.onestopsports.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;           // Substring matcher for header assertions
import static org.mockito.ArgumentMatchers.any;               // Matches any argument of the right type
import static org.mockito.ArgumentMatchers.anyString;         // Matches any String argument
import static org.mockito.ArgumentMatchers.nullable;          // Matches any value INCLUDING null
import static org.mockito.Mockito.doThrow;                    // Stub a void method to throw
import static org.mockito.Mockito.verify;                     // Assert a mock method was called
import static org.mockito.Mockito.when;                       // Stub a mock method's return value
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf; // Adds a CSRF token to POST requests
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // Build a POST request
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;      // status(), jsonPath(), etc.

// @WebMvcTest loads ONLY the web layer — controllers, filters, and Spring Security.
// It does NOT start the full application context (no database, no services, no DataLoader).
// This makes the test very fast and focused purely on HTTP request/response behaviour.
//
// excludeAutoConfiguration removes Spring Boot's auto-configured InMemoryUserDetailsManager.
// Without this, Spring finds two UserDetailsService beans (the auto one + our mocked AuthService)
// and crashes with "expected single matching bean but found 2".
//
// @Import(SecurityConfig.class) is required because @WebMvcTest only scans web-tier beans
// (@Controller, Filter, etc.) — @Configuration classes like SecurityConfig are NOT picked up
// automatically. Without this import, Spring Security falls back to its default "deny all" rule
// and every request returns 401, even public endpoints like /api/auth/register.
@WebMvcTest(
        value = AuthController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class
)
@Import(SecurityConfig.class)
class AuthControllerTest {

    // MockMvc lets us fire fake HTTP requests at the controller without starting a real server.
    // It's injected automatically by @WebMvcTest.
    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper converts Java objects to JSON strings for the request body.
    @Autowired
    private ObjectMapper objectMapper;

    // @MockBean replaces the real AuthService with a Mockito mock in the Spring context.
    // We control what it returns so tests don't depend on a real database.
    @MockBean
    private AuthService authService;

    // JwtAuthFilter (loaded by Spring Security in @WebMvcTest) needs JwtUtil to validate tokens.
    // We mock it here so the filter doesn't crash on startup — it just won't do anything with tokens.
    @MockBean
    private JwtUtil jwtUtil;

    // The controller now runs every auth request through AuthRateLimiter. We mock it so
    // by default it does nothing (all requests allowed) — that keeps the existing tests
    // focused on their own behaviour. The 429 test below stubs it to throw on demand.
    // (The limiter's real counting/window logic is covered in AuthRateLimiterTest.)
    @MockBean
    private AuthRateLimiter rateLimiter;

    // AuthController now issues/rotates/revokes refresh tokens through this service. Mock it so
    // the HTTP-layer tests don't touch the database.
    @MockBean
    private RefreshTokenService refreshTokenService;

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Test
    void register_validRequest_returns201AndToken() throws Exception {
        // GIVEN: a well-formed registration body with all required fields
        RegisterRequest request = new RegisterRequest("james", "james@test.com", "password123");

        // The mock service returns a fake token — we're only testing the HTTP layer here
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthResponse("fake.jwt.token", "james"));
        // After register the controller issues a refresh token for the new user.
        when(refreshTokenService.issue("james")).thenReturn("raw-refresh-token");

        // WHEN + THEN: POST the JSON body and assert the response
        mockMvc.perform(post("/api/auth/register").with(csrf()) // csrf() adds the token MockMvc needs for POST requests
                        .contentType(MediaType.APPLICATION_JSON)                // tell Spring the body is JSON
                        .content(objectMapper.writeValueAsString(request)))     // serialize the request object
                .andExpect(status().isCreated())                                // must be 201, not 200
                .andExpect(jsonPath("$.token").value("fake.jwt.token"))         // token field is present
                .andExpect(jsonPath("$.username").value("james"))               // username echoed back
                // The refresh token is set as an httpOnly cookie, never in the body.
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=raw-refresh-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));
    }

    @Test
    void register_blankUsername_returns400() throws Exception {
        // GIVEN: username is blank — violates @NotBlank on RegisterRequest
        // The @Valid annotation on the controller method triggers validation before the service is called
        mockMvc.perform(post("/api/auth/register").with(csrf()) // csrf() adds the token MockMvc needs for POST requests
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"email\":\"james@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest()); // GlobalExceptionHandler returns 400 for validation failures
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        // GIVEN: email doesn't match the @Email format constraint — "notanemail" has no @ sign
        mockMvc.perform(post("/api/auth/register").with(csrf()) // csrf() adds the token MockMvc needs for POST requests
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"james\",\"email\":\"notanemail\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest()); // @Email validation fails → 400
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        // GIVEN: password is only 3 chars — violates @Size(min = 8) on RegisterRequest
        mockMvc.perform(post("/api/auth/register").with(csrf()) // csrf() adds the token MockMvc needs for POST requests
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"james\",\"email\":\"james@test.com\",\"password\":\"abc\"}"))
                .andExpect(status().isBadRequest()); // @Size(min=8) fails → 400
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200AndToken() throws Exception {
        // GIVEN: mock service returns a token for valid credentials
        when(authService.login(any(AuthRequest.class)))
                .thenReturn(new AuthResponse("fake.jwt.token", "james"));
        when(refreshTokenService.issue("james")).thenReturn("raw-refresh-token");

        mockMvc.perform(post("/api/auth/login").with(csrf()) // csrf() adds the token MockMvc needs for POST requests
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"james\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())                                     // 200 OK
                .andExpect(jsonPath("$.token").value("fake.jwt.token"))         // token is in the response
                .andExpect(jsonPath("$.username").value("james"))              // username is in the response
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=raw-refresh-token")));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        // GIVEN: the mock service throws BadCredentialsException (what Spring Security throws for wrong passwords)
        // GlobalExceptionHandler catches this and maps it to a 401 response
        when(authService.login(any(AuthRequest.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login").with(csrf()) // csrf() adds the token MockMvc needs for POST requests
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"james\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())                           // GlobalExceptionHandler → 401
                .andExpect(jsonPath("$.message").value("Invalid username or password")); // our error message
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        // GIVEN: password field is blank — violates @NotBlank on AuthRequest
        mockMvc.perform(post("/api/auth/login").with(csrf()) // csrf() adds the token MockMvc needs for POST requests
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"james\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest()); // @NotBlank validation fires before the service is called
    }

    // ── 429 Too Many Requests — rate limiting ─────────────────────────────────

    @Test
    void login_overRateLimit_returns429WithRetryAfter() throws Exception {
        // GIVEN: the rate limiter rejects this request (simulating too many prior attempts).
        // checkRateLimit is a void method, so we use doThrow(...).when(mock) to stub it.
        doThrow(new RateLimitExceededException(30))
                .when(rateLimiter).checkRateLimit(anyString());

        // WHEN + THEN: the request is blocked with 429, our error envelope, and a Retry-After header
        mockMvc.perform(post("/api/auth/login").with(csrf()) // csrf() adds the token MockMvc needs for POST requests
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"james\",\"password\":\"password123\"}"))
                .andExpect(status().isTooManyRequests())                                 // GlobalExceptionHandler → 429
                .andExpect(header().string("Retry-After", "30"))                         // tells the client how long to wait
                .andExpect(jsonPath("$.status").value(429))                              // our standard error envelope
                .andExpect(jsonPath("$.message").value("Too many attempts. Please try again later."));
    }

    // ── Security headers ──────────────────────────────────────────────────────

    @Test
    void response_carriesContentSecurityPolicyHeader() throws Exception {
        // The CSP header (configured in SecurityConfig's headers DSL) must be present on
        // responses so the browser enforces it. We check it on the login response, but the
        // header writer applies it to every response the app serves.
        when(authService.login(any(AuthRequest.class)))
                .thenReturn(new AuthResponse("fake.jwt.token", "james"));
        when(refreshTokenService.issue("james")).thenReturn("raw-refresh-token");

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"james\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                // Spring Security's default hardening headers should still be present too.
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    // ── POST /api/auth/refresh ────────────────────────────────────────────────

    @Test
    void refresh_validCookie_returns200AndNewAccessToken() throws Exception {
        // GIVEN: the refresh cookie is valid — the service rotates it and reports the user.
        when(refreshTokenService.rotate("valid-refresh"))
                .thenReturn(new RefreshTokenService.Rotation("james", "rotated-refresh"));
        // A fresh access token is minted for that user.
        when(jwtUtil.generateToken("james")).thenReturn("new.access.token");

        mockMvc.perform(post("/api/auth/refresh").with(csrf())
                        .cookie(new Cookie("refreshToken", "valid-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new.access.token"))       // new access token in the body
                .andExpect(jsonPath("$.username").value("james"))
                // The rotated refresh token is set as a fresh cookie.
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=rotated-refresh")));
    }

    @Test
    void refresh_invalidCookie_returns401() throws Exception {
        // GIVEN: the service rejects the token (unknown/expired/revoked) with a 401.
        when(refreshTokenService.rotate("bad-refresh"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        mockMvc.perform(post("/api/auth/refresh").with(csrf())
                        .cookie(new Cookie("refreshToken", "bad-refresh")))
                .andExpect(status().isUnauthorized()); // GlobalExceptionHandler passes the 401 through
    }

    @Test
    void refresh_noCookie_returns401() throws Exception {
        // GIVEN: no cookie at all → the controller passes null to rotate(), which rejects it.
        when(refreshTokenService.rotate(nullable(String.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh token"));

        mockMvc.perform(post("/api/auth/refresh").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    @Test
    void logout_returns204AndClearsCookie() throws Exception {
        // Logout revokes the token server-side and clears the cookie (Max-Age=0).
        mockMvc.perform(post("/api/auth/logout").with(csrf())
                        .cookie(new Cookie("refreshToken", "some-refresh")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        // The presented token must actually be revoked on the server.
        verify(refreshTokenService).revoke("some-refresh");
    }

    @Test
    void logout_noCookie_stillReturns204() throws Exception {
        // Logging out when not logged in is a harmless no-op (idempotent).
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());

        verify(refreshTokenService).revoke(nullable(String.class));
    }
}
