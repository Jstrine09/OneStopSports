package com.onestopsports.dto;

// A DTO (Data Transfer Object) is what we actually send back to the frontend as JSON.
// We use DTOs instead of entities directly so we control exactly what data leaves the server.
// SportDto is the simplified view of a Sport — just the fields the frontend needs.
public record SportDto(
        Long id,
        String name,    // e.g. "Football"
        String slug,    // e.g. "football" — used in URLs like /api/sports/football/leagues
        String iconUrl  // URL to the sport's icon image
) {}
