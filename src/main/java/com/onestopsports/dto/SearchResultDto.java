package com.onestopsports.dto;

import java.util.List;

// Combined search result — bundles matching teams and players into one response.
// Returned by GET /api/search?q={query} so the frontend gets both in a single request.
public record SearchResultDto(
        List<TeamDto> teams,     // Teams whose name contains the query string
        List<PlayerDto> players  // Players whose name contains the query string
) {}
