package com.onestopsports.config;

import com.onestopsports.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// This class controls who is allowed to access which endpoints.
// It also plugs in our custom JWT filter so every request is checked for a valid token.
@Configuration
@EnableWebSecurity // Activates Spring Security for this application
public class SecurityConfig {

    // Our custom filter that checks the JWT token on every incoming request
    private final JwtAuthFilter jwtAuthFilter;

    // Which website origins are allowed to call this API from a browser. Read from the
    // `app.cors.allowed-origin-patterns` property (a comma-separated list) so we can add a
    // new domain — e.g. a custom domain later — without editing any Java. These are Spring
    // "origin patterns", so "*" wildcards are allowed (handy for Vercel's per-deploy preview
    // URLs). The ":*" after the colon is a fallback used only if the property is missing
    // (e.g. in some tests), where allowing any origin is harmless.
    @Value("${app.cors.allowed-origin-patterns:*}")
    private List<String> allowedOriginPatterns;

    // The Content-Security-Policy sent on every response. CSP tells the browser which sources
    // it may load scripts/styles/images/etc. from, and — crucially for XSS defence — it blocks
    // inline and injected <script> from executing. The default below suits the single-origin
    // deploy (the backend serves the SPA, so the API, the app and its same-origin WebSocket are
    // all 'self'). It's a property so an environment can override it via the
    // APP_SECURITY_CONTENT_SECURITY_POLICY env var — e.g. to widen connect-src if the WebSocket
    // ever moves to a different host. (On the Vercel split deploy the SPA is served by Vercel,
    // which sets its own CSP in frontend/vercel.json; this header still guards API responses and
    // the single-origin fallback.)
    //
    // Directive notes:
    //   • script-src 'self'      — only our own hashed bundles run; no inline scripts (the theme
    //                              init + the PWA service-worker registration are external files
    //                              on purpose, so we don't need 'unsafe-inline' here).
    //   • style-src 'unsafe-inline' — allowed because React/component libraries set inline style
    //                              attributes; styles can't execute code, so this is low-risk.
    //   • img-src https:         — crests/headshots come from several external CDNs (ESPN,
    //                              football-data, balldontlie); images are not an execution vector.
    //   • connect-src 'self'     — same-origin API + same-origin WebSocket in this deploy.
    //   • frame-ancestors 'none' + object-src 'none' — no embedding us in a frame, no plugins.
    @Value("${app.security.content-security-policy:"
            + "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: https:; "
            + "font-src 'self' data:; "
            + "connect-src 'self'; "
            + "frame-ancestors 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'; "
            + "object-src 'none'}")
    private String contentSecurityPolicy;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF protection — not needed for stateless REST APIs (no browser sessions)
                .csrf(csrf -> csrf.disable())

                // Apply our CORS config so the React frontend (on a different port) can make requests
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Send a Content-Security-Policy header on every response. This is the primary
                // in-browser defence against XSS: even if a script were injected, the policy
                // stops it running (script-src 'self') and stops it exfiltrating data to another
                // host (connect-src 'self'). Spring already adds the other standard hardening
                // headers by default (X-Content-Type-Options: nosniff, X-Frame-Options: DENY).
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy)))

                // Don't store sessions — every request must include a JWT token instead
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Define which endpoints are public and which require a logged-in user.
                //
                // ORDER MATTERS: Spring evaluates these matchers top-to-bottom and the FIRST
                // match wins. The authenticated rules must therefore come BEFORE the broad
                // `GET /api/**` permitAll — otherwise a `GET /api/users/me/...` would match
                // the public GET rule first and skip the auth check entirely (a real bypass
                // that earlier versions of this file had).
                .authorizeHttpRequests(auth -> auth
                        // Always-public endpoints first.
                        .requestMatchers("/api/auth/**").permitAll()              // Login and register are always public
                        .requestMatchers("/ws/**").permitAll()                   // WebSocket endpoint is public
                        // Swagger UI assets and the raw OpenAPI JSON spec must be public —
                        // without this, Spring Security returns 401 and the UI never loads.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                        // Protected endpoints — declared BEFORE the broad GET permitAll so the
                        // auth check can't be skipped. Covers every method (GET included).
                        .requestMatchers("/api/users/me/**").authenticated()     // Profile / favourites require login

                        // Public browsing — all other GET /api/** is open (read-only data).
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        // Serve the React single-page app (index.html, hashed assets, icons,
                        // and client-side routes like /leagues or /live) to everyone.
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().authenticated()                            // Everything else (POST/PUT/DELETE) requires login
                )

                // When an unauthenticated request hits a protected endpoint, return a clean
                // 401 with our standard JSON error envelope instead of an empty 403 (or, in
                // the old bypass case, a 500 from a null principal).
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authEx) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"status\":401,\"error\":\"Unauthorized\","
                            + "\"message\":\"Authentication required\","
                            + "\"timestamp\":\"" + java.time.LocalDateTime.now() + "\"}");
                }))

                // Add our JWT filter BEFORE Spring's default username/password filter.
                // This means every request is checked for a JWT before anything else runs.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        // Spring needs an AuthenticationManager to verify username + password during login.
        // We get it from AuthenticationConfiguration to avoid creating it manually.
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // CORS (Cross-Origin Resource Sharing) controls which website origins are allowed to
        // call our API from a browser. In the Vercel split deploy the REST calls are proxied
        // same-origin (so the browser doesn't even enforce CORS on them), but we still scope
        // the allowed origins down from the old "*" to the configured list — defence in depth
        // for any direct cross-origin call, and it keeps local dev working (localhost).
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);                     // Only our known origins (dev + Vercel)
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")); // Allowed HTTP methods
        configuration.setAllowedHeaders(List.of("*"));                                    // Allow any header (incl. Authorization)
        configuration.setAllowCredentials(true);                                           // Allow cookies and auth headers

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply this config to every URL
        return source;
    }
}
