package com.onestopsports.service;

import com.onestopsports.dto.PlayerDto;
import com.onestopsports.dto.TeamDto;
import com.onestopsports.model.League;
import com.onestopsports.model.Team;
import com.onestopsports.repository.TeamRepository;
import com.onestopsports.util.TextNormalizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

// Handles business logic for Teams.
// All data comes from our own database (seeded from football-data.org at startup).
// For NBA/NFL, also calls ESPN to serve historical season rosters.
@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final NbaApiService  nbaApiService;
    private final NflApiService  nflApiService;

    // Manual constructor injection — kept explicit for clarity (see project conventions).
    public TeamService(TeamRepository teamRepository,
                       NbaApiService nbaApiService,
                       NflApiService nflApiService) {
        this.teamRepository = teamRepository;
        this.nbaApiService  = nbaApiService;
        this.nflApiService  = nflApiService;
    }

    // Returns a single team by its database ID, or throws 404 if not found.
    // Called by GET /api/teams/{id} — used to load the team header on TeamDetailPage.
    public TeamDto getTeamById(Long id) {
        return teamRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found: " + id));
    }

    // Returns all teams in a given league.
    // Called by GET /api/leagues/{id}/teams — used for the Teams tab in LeaguesPage.
    public List<TeamDto> getTeamsByLeague(Long leagueId) {
        return teamRepository.findByLeagues_Id(leagueId).stream()
                .map(this::toDto)
                .toList();
    }

    // Returns teams whose name contains the query string (accent-insensitive).
    // We normalize the query the same way the stored name_normalized column was
    // normalized (accents stripped, lower-cased) so "Atletico" matches "Atlético".
    //
    // No de-duplication step is needed any more: since the team↔league refactor a club
    // is a single row regardless of how many competitions it plays in, so "Arsenal" can
    // only match once. (The old presentation-layer collapse-by-name is gone.)
    // Called by GET /api/search?q=...
    public List<TeamDto> searchTeams(String query) {
        String normalized = TextNormalizer.normalize(query);
        return teamRepository.findByNameNormalizedContaining(normalized).stream()
                .limit(8)
                .map(this::toDto)
                .toList();
    }

    /**
     * Returns the roster for a given team in a specific historical season.
     *
     * Routing by sport slug — same dispatch pattern used in MatchService and LeagueService:
     *   basketball        → NbaApiService.fetchRosterDtos (ESPN unofficial API)
     *   american-football → NflApiService.fetchRosterDtos (ESPN unofficial API)
     *   football          → empty list (football-data.org free tier has no historical rosters)
     *
     * @Transactional(readOnly = true) is required because we walk the lazy relationship chain
     * team → league → sport → slug. Without a transaction, Hibernate closes the session after
     * the repository call and throws a LazyInitializationException on the first lazy access.
     *
     * The returned PlayerDtos have null id and teamId — historical players may not be in our
     * database. The frontend checks for null id to disable the profile link.
     *
     * @param teamId  our internal database team ID
     * @param season  start year of the season (e.g. 2022 for "2022-23") — ESPN convention
     */
    @Transactional(readOnly = true)
    public List<PlayerDto> getRosterForSeason(Long teamId, Integer season) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Team not found: " + teamId));

        // externalId is the ESPN (or football-data.org) team ID, stored since V7.
        // If it's null, the team was seeded before V7 and the backfill hasn't run yet.
        String externalId = team.getExternalId();
        if (externalId == null) {
            return List.of(); // Can't call ESPN without an ID — tell the frontend "no data"
        }

        // Sport is now stored directly on the team, so no league hop is needed. Still a lazy
        // load (Sport is fetched LAZY), which is why this method stays @Transactional.
        String sportSlug = team.getSport().getSlug();

        return switch (sportSlug) {
            case "basketball"        -> nbaApiService.fetchRosterDtos(externalId, season);
            case "american-football" -> nflApiService.fetchRosterDtos(externalId, season);
            default                  -> List.of(); // Football: no historical roster API on free tier
        };
    }

    // Package-private (no access modifier) so UserService can also use it
    // to convert FavoriteTeam entities into TeamDtos without duplicating logic.
    //
    // Walks the lazy `leagues` collection to expose both a single primary league (for the
    // team-page header) and the full list of competition ids. All callers run inside a web
    // request, so open-session-in-view keeps the Hibernate session alive for the lazy load.
    TeamDto toDto(Team team) {
        League primary = team.getPrimaryLeague();
        List<Long> leagueIds = team.getLeagues().stream()
                .map(League::getId)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new TeamDto(
                team.getId(),
                team.getName(),
                team.getShortName(),
                team.getCrestUrl(),
                team.getStadium(),
                team.getCountry(),
                primary != null ? primary.getId() : null,
                leagueIds);
    }
}
