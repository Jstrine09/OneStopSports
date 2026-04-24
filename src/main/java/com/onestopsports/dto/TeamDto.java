package com.onestopsports.dto;

// The data the frontend receives when fetching a team.
// Used both when listing teams in a league and when showing the team header on TeamDetailPage.
public record TeamDto(
        Long id,           // Our internal database ID — used to navigate to /teams/{id}
        String name,       // Full name — e.g. "Manchester City FC"
        String shortName,  // Abbreviated — e.g. "Man City" (shown in tight spaces like match scores)
        String crestUrl,   // URL to the team's badge image
        String stadium,    // e.g. "Etihad Stadium"
        String country,    // e.g. "England"
        Long leagueId      // Which league this team belongs to
) {}
