package com.onestopsports.dto;

public record StandingsEntryDto(
        Integer position,
        TeamDto team,
        Integer played,
        Integer won,
        Integer drawn,
        Integer lost,
        Integer goalsFor,
        Integer goalsAgainst,
        Integer points) {}
