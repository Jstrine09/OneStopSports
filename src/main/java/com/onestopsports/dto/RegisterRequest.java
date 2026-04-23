package com.onestopsports.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The request body sent when a new user creates an account.
// Spring validates these constraints automatically before the method is even called.
public record RegisterRequest(
        @NotBlank String username,                  // Cannot be empty
        @NotBlank @Email String email,              // Must be a valid email format (e.g. user@example.com)
        @NotBlank @Size(min = 8) String password    // Must be at least 8 characters long
) {}
