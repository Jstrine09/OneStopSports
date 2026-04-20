package com.matchday.dto;

import java.time.LocalDate;

public record PlayerDto(
        Long id,
        String name,
        String position,
        String nationality,
        LocalDate dateOfBirth,
        Integer jerseyNumber,
        String photoUrl,
        Long teamId) {}
