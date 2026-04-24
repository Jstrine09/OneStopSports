package com.onestopsports.dto;

// What we send back after a successful login or registration.
// The frontend stores the token and includes it in the Authorization header on every future request.
public record AuthResponse(
        String token,    // The JWT token — the frontend uses this to prove who the user is
        String username  // Included so the frontend can display the username without decoding the token
) {}
