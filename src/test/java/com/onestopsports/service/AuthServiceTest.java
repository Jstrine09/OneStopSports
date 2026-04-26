package com.onestopsports.service;

import com.onestopsports.dto.AuthRequest;
import com.onestopsports.dto.AuthResponse;
import com.onestopsports.dto.RegisterRequest;
import com.onestopsports.model.UserAccount;
import com.onestopsports.repository.UserRepository;
import com.onestopsports.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;          // For readable "assertThat(x).isEqualTo(y)" checks
import static org.assertj.core.api.Assertions.assertThatThrownBy;  // For asserting that a method throws a specific exception
import static org.mockito.ArgumentMatchers.any;                     // Matches any argument of the right type
import static org.mockito.ArgumentMatchers.anyString;               // Matches any String argument
import static org.mockito.Mockito.*;                                // when(), verify(), never(), etc.

// @ExtendWith(MockitoExtension.class) tells JUnit to use Mockito for this test class.
// Mockito lets us create fake versions of dependencies (like UserRepository) so we can
// test AuthService in isolation — no real database or Spring context needed.
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // @Mock creates a fake version of each dependency.
    // We control what these fakes return, so tests are fast and predictable.
    @Mock private UserRepository        userRepository;    // Fake DB — no Postgres needed
    @Mock private PasswordEncoder       passwordEncoder;   // Fake hasher — returns whatever we tell it
    @Mock private JwtUtil               jwtUtil;           // Fake JWT generator
    @Mock private AuthenticationManager authenticationManager; // Fake Spring auth check

    // @InjectMocks creates a real AuthService and injects the mocks above into its constructor.
    // This is the class we're actually testing.
    @InjectMocks
    private AuthService authService;

    // A reusable RegisterRequest — used in multiple tests so we define it once here.
    private RegisterRequest registerRequest;

    @BeforeEach // Runs before every test to reset to a clean state
    void setUp() {
        // Build a typical registration payload — username, email, plain-text password
        registerRequest = new RegisterRequest("james", "james@test.com", "password123");
    }

    // ── register() ───────────────────────────────────────────────────────────

    @Test
    void register_success_returnsTokenAndUsername() {
        // GIVEN: no existing user with that username or email
        when(userRepository.findByUsername("james")).thenReturn(Optional.empty()); // username is free
        when(userRepository.findByEmail("james@test.com")).thenReturn(Optional.empty()); // email is free

        // The password encoder returns a fake hash — we don't care about the actual hash value here
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");

        // save() returns the user object that was passed to it (simulates what the DB would do)
        when(userRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        // The JWT util returns a predictable fake token
        when(jwtUtil.generateToken("james")).thenReturn("fake.jwt.token");

        // WHEN: we call register()
        AuthResponse response = authService.register(registerRequest);

        // THEN: the response contains the token and the username
        assertThat(response.token()).isEqualTo("fake.jwt.token"); // token was generated and returned
        assertThat(response.username()).isEqualTo("james");        // username is in the response

        // Verify the password was hashed before saving — we should NEVER save a plain-text password
        verify(passwordEncoder).encode("password123");

        // Verify the user was actually saved to the repository
        verify(userRepository).save(any(UserAccount.class));
    }

    @Test
    void register_duplicateUsername_throwsConflict() {
        // GIVEN: a user with that username already exists in the DB
        when(userRepository.findByUsername("james"))
                .thenReturn(Optional.of(new UserAccount())); // non-empty = username taken

        // WHEN + THEN: register() should throw a 409 Conflict ResponseStatusException
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ResponseStatusException.class)              // must be a ResponseStatusException
                .hasMessageContaining("Username already taken");          // must have this message

        // The user should NOT have been saved since we bailed out early
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        // GIVEN: username is free but the email is already registered
        when(userRepository.findByUsername("james")).thenReturn(Optional.empty()); // username ok
        when(userRepository.findByEmail("james@test.com"))
                .thenReturn(Optional.of(new UserAccount())); // email taken

        // WHEN + THEN: should throw 409 with the email conflict message
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Email already registered");

        // Again, save() must not have been called
        verify(userRepository, never()).save(any());
    }

    // ── login() ──────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsToken() {
        // GIVEN: AuthenticationManager accepts the credentials without throwing.
        // authenticate() returns an Authentication object — we return null here because
        // AuthService doesn't use the return value, it just needs the call not to throw.
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);

        // The JWT util generates a token for the username
        when(jwtUtil.generateToken("james")).thenReturn("fake.jwt.token");

        // WHEN: we call login()
        AuthResponse response = authService.login(new AuthRequest("james", "password123"));

        // THEN: the response contains the token and username
        assertThat(response.token()).isEqualTo("fake.jwt.token");
        assertThat(response.username()).isEqualTo("james");

        // Verify the AuthenticationManager was actually called — it's what checks the password
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        // GIVEN: AuthenticationManager rejects the credentials (wrong password)
        // In real Spring Security this throws BadCredentialsException
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        // WHEN + THEN: login() should propagate the BadCredentialsException
        // (GlobalExceptionHandler catches this and returns 401 to the client)
        assertThatThrownBy(() -> authService.login(new AuthRequest("james", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        // No token should have been generated since auth failed
        verify(jwtUtil, never()).generateToken(anyString());
    }

    // ── loadUserByUsername() ─────────────────────────────────────────────────

    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        // GIVEN: a saved user exists in the DB
        UserAccount user = UserAccount.builder()
                .username("james")
                .passwordHash("hashed_password") // stored hash — Spring will compare against this
                .build();
        when(userRepository.findByUsername("james")).thenReturn(Optional.of(user));

        // WHEN: Spring Security calls loadUserByUsername() during JWT validation
        var userDetails = authService.loadUserByUsername("james");

        // THEN: the returned UserDetails has the correct username and password hash
        assertThat(userDetails.getUsername()).isEqualTo("james");
        assertThat(userDetails.getPassword()).isEqualTo("hashed_password");
    }
}
