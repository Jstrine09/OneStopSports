package com.onestopsports.service;

import com.onestopsports.dto.PlayerBioDto;
import com.onestopsports.dto.PlayerCareerStatsDto;
import com.onestopsports.dto.PlayerDto;
import com.onestopsports.model.League;
import com.onestopsports.model.Player;
import com.onestopsports.repository.PlayerRepository;
import com.onestopsports.util.TextNormalizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

// Handles business logic for Players.
// All player data comes from our database (seeded at startup from football-data.org).
// Bio enrichment (height, weight, college, draft info) is fetched live from balldontlie.io.
// Career stats are fetched live too — ESPN for NBA/NFL, API-Football for football.
@Service
public class PlayerService {

    private final PlayerRepository   playerRepository;
    private final BallDontLieService ballDontLieService;
    private final NbaApiService      nbaApiService;
    private final NflApiService      nflApiService;
    private final ApiFootballService apiFootballService;

    public PlayerService(PlayerRepository playerRepository,
                         BallDontLieService ballDontLieService,
                         NbaApiService nbaApiService,
                         NflApiService nflApiService,
                         ApiFootballService apiFootballService) {
        this.playerRepository   = playerRepository;
        this.ballDontLieService = ballDontLieService;
        this.nbaApiService      = nbaApiService;
        this.nflApiService      = nflApiService;
        this.apiFootballService = apiFootballService;
    }

    // Returns a single player by their database ID, or throws 404 if not found.
    // Called by GET /api/players/{id} — used on the PlayerDetailPage when navigating directly by URL.
    public PlayerDto getPlayerById(Long id) {
        return playerRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: " + id));
    }

    // Returns all players on a given team.
    // Called by GET /api/teams/{id}/players — used to display the squad roster on TeamDetailPage.
    public List<PlayerDto> getPlayersByTeam(Long teamId) {
        return playerRepository.findByTeamId(teamId).stream()
                .map(this::toDto)
                .toList();
    }

    // Looks up biographical data for a player from balldontlie.io.
    // Returns empty if the player doesn't exist, or if balldontlie has no record
    // (e.g. football/NFL players who aren't in the NBA database).
    // Called by GET /api/players/{id}/bio — used on PlayerDetailPage to enrich NBA profiles.
    public Optional<PlayerBioDto> getPlayerBioById(Long id) {
        // First confirm the player actually exists in our DB
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: " + id));

        // Search balldontlie by full name — will return empty for non-NBA players
        return ballDontLieService.searchPlayerByName(player.getName());
    }

    // Fetches a player's career stats by routing to the right external API based on their sport.
    //
    // Routing (same pattern as LeagueService.getStandings):
    //   • basketball         → ESPN NBA career stats
    //   • american-football  → ESPN NFL career stats
    //   • football (soccer)  → API-SPORTS (api-sports.io) current-season stats
    //
    // For NBA / NFL the externalId is set at seed time by the data loaders. For football the
    // ID is filled in lazily on the first stats request, then persisted so subsequent requests
    // skip the search step (we get exactly one search call per football player, then cheap
    // direct lookups forever — important given the 100 req/day free tier).
    //
    // Returns Optional.empty() when:
    //   - the upstream API has no record of the player
    //   - the player's sport has no stats integration
    //   - rate-limited or otherwise failing upstream calls
    //
    // The controller maps Optional.empty() to HTTP 204 No Content — same pattern as getPlayerBioById.
    //
    // @Transactional so the lazy chain player → team → league → sport can resolve within a
    // Hibernate session, AND so the football "save the externalId we just learned" path works.
    @Transactional
    public Optional<PlayerCareerStatsDto> getPlayerCareerStats(Long id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: " + id));

        // Resolve sport slug via the lazy chain (sport is stored on the team now, so this is
        // player → team → sport). The @Transactional above keeps the session alive.
        String sportSlug = player.getTeam().getSport().getSlug();

        PlayerCareerStatsDto stats = switch (sportSlug) {
            case "basketball" -> hasExternalId(player) ? nbaApiService.fetchCareerStats(player.getExternalId()) : null;
            case "american-football" -> hasExternalId(player) ? nflApiService.fetchCareerStats(player.getExternalId()) : null;
            case "football" -> fetchFootballStats(player);
            default -> null;
        };

        return Optional.ofNullable(stats);
    }

    // True when the player has a non-blank externalId — meaning we can call the upstream API
    // directly without an ID-lookup step. NBA / NFL players always satisfy this after a re-seed.
    private boolean hasExternalId(Player player) {
        return player.getExternalId() != null && !player.getExternalId().isBlank();
    }

    // Football-only flow: lazy ID lookup then per-season fetch.
    //
    // If the player already has an externalId (from a previous successful lookup) we go
    // straight to fetchPlayerStats — that's the steady state and the cheap path.
    //
    // Otherwise we need to find their API-SPORTS player ID first. That requires the league
    // ID, which we map from the football-data competition ID we already store on League.
    // If we can't map the league (e.g. a domestic cup we haven't onboarded) or can't find
    // the player by name, we return null and the controller responds 204.
    //
    // Why save the ID even though it costs a write? It's the difference between 1 API call
    // per visit (cheap) and 2 per visit (expensive on a 100/day budget). Worth it.
    private PlayerCareerStatsDto fetchFootballStats(Player player) {
        int season = apiFootballService.currentSeason();

        // Steady state: ID already known — single API call, return.
        if (hasExternalId(player)) {
            try {
                return apiFootballService.fetchPlayerStats(Integer.parseInt(player.getExternalId()), season);
            } catch (NumberFormatException ex) {
                // ExternalId was set to something non-numeric (shouldn't happen — log and re-search).
                // We don't clear it here; falling through to re-search would risk an infinite loop.
                return null;
            }
        }

        // First time: we need the league ID before we can search by name. A club can now play
        // in several competitions, so we key off its primary (domestic) league — that's the
        // competition whose player list API-Football is searched against.
        League primaryLeague = player.getTeam().getPrimaryLeague();
        Integer fdLeagueId = primaryLeague != null ? primaryLeague.getExternalId() : null;
        Integer apiSportsLeagueId = apiFootballService.mapLeagueId(fdLeagueId);
        if (apiSportsLeagueId == null) {
            // League isn't in our mapping — e.g. a cup competition we haven't added.
            return null;
        }

        // Search the league by the player's name, capture and persist the ID on hit.
        Optional<Integer> found = apiFootballService.searchPlayerId(player.getName(), apiSportsLeagueId, season);
        if (found.isEmpty()) {
            return null;
        }
        player.setExternalId(String.valueOf(found.get()));
        playerRepository.save(player);  // writes within the same @Transactional method

        // Now fetch the actual stats with the freshly-saved ID.
        return apiFootballService.fetchPlayerStats(found.get(), season);
    }

    // Returns players whose name contains the query string (accent-insensitive).
    // The query is normalized (accents stripped, lower-cased) to match the stored
    // name_normalized column, so "Dembele" finds "Dembélé".
    //
    // No de-duplication step is needed any more: since the team↔league refactor a club is a
    // single row with a single squad, so each player exists exactly once. (The old collapse
    // by (player name + club name) — which worked around the duplicated squads — is gone.)
    // Capped at 10 results so the search results page stays readable.
    // Called by GET /api/search?q=...
    public List<PlayerDto> searchPlayers(String query) {
        String normalized = TextNormalizer.normalize(query);
        return playerRepository.findByNameNormalizedContaining(normalized).stream()
                .limit(10)
                .map(this::toDto)
                .toList();
    }

    // Package-private so UserService can reuse this converter for favourite player data.
    PlayerDto toDto(Player player) {
        return new PlayerDto(
                player.getId(),
                player.getName(),
                player.getPosition(),
                player.getNationality(),
                player.getDateOfBirth(),
                player.getJerseyNumber(),
                resolvePhotoUrl(player),
                player.getTeam().getId()); // Triggers a lazy load of the Team — expected here
    }

    // Photo URL resolution — three layers, returning the first hit:
    //
    //   1. A persisted Player.photoUrl on the row. This is how football players get a photo:
    //      when their stats page is first opened we capture API-Football's player.photo URL
    //      and save it (see PlayerService.fetchFootballStats — added in a follow-up).
    //
    //   2. ESPN's deterministic headshot CDN URL, derived from the sport + externalId.
    //      Pattern: https://a.espncdn.com/i/headshots/{sport}/players/full/{espnAthleteId}.png
    //      This covers every NBA / NFL player without us storing or fetching anything —
    //      the URL is reconstructable from data we already have.
    //
    //   3. null — used for football players whose stats page hasn't been visited yet
    //      (no photoUrl stored, no ESPN equivalent). The frontend falls back to initials.
    //
    // Reads .team.league.sport.slug — three lazy hops through the OSIV-managed Hibernate
    // session. All callers of toDto() run inside a web request, so OSIV keeps the session
    // alive long enough to resolve them.
    private static String resolvePhotoUrl(Player player) {
        // Layer 1: stored value wins (football players post-first-stats-visit)
        if (player.getPhotoUrl() != null && !player.getPhotoUrl().isBlank()) {
            return player.getPhotoUrl();
        }

        // Layer 2: ESPN CDN — only meaningful when we have an ESPN athlete ID
        String externalId = player.getExternalId();
        if (externalId == null || externalId.isBlank()) return null;

        // Walk the chain defensively — bad seed data shouldn't crash this mapper.
        // Sport now lives directly on the team (player → team → sport).
        if (player.getTeam() == null
                || player.getTeam().getSport() == null) {
            return null;
        }
        String sportSlug = player.getTeam().getSport().getSlug();

        return switch (sportSlug) {
            case "basketball"        -> "https://a.espncdn.com/i/headshots/nba/players/full/" + externalId + ".png";
            case "american-football" -> "https://a.espncdn.com/i/headshots/nfl/players/full/" + externalId + ".png";
            default                  -> null; // football handled by Layer 1 once stats visited; others unsupported
        };
    }
}
