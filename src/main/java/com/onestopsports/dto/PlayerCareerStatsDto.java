package com.onestopsports.dto;

import java.util.List;

// Sport-agnostic career stats payload. The same DTO ferries NBA, NFL, and football stats
// to the frontend — the schema is "table-shaped" rather than sport-shaped so the UI can
// render any sport with one component (a labelled grid of columns × rows).
//
// Layout per sport:
//   • NBA  → 3 categories (averages, totals, miscellaneous)
//   • NFL  → 1-2 categories chosen by position (passing for QBs, rushing for RBs, etc.)
//   • Football → 1 category (current-season key stats from API-Football)
//
// Why the indirection? ESPN already groups its stats this way; mirroring their structure
// means we don't lose information or have to invent a normalised superset of every stat.
// The frontend just iterates `categories[].labels` and `categories[].seasons[].values`.
public record PlayerCareerStatsDto(

        // Sport slug — lets the frontend pick a layout or icon set if it wants to.
        // Same vocabulary as Sport.slug: "basketball", "american-football", "football".
        String sport,

        // One or more categories of stats. ESPN returns several per athlete; API-Football
        // collapses to one. Order matters — the frontend renders top-to-bottom.
        List<StatCategory> categories

) {

    // A single category (e.g. "Per Game Averages" or "Passing").
    // Each category has its own columns, per-season rows, and a career-aggregate row.
    public record StatCategory(

            // Machine name — e.g. "averages", "passing".
            String name,

            // Human-readable header for the table — e.g. "Per Game", "Passing".
            String displayName,

            // Column headers, aligned with each SeasonRow.values index.
            // E.g. NBA averages: ["GP", "GS", "MIN", "FG", "FG%", "3PT", "3P%", "FT", "FT%",
            //                     "OR", "DR", "REB", "AST", "BLK", "STL", "PF", "TO", "PTS"]
            List<String> labels,

            // Per-season rows. Newest season is typically last; ESPN returns chronologically.
            List<SeasonRow> seasons,

            // Career aggregate row across every season. Null if the upstream API doesn't
            // provide it (e.g. football, which is single-season only on the free tier).
            SeasonRow career
    ) {}

    // One row of stats — either a single season or the career total.
    // Storing values as strings preserves the upstream formatting (e.g. "7.9-18.9" for
    // shots made-attempted, "41.7" for percentages) without us having to model every
    // possible numeric format.
    public record SeasonRow(

            // Season label — e.g. "2024-25" for NBA, "2024" for NFL. Null for the career row.
            String season,

            // Team — for NBA it's the team abbreviation (e.g. "CLE", "LAL"); for football
            // it's the full club name (e.g. "Paris Saint Germain"). Null for the career row.
            String team,

            // Competition / league name — populated for football to disambiguate the multiple
            // rows API-Football returns per season (Ligue 1, UCL, Coupe de France, etc., all
            // for the same team). Null for NBA / NFL where there's only one competition per
            // row. The frontend hides the Competition column when every row in the category
            // has a null value.
            String competition,

            // Stat values, aligned with the parent category's labels[] by index.
            // Always the same length as labels[].
            List<String> values
    ) {}
}
