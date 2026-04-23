package com.onestopsports.dto;

import java.time.LocalDateTime;

// The data the frontend receives for a single match.
// Includes both teams, the current score, and status so the frontend
// can decide how to display it (live, finished, scheduled, etc.)
public record MatchDto(
        Long id,              // football-data.org's match ID (used to fetch events)
        TeamDto homeTeam,     // Full team object for the home side
        TeamDto awayTeam,     // Full team object for the away side
        Integer homeScore,    // null if the match hasn't started yet
        Integer awayScore,    // null if the match hasn't started yet
        String status,        // e.g. "LIVE", "FINISHED", "SCHEDULED", "PAUSED" (halftime)
        LocalDateTime startTime, // Kick-off time in local time (converted from UTC)
        Long leagueId         // Our internal DB league ID (used to navigate to the right league)
) {}
