package com.onestopsports.dto;

// Represents one row in a league standings table.
// e.g. "Manchester City — 1st, 30 played, 21W 5D 4L, GF 72, GA 30, 68 pts"
public record StandingsEntryDto(
        Integer position,    // League position (1st, 2nd, 3rd...)
        TeamDto team,        // The team for this row (includes name and crest)
        Integer played,      // Total games played
        Integer won,         // Games won
        Integer drawn,       // Games drawn
        Integer lost,        // Games lost
        Integer goalsFor,    // Total goals scored
        Integer goalsAgainst, // Total goals conceded
        Integer points       // Total points (usually 3 for a win, 1 for a draw, 0 for a loss)
) {}
