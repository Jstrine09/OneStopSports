package com.onestopsports.dto;

// The data the frontend receives when fetching a league.
// Includes the externalId so the frontend can use it to match live matches
// back to the correct league (football-data.org uses externalId in their match data).
public record LeagueDto(
        Long id,           // Our internal database ID
        String name,       // e.g. "Premier League"
        String country,    // e.g. "England"
        String logoUrl,    // League logo image URL
        String season,     // e.g. "2024/25"
        Long sportId,      // Which sport this league belongs to
        Integer externalId // football-data.org competition ID (e.g. 2021 for Premier League)
) {}
