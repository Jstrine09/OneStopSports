package com.onestopsports.dto;

import java.util.List;

// The data the frontend receives when fetching a team.
// Used both when listing teams in a league and when showing the team header on TeamDetailPage.
public record TeamDto(
        Long id,             // Our internal database ID — used to navigate to /teams/{id}
        String name,         // Full name — e.g. "Manchester City FC"
        String shortName,    // Abbreviated — e.g. "Man City" (shown in tight spaces like match scores)
        String crestUrl,     // URL to the team's badge image
        String stadium,      // e.g. "Etihad Stadium"
        String country,      // e.g. "England"
        Long leagueId,       // Primary league — kept for the team-page league header (single value)
        List<Long> leagueIds // EVERY competition this club takes part in (a club can be in several)
) {
    // Convenience constructor for synthetic single-league DTOs (standings rows, box-score
    // teams) that are built from external-API data rather than a persisted club. They only
    // know one league, so leagueIds is derived from it (empty when there's no league).
    public TeamDto(Long id, String name, String shortName, String crestUrl,
                   String stadium, String country, Long leagueId) {
        this(id, name, shortName, crestUrl, stadium, country, leagueId,
                leagueId == null ? List.of() : List.of(leagueId));
    }
}
