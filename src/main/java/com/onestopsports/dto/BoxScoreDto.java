package com.onestopsports.dto;

import java.util.List;

// ── BoxScoreDto ────────────────────────────────────────────────────────────────
// Represents the full box score for a completed (or in-progress) match.
//
// Design goals:
//   1. Sport-agnostic structure — the same record works for NBA, NFL, and football.
//      Each sport gives different stat labels ("Points", "Passing Yards", "Goals")
//      but they all fit into the same List<StatLine> pattern.
//   2. All fields use a simple label/value approach so the frontend doesn't need
//      sport-specific rendering logic — it just iterates and displays.
//   3. Nested records are "sealed" to BoxScoreDto — they don't appear elsewhere in
//      the codebase, keeping this DTO self-contained.
//
// Populated by:
//   NbaApiService.fetchBoxScore()         → ESPN /summary endpoint (real data)
//   NflApiService.fetchBoxScore()         → ESPN /summary endpoint (real data)
//   ExternalApiService.fetchFootballBoxScore() → derived from match events (free tier)

public record BoxScoreDto(

    // Which sport produced this box score ("basketball", "american-football", "football").
    // Used on the frontend to pick the right column headers and layout.
    String sport,

    // Always exactly 2 entries: index 0 = home team, index 1 = away team.
    // Holds the aggregate team-level stats (total points, total yards, etc.).
    List<TeamBoxScore> teams,

    // One entry per team, containing the per-player stat rows.
    // Index 0 = home team players, index 1 = away team players.
    // Empty for football (free tier doesn't expose player-level stats).
    List<PlayerStatGroup> playerStats

) {

    // ── TeamBoxScore ───────────────────────────────────────────────────────────
    // The team-level aggregate stats for one side of the match.
    // Example for NBA:  stats = [("Points","112"), ("Rebounds","44"), ...]
    // Example for NFL:  stats = [("Total Yards","387"), ("Passing Yards","241"), ...]
    // Example for football: stats = [("Goals","2"), ("Yellow Cards","1"), ...]
    public record TeamBoxScore(
        Long teamId,
        String teamName,
        String abbreviation,
        boolean isHome,

        // Ordered list of stat label/value pairs to display in a table row.
        // Frontend renders them in the order they arrive — no reordering needed.
        List<StatLine> stats
    ) {}

    // ── StatLine ───────────────────────────────────────────────────────────────
    // One stat for a team or player: a human-readable label and its display value.
    // Example: label="Points", value="112"   or   label="FG%", value="47.2"
    public record StatLine(String label, String value) {}

    // ── PlayerStatGroup ────────────────────────────────────────────────────────
    // All player stats for one team.
    // 'columns' are the column headers (e.g. ["Player","MIN","PTS","REB","AST"]).
    // 'players' is the list of rows, one per player, where each row's stats list
    // aligns positionally with the columns list (stats[0] = column[1], etc. —
    // the first "column" is always the player name, which comes from playerName).
    public record PlayerStatGroup(
        Long teamId,
        String teamName,
        boolean isHome,

        // Column header labels (excluding the implicit "Player" name column).
        List<String> columns,

        // One row per player.
        List<PlayerStatRow> players
    ) {}

    // ── PlayerStatRow ──────────────────────────────────────────────────────────
    // A single player's stats for this match.
    // 'stats' aligns with PlayerStatGroup.columns positionally.
    // 'starter' = true if the player started the game (ESPN provides this).
    public record PlayerStatRow(
        String playerName,

        // Nullable — football derived box scores won't always have a DB player ID.
        Long playerId,

        boolean starter,

        // The stat values in the same order as PlayerStatGroup.columns.
        // Example: ["32:14", "22", "8", "5", "1"] for MIN/PTS/REB/AST/STL
        List<String> stats
    ) {}
}
