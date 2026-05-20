package com.onestopsports.service;

import com.onestopsports.dto.PlayerBioDto;
import com.onestopsports.dto.PlayerCareerStatsDto;
import com.onestopsports.dto.PlayerDto;
import com.onestopsports.model.Player;
import com.onestopsports.repository.PlayerRepository;
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

    public PlayerService(PlayerRepository playerRepository,
                         BallDontLieService ballDontLieService,
                         NbaApiService nbaApiService,
                         NflApiService nflApiService) {
        this.playerRepository   = playerRepository;
        this.ballDontLieService = ballDontLieService;
        this.nbaApiService      = nbaApiService;
        this.nflApiService      = nflApiService;
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
    //   • football (soccer)  → not yet implemented — returns Optional.empty() until Phase 6 lands
    //
    // Returns Optional.empty() when:
    //   - the player has no externalId (e.g. pre-V6 row, never re-seeded)
    //   - the sport has no stats integration
    //   - the upstream API returns nothing
    //
    // The controller maps Optional.empty() to HTTP 204 No Content — same pattern as getPlayerBioById.
    //
    // @Transactional(readOnly=true) so the lazy chain player → team → league → sport can resolve
    // within a Hibernate session (the same pattern used by MatchService.getMatchesByLeagueAndDate).
    @Transactional(readOnly = true)
    public Optional<PlayerCareerStatsDto> getPlayerCareerStats(Long id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: " + id));

        // No externalId means we never captured the upstream API's player ID for this row.
        // Most likely a pre-V6 player that hasn't been re-seeded yet — gracefully return empty.
        if (player.getExternalId() == null || player.getExternalId().isBlank()) {
            return Optional.empty();
        }

        // Resolve sport slug via the lazy chain. The @Transactional above keeps the session alive.
        String sportSlug = player.getTeam().getLeague().getSport().getSlug();

        PlayerCareerStatsDto stats = switch (sportSlug) {
            case "basketball"         -> nbaApiService.fetchCareerStats(player.getExternalId());
            case "american-football"  -> nflApiService.fetchCareerStats(player.getExternalId());
            // football (soccer) — Phase 6 will plug in ApiFootballService here.
            // Until then we return null so the controller can respond 204.
            case "football"           -> null;
            default                   -> null;
        };

        return Optional.ofNullable(stats);
    }

    // Returns players whose name contains the query string (case-insensitive).
    // Capped at 10 results so the search results page stays readable.
    // Called by GET /api/search?q=...
    public List<PlayerDto> searchPlayers(String query) {
        return playerRepository.findByNameContainingIgnoreCase(query)
                .stream()
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
                player.getPhotoUrl(),
                player.getTeam().getId()); // Triggers a lazy load of the Team — expected here
    }
}
