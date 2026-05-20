package com.onestopsports.dto;

// Represents one row in a league standings table.
// e.g. "Manchester City — 1st, 30 played, 21W 5D 4L, GF 72, GA 30, 68 pts"
//
// The last two fields are NFL-only:
//   conference — "American Football Conference" / "National Football Conference" (null for football/NBA)
//   division   — "AFC East", "NFC West", etc.                                   (null for football/NBA)
// When the frontend sees a non-null division it switches to the grouped conference/division layout
// instead of a flat table, and swaps column headers (PF/PA/DIFF instead of GF/GA/GD).
public record StandingsEntryDto(
        Integer position,      // League or within-division rank (1st, 2nd, 3rd...)
        TeamDto team,          // The team for this row (includes name and crest)
        Integer played,        // Total games played
        Integer won,           // Games won
        Integer drawn,         // Games drawn (NFL: ties)
        Integer lost,          // Games lost
        Integer goalsFor,      // Goals/points scored (reused for NFL points-for)
        Integer goalsAgainst,  // Goals/points conceded (reused for NFL points-against)
        Integer points,        // Accumulated points (3W+1D for football; wins for NFL/NBA)
        String conference,     // NFL only — e.g. "American Football Conference"   (null for football/NBA)
        String division        // NFL only — e.g. "AFC East", "NFC West"           (null for football/NBA)
) {}
