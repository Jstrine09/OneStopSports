package com.onestopsports.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// This filter runs on every incoming HTTP request (exactly once per request).
// Its job is to check if the request has a valid JWT token in the Authorization header.
// If it does, we tell Spring Security who the user is so protected endpoints work correctly.
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService; // Used to load user data from the database

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Look for the "Authorization" header — it should look like: "Bearer eyJhbGc..."
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            // Strip "Bearer " prefix (7 characters) to get the raw token string
            String token = header.substring(7);

            if (jwtUtil.validateToken(token)) {
                // Token is valid — extract the username stored inside it
                String username = jwtUtil.extractUsername(token);

                // Load the full user details from the database using the username
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Create an authentication object that tells Spring Security "this user is logged in"
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()); // null = no credentials needed (token already verified)

                // Attach extra request details (IP address, session info, etc.)
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Store the authenticated user in Spring's security context for this request.
                // After this, any controller can call principal.getName() to get the username.
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // Always pass the request along to the next filter in the chain
        // (even if no token was present — public endpoints don't need one)
        filterChain.doFilter(request, response);
    }
}
