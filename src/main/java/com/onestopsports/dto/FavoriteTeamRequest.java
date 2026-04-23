package com.onestopsports.dto;

// The request body sent when a user adds a team to their favourites.
// POST /api/users/me/favorites/teams — body: { "teamId": 5 }
public record FavoriteTeamRequest(
        Long teamId // The internal DB ID of the team to favourite
) {}
