package com.onestopsports.dto;

import jakarta.validation.constraints.NotBlank;

// The request body sent when a user tries to log in.
// @NotBlank means Spring will reject the request with a 400 error if either field is empty.
public record AuthRequest(
        @NotBlank String username, // The user's username (cannot be empty or whitespace)
        @NotBlank String password  // The user's plain-text password (we hash it before comparing)
) {}
