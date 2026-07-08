package com.onestopsports.service;

import com.onestopsports.dto.MatchDto;
import com.onestopsports.dto.PlayerCareerStatsDto;
import com.onestopsports.dto.StandingsEntryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// Unit tests for NflApiService — no Spring context, no real HTTP calls.
//
// How the mocking works here:
//   NflApiService uses RestClient's fluent chain: restClient.get().uri(...).retrieve().body(Class)
//   Each step in that chain returns a different interface type. Rather than mocking each step
//   separately, we use Mockito's RETURNS_DEEP_STUBS mode which automatically creates sub-mocks
//   for every call in the chain. We then stub just the final body() call with our test data.
//
//   NflApiService has THREE RestClient fields (restClient, standingsClient, statsClient) because
//   ESPN's NFL data lives on three different URL paths. We mock all three separately using the
//   package-private test constructor added in plan 01-01.
@SuppressWarnings({"unchecked", "rawtypes"})  // RestClient generics cause unchecked cast warnings in test stubs
@ExtendWith(MockitoExtension.class)
class NflApiServiceTest {

    // Covers restClient.get().uri(...).retrieve().body(...) — used for scoreboard fetches
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RestClient restClient;

    // Separate client for the standings endpoint (a different ESPN URL path — apis/v2, not apis/site/v2)
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RestClient standingsClient;

    // Third client for career stats (yet another ESPN path — common/v3)
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RestClient statsClient;

    // We construct NflApiService manually using the package-private test constructor
    // (rather than @InjectMocks, which needs @Value to resolve the URL strings)
    NflApiService nflApiService;

    @BeforeEach
    void setUp() {
        nflApiService = new NflApiService(restClient, standingsClient, statsClient);
    }

    // ── Helpers: build ESPN response objects ──────────────────────────────────

    // Builds a minimal EspnScoreboardResponse with one game between two teams,
    // matching the shape ESPN returns from GET /scoreboard?dates=YYYYMMDD.
    // Scores are passed as strings, matching ESPN's format ("24", "20").
    // Use empty string "" for unstarted games (ESPN sends "" before kickoff).
    private NflApiService.EspnScoreboardResponse scoreboardWith(
            String statusName, String homeScore, String awayScore) {

        var statusType = new NflApiService.EspnStatusType(statusName, "description");
        var status     = new NflApiService.EspnEventStatus(statusType, "9:22", 3);

        var homeTeam = new NflApiService.EspnCompTeam("12", "Kansas City Chiefs", "KC", null);
        var awayTeam = new NflApiService.EspnCompTeam("1",  "Atlanta Falcons",    "ATL", null);
        var homeComp = new NflApiService.EspnCompetitor("home", homeTeam, homeScore);
        var awayComp = new NflApiService.EspnCompetitor("away", awayTeam, awayScore);
        var comp     = new NflApiService.EspnCompetition(List.of(homeComp, awayComp));
        var event    = new NflApiService.EspnEvent("401671759", "2025-09-07T17:00Z", status, List.of(comp));

        return new NflApiService.EspnScoreboardResponse(List.of(event));
    }

    // Builds a standings response with two conferences, each containing one team,
    // matching ESPN's flat-per-conference structure (children[] → standings.entries[]).
    private NflApiService.EspnStandingsResponse standingsWithTwo(int team1Wins, int team2Wins) {
        var entry1 = new NflApiService.EspnStandingsEntry(
                new NflApiService.EspnStandingsTeam("12", "Kansas City Chiefs", "KC", "Kansas City", null),
                List.of(
                        new NflApiService.EspnStat("wins",   (double) team1Wins,         String.valueOf(team1Wins)),
                        new NflApiService.EspnStat("losses", (double) (17 - team1Wins),  String.valueOf(17 - team1Wins)),
                        new NflApiService.EspnStat("ties",   0.0, "0"),
                        new NflApiService.EspnStat("pointsFor", 400.0, "400"),
                        new NflApiService.EspnStat("pointsAgainst", 300.0, "300")));

        var entry2 = new NflApiService.EspnStandingsEntry(
                new NflApiService.EspnStandingsTeam("1", "Atlanta Falcons", "ATL", "Atlanta", null),
                List.of(
                        new NflApiService.EspnStat("wins",   (double) team2Wins,        String.valueOf(team2Wins)),
                        new NflApiService.EspnStat("losses", (double) (17 - team2Wins), String.valueOf(17 - team2Wins)),
                        new NflApiService.EspnStat("ties",   0.0, "0"),
                        new NflApiService.EspnStat("pointsFor", 350.0, "350"),
                        new NflApiService.EspnStat("pointsAgainst", 320.0, "320")));

        // KC is AFC West, ATL is NFC South — split across the two conferences
        var afc = new NflApiService.EspnConference("American Football Conference",
                new NflApiService.EspnConferenceStandings(List.of(entry1)));
        var nfc = new NflApiService.EspnConference("National Football Conference",
                new NflApiService.EspnConferenceStandings(List.of(entry2)));

        return new NflApiService.EspnStandingsResponse(List.of(afc, nfc));
    }

    // Builds a minimal career-stats response with one category containing one season row.
    private NflApiService.EspnStatsResponse statsResponseWith() {
        var season = new NflApiService.EspnStatSeason(2024, "2024");
        var entry  = new NflApiService.EspnStatEntry("kansas-city-chiefs", "KC", season, List.of("17", "400", "4000"));
        var category = new NflApiService.EspnStatCategory(
                "passing", "Passing", List.of("GP", "YDS", "TD"), List.of(entry), List.of("17", "4000", "40"));
        return new NflApiService.EspnStatsResponse(List.of(category));
    }

    // ── fetchGameDtosByDate ───────────────────────────────────────────────────

    @Test
    void fetchGameDtosByDate_finalGame_mapsStatusScoresTimezoneAndLeagueId() {
        // HAPPY PATH: a STATUS_FINAL event should map to a MatchDto with status "FINISHED",
        // integer scores parsed from ESPN's string format, timezone "ET" (NFL games are
        // always converted to Eastern Time), and the dbLeagueId we passed in.
        when(restClient.get().uri(anyString()).retrieve()
                .body(NflApiService.EspnScoreboardResponse.class))
                .thenReturn(scoreboardWith("STATUS_FINAL", "24", "20"));

        List<MatchDto> result = nflApiService.fetchGameDtosByDate(LocalDate.of(2025, 9, 7), 3L);

        assertThat(result).hasSize(1);
        MatchDto match = result.get(0);
        assertThat(match.status()).isEqualTo("FINISHED");
        assertThat(match.homeScore()).isEqualTo(24);
        assertThat(match.awayScore()).isEqualTo(20);
        assertThat(match.timezone()).isEqualTo("ET");
        assertThat(match.leagueId()).isEqualTo(3L);
    }

    @Test
    void fetchGameDtosByDate_nullBody_returnsEmptyList() {
        // NULL-BODY GUARD: this method has no try/catch, so a null body must be handled
        // by the explicit `if (response == null ...)` check rather than throwing an NPE.
        when(restClient.get().uri(anyString()).retrieve()
                .body(NflApiService.EspnScoreboardResponse.class))
                .thenReturn(null);

        List<MatchDto> result = nflApiService.fetchGameDtosByDate(LocalDate.of(2025, 9, 7), 3L);

        assertThat(result).isEmpty();
    }

    // ── fetchStandings ────────────────────────────────────────────────────────

    @Test
    void fetchStandings_twoConferences_mapsRankedEntriesWithConferenceAndDivision() {
        // STANDINGS HAPPY PATH: with one team per conference/division, each team is ranked
        // #1 within its division. Both conference and division should be populated (NFL-only
        // fields), and each team's leagueId should carry the dbLeagueId we passed in.
        when(standingsClient.get().uri(anyString()).retrieve()
                .body(NflApiService.EspnStandingsResponse.class))
                .thenReturn(standingsWithTwo(12, 9));

        List<StandingsEntryDto> result = nflApiService.fetchStandings(5L);

        assertThat(result).hasSize(2);

        StandingsEntryDto kc = result.stream()
                .filter(e -> e.team().name().equals("Kansas City Chiefs"))
                .findFirst().orElseThrow();
        assertThat(kc.position()).isEqualTo(1);
        assertThat(kc.conference()).isEqualTo("American Football Conference");
        assertThat(kc.division()).isEqualTo("AFC West");
        assertThat(kc.team().leagueId()).isEqualTo(5L);

        StandingsEntryDto atl = result.stream()
                .filter(e -> e.team().name().equals("Atlanta Falcons"))
                .findFirst().orElseThrow();
        assertThat(atl.position()).isEqualTo(1);
        assertThat(atl.conference()).isEqualTo("National Football Conference");
        assertThat(atl.division()).isEqualTo("NFC South");
        assertThat(atl.team().leagueId()).isEqualTo(5L);
    }

    @Test
    void fetchStandings_restClientException_returnsEmptyListWithoutThrowing() {
        // SOFT-FAIL: if the standings endpoint throws (e.g. ESPN structure change,
        // network error), fetchStandings must catch it and return an empty list —
        // NOT propagate a 500 to the client.
        when(standingsClient.get().uri(anyString()).retrieve()
                .body(NflApiService.EspnStandingsResponse.class))
                .thenThrow(new RestClientException("ESPN standings unavailable"));

        List<StandingsEntryDto> result = nflApiService.fetchStandings(5L);

        assertThat(result).isEmpty();
    }

    // ── fetchCareerStats ──────────────────────────────────────────────────────

    @Test
    void fetchCareerStats_happyPath_mapsCategoriesWithCareerRow() {
        // HAPPY PATH: a valid stats response should map to a PlayerCareerStatsDto with
        // sport "american-football" and one populated category carrying a career totals row.
        when(statsClient.get().uri(any(String.class), any(Object[].class)).retrieve()
                .body(NflApiService.EspnStatsResponse.class))
                .thenReturn(statsResponseWith());

        PlayerCareerStatsDto result = nflApiService.fetchCareerStats("3139477");

        assertThat(result).isNotNull();
        assertThat(result.sport()).isEqualTo("american-football");
        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).career()).isNotNull();
    }

    @Test
    void fetchCareerStats_restClientException_returnsNull() {
        // SOFT-FAIL: if ESPN returns a 404/500/timeout for the athlete's stats endpoint,
        // fetchCareerStats must catch the exception and return null (not propagate) so
        // the caller (PlayerService) treats it as "no stats available" → 204 No Content.
        when(statsClient.get().uri(any(String.class), any(Object[].class)).retrieve()
                .body(NflApiService.EspnStatsResponse.class))
                .thenThrow(new RestClientException("ESPN stats unavailable"));

        PlayerCareerStatsDto result = nflApiService.fetchCareerStats("3139477");

        assertThat(result).isNull();
    }
}
