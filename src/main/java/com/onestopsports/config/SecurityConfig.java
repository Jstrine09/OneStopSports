package com.onestopsports.config;

import com.onestopsports.security.JwtAuthFilter;
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
        // CORS (Cross-Origin Resource Sharing) controls which domains can call our API.
        // The React frontend runs on localhost:5173 while the API is on localhost:8080 —
        // without CORS config, the browser would block those cross-origin requests.
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));                              // Any origin (fine for dev)
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")); // Allowed HTTP methods
        configuration.setAllowedHeaders(List.of("*"));                                    // Allow any header (incl. Authorization)
        configuration.setAllowCredentials(true);                                           // Allow cookies and auth headers

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply this config to every URL
        return source;
    }
}
