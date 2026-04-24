package com.onestopsports.service;

import com.onestopsports.dto.AuthRequest;
import com.onestopsports.dto.AuthResponse;
import com.onestopsports.dto.RegisterRequest;
import com.onestopsports.model.UserAccount;
import com.onestopsports.repository.UserRepository;
import com.onestopsports.security.JwtUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Handles user registration and login.
// Also implements UserDetailsService so Spring Security knows how to load a user
// from the database when checking a JWT token.
@Service
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // For hashing passwords before saving
    private final JwtUtil jwtUtil;                  // For generating JWT tokens after login/register
    private final AuthenticationManager authenticationManager; // Spring's built-in login verifier

    // @Lazy on AuthenticationManager breaks a circular dependency:
    // SecurityConfig needs AuthService (to set it as UserDetailsService),
    // but AuthService would need AuthenticationManager which lives in SecurityConfig.
    // @Lazy tells Spring to create the AuthenticationManager only when it's first actually used
    // (i.e. on the first login request), breaking the startup cycle.
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       @Lazy AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
    }

    // Required by UserDetailsService — Spring calls this during JWT token validation
    // to load the user's details (username, hashed password, roles) from the database.
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Build Spring's UserDetails object — Spring uses this internally for auth checks.
        // We give every user the "USER" role (no admin functionality needed yet).
        return User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash()) // Spring compares this against what the user typed
                .roles("USER")
                .build();
    }

    // Creates a new user account and returns a JWT token so they're logged in immediately.
    // Checks for duplicate username and email before saving.
    public AuthResponse register(RegisterRequest request) {
        // Make sure the username isn't already taken
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        // Make sure the email isn't already registered
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        // Build and save the new user — password is hashed before saving (NEVER stored as plain text)
        UserAccount user = UserAccount.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password())) // Hash the password
                .build();
        userRepository.save(user);

        // Generate a JWT token and return it — user is immediately logged in after registering
        return new AuthResponse(jwtUtil.generateToken(user.getUsername()), user.getUsername());
    }

    // Verifies the username and password, then returns a JWT token.
    // Spring's AuthenticationManager handles the actual password check — it loads the user,
    // hashes the provided password, and compares it to the stored hash.
    public AuthResponse login(AuthRequest request) {
        // This will throw an exception automatically if the credentials are wrong
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        // Credentials were valid — generate and return a token
        return new AuthResponse(jwtUtil.generateToken(request.username()), request.username());
    }
}
