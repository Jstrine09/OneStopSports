package com.matchday.dto;

import java.time.LocalDateTime;

public record UserDto(Long id, String username, String email, String avatarUrl, LocalDateTime createdAt) {}
