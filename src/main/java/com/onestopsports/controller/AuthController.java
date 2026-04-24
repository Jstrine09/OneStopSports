package com.onestopsports.controller;

import com.onestopsports.dto.AuthRequest;
import com.onestopsports.dto.AuthResponse;
import com.onestopsports.dto.RegisterRequest;
import com.onestopsports.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Handles user registration and login.
// Both endpoints are public — no JWT token required.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // POST /api/auth/register
    // Creates a new user account. Returns a JWT token so the user is immediately logged in.
    // @Valid triggers the validation rules on RegisterRequest (e.g. email format, min password length).
    // Returns HTTP 201 Created (not 200 OK) because a new resource was created.
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    // POST /api/auth/login
    // Verifies the username + password and returns a JWT token on success.
    // Returns HTTP 200 OK with the token, or 401 Unauthorized if credentials are wrong.
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
