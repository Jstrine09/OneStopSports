package com.matchday.dto;

import java.time.LocalDateTime;

public record MatchDto(
        Long id,
        TeamDto homeTeam,
        TeamDto awayTeam,
        Integer homeScore,
        Integer awayScore,
        String status,
        LocalDateTime startTime,
        Long leagueId) {}
