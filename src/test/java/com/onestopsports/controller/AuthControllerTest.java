package com.onestopsports.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onestopsports.config.SecurityConfig;
import com.onestopsports.dto.AuthRequest;
import com.onestopsports.dto.AuthResponse;
import com.onestopsports.dto.RegisterRequest;
import com.onestopsports.security.JwtUtil;
import com.onestopsports.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;               // Matches any argument of the right type
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

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Test
    void register_validRequest_returns201AndToken() throws Exception {
        // GIVEN: a well-formed registration body with all required fields
        RegisterRequest request = new RegisterRequest("james", "james@test.com", "password123");

        // The mock service returns a fake token — we're only testing the HTTP layer here
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthResponse("fake.jwt.token", "james"));

        // WHEN + THEN: POST the JSON body and assert the response
        mockMvc.perform(post("/api/auth/register").with(csrf()) // csrf() adds the token MockMvc needs for POST requests
                        .contentType(MediaType.APPLICATION_JSON)                // tell Spring the body is JSON
                        .content(objectMapper.writeValueAsString(request)))     // serialize the request object
                .andExpect(status().isCreated())                                // must be 201, not 200
                .andExpect(jsonPath("$.token").value("fake.jwt.token"))         // token field is present
                .andExpect(jsonPath("$.username").value("james"));              // username echoed back
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

        mockMvc.perform(post("/api/auth/login").with(csrf()) // csrf() adds the token MockMvc needs for POST requests
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"james\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())                                     // 200 OK
                .andExpect(jsonPath("$.token").value("fake.jwt.token"))         // token is in the response
                .andExpect(jsonPath("$.username").value("james"));              // username is in the response
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
}
