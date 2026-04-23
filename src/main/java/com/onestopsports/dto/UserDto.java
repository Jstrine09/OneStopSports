package com.onestopsports.dto;

import java.time.LocalDateTime;

// The data sent back when a logged-in user fetches their own profile (GET /api/users/me).
// Intentionally excludes sensitive fields like passwordHash.
public record UserDto(
        Long id,
        String username,
        String email,
        String avatarUrl,        // Profile picture URL (not currently populated)
        LocalDateTime createdAt  // When the account was created
) {}
