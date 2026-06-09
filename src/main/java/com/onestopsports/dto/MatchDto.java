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
        LocalDateTime startTime, // NBA/NFL: already converted to ET. Football (soccer): UTC.
        Long leagueId,        // Our internal DB league ID (used to navigate to the right league)
        String timezone,      // "ET" for NBA/NFL (so frontend can show the label). null for football.
        String clock          // Live game clock for in-progress games, e.g. "3RD · 4:12" (NBA),
                              // "Q3 · 9:22" (NFL). null when not live or unavailable.
) {}
