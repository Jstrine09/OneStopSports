package com.onestopsports.dto;

import java.time.LocalDate;

// The data the frontend receives when fetching a player.
// Used in squad lists, player detail pages, and the favourites section of ProfilePage.
public record PlayerDto(
        Long id,
        String name,              // Full name — e.g. "Erling Haaland"
        String position,          // e.g. "Forward", "Midfielder", "Defender", "Goalkeeper"
        String nationality,       // e.g. "Norway"
        LocalDate dateOfBirth,    // Used to calculate age in the frontend
        Integer jerseyNumber,     // Shirt number — shown on the squad roster
        String photoUrl,          // Player headshot (currently always null — not populated from API)
        Long teamId               // Which team this player belongs to
) {}
