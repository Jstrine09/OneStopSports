package com.onestopsports.dto;

// The request body sent when a user adds a player to their favourites.
// POST /api/users/me/favorites/players — body: { "playerId": 42 }
public record FavoritePlayerRequest(
        Long playerId // The internal DB ID of the player to favourite
) {}
