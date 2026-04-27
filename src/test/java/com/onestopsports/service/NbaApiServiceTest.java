package com.onestopsports.service;

import com.onestopsports.dto.MatchDto;
import com.onestopsports.dto.StandingsEntryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Answers;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// Unit tests for NbaApiService — no Spring context, no real HTTP calls.
//
// How the mocking works here:
//   NbaApiService uses RestClient's fluent chain: restClient.get().uri(...).retrieve().body(Class)
//   Each step in that chain returns a different interface type. Rather than mocking each type
//   separately, we use Mockito's RETURNS_DEEP_STUBS mode which automatically creates sub-mocks
//   for every call in the chain. We then stub just the final body() call with our test data.
//
//   RETURNS_DEEP_STUBS is ideal for "chains" — the same sub-mock is returned for any argument
//   passed to intermediate methods (uri, retrieve), so the stub is independent of the exact URL.
//
// NbaApiService has a package-private test constructor (same package = same com.onestopsports.service)
// that accepts pre-built RestClient instances, which we inject here instead of using @InjectMocks.
@SuppressWarnings({"unchecked", "rawtypes"})  // RestClient generics cause unchecked cast warnings in test stubs
@ExtendWith(MockitoExtension.class)
class NbaApiServiceTest {

    // RETURNS_DEEP_STUBS auto-creates sub-mocks for every method in the chain.
    // This covers: restClient.get() → uriSpec → headersSpec → responseSpec → body()
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RestClient restClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RestClient standingsClient;

    // We construct NbaApiService manually using the package-private test constructor
    // (rather than @InjectMocks, which needs @Value to resolve the URL strings)
    NbaApiService nbaApiService;

    @BeforeEach
    void setUp() {
        nbaApiService = new NbaApiService(restClient, standingsClient);
    }

    // ── Helpers: build ESPN response objects ──────────────────────────────────

    // Builds a minimal EspnEvent (game) suitable for a single-game scoreboard response.
    // homeScore/awayScore are passed as strings, matching ESPN's format ("112", "98").
    // Use empty string "" for unstarted games (ESPN sends "" before tip-off).
    private NbaApiService.EspnScoreboardResponse scoreboardWith(
            String statusName, String homeScore, String awayScore) {

        var statusType = new NbaApiService.EspnStatusType(statusName, "description");
        var status     = new NbaApiService.EspnEventStatus(statusType);

        var homeTeam = new NbaApiService.EspnCompTeam("1", "Boston Celtics", "BOS", "logo.png");
        var awayTeam = new NbaApiService.EspnCompTeam("2", "Miami Heat",     "MIA", "logo2.png");
        var homeComp = new NbaApiService.EspnCompetitor("home", homeTeam, homeScore);
        var awayComp = new NbaApiService.EspnCompetitor("away", awayTeam, awayScore);
        var comp     = new NbaApiService.EspnCompetition(List.of(homeComp, awayComp));
        var event    = new NbaApiService.EspnEvent("12345", "2025-04-20T17:00Z", status, List.of(comp));

        return new NbaApiService.EspnScoreboardResponse(List.of(event));
    }

    // Builds a standings response with two teams (one per conference) and configurable win counts
    private NbaApiService.EspnStandingsResponse standingsWithTwo(int team1Wins, int team2Wins) {
        var entry1 = new NbaApiService.EspnStandingsEntry(
                new NbaApiService.EspnStandingsTeam("1", "Boston Celtics", "BOS", "Boston"),
                List.of(
                        new NbaApiService.EspnStat("wins",   (double) team1Wins,        String.valueOf(team1Wins)),
                        new NbaApiService.EspnStat("losses", (double) (82 - team1Wins), String.valueOf(82 - team1Wins))));

        var entry2 = new NbaApiService.EspnStandingsEntry(
                new NbaApiService.EspnStandingsTeam("5", "Cleveland Cavaliers", "CLE", "Cleveland"),
                List.of(
                        new NbaApiService.EspnStat("wins",   (double) team2Wins,        String.valueOf(team2Wins)),
                        new NbaApiService.EspnStat("losses", (double) (82 - team2Wins), String.valueOf(82 - team2Wins))));

        // Split across East and West conferences (realistic ESPN response structure)
        var eastConf = new NbaApiService.EspnConference("Eastern Conference",
                new NbaApiService.EspnStandingsSection(List.of(entry1)));
        var westConf = new NbaApiService.EspnConference("Western Conference",
                new NbaApiService.EspnStandingsSection(List.of(entry2)));

        return new NbaApiService.EspnStandingsResponse(List.of(eastConf, westConf));
    }

    // ── fetchGameDtosByDate ───────────────────────────────────────────────────

    @Test
    void fetchGameDtosByDate_nullApiResponse_returnsEmptyList() {
        // If ESPN's scoreboard endpoint returns nothing (network error returns null body),
        // we should get an empty list rather than a NullPointerException
        when(restClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnScoreboardResponse.class))
                .thenReturn(null);

        List<MatchDto> result = nbaApiService.fetchGameDtosByDate(LocalDate.of(2025, 4, 20), 7L);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchGameDtosByDate_finalGame_mapsStatusToFinished() {
        // ESPN uses STATUS_FINAL for completed games.
        // Our app normalises this to "FINISHED" so all sports use the same status strings.
        when(restClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnScoreboardResponse.class))
                .thenReturn(scoreboardWith("STATUS_FINAL", "112", "98"));

        List<MatchDto> result = nbaApiService.fetchGameDtosByDate(LocalDate.of(2025, 4, 20), 7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("FINISHED");
        // Scores should be parsed from ESPN's string format into integers
        assertThat(result.get(0).homeScore()).isEqualTo(112);
        assertThat(result.get(0).awayScore()).isEqualTo(98);
    }

    @Test
    void fetchGameDtosByDate_inProgressGame_mapsStatusToLive() {
        // STATUS_IN_PROGRESS → "LIVE" so the frontend highlights the score in green
        when(restClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnScoreboardResponse.class))
                .thenReturn(scoreboardWith("STATUS_IN_PROGRESS", "56", "49"));

        List<MatchDto> result = nbaApiService.fetchGameDtosByDate(LocalDate.of(2025, 4, 20), 7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("LIVE");
        assertThat(result.get(0).homeScore()).isEqualTo(56);
        assertThat(result.get(0).awayScore()).isEqualTo(49);
    }

    @Test
    void fetchGameDtosByDate_scheduledGame_hasNullScoresAndScheduledStatus() {
        // Before the game starts, ESPN sends empty strings "" for scores.
        // We store them as null so the frontend shows "--" instead of "0"
        when(restClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnScoreboardResponse.class))
                .thenReturn(scoreboardWith("STATUS_SCHEDULED", "", ""));

        List<MatchDto> result = nbaApiService.fetchGameDtosByDate(LocalDate.of(2025, 4, 20), 7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("SCHEDULED");
        // Null scores — not zero — so the frontend renders "--" not "0"
        assertThat(result.get(0).homeScore()).isNull();
        assertThat(result.get(0).awayScore()).isNull();
    }

    @Test
    void fetchGameDtosByDate_setsLeagueIdOnEachMatchDto() {
        // The leagueId in each MatchDto must match the DB ID passed in,
        // so the frontend can navigate back to the correct league page
        when(restClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnScoreboardResponse.class))
                .thenReturn(scoreboardWith("STATUS_FINAL", "110", "105"));

        List<MatchDto> result = nbaApiService.fetchGameDtosByDate(LocalDate.of(2025, 4, 20), 99L);

        assertThat(result.get(0).leagueId()).isEqualTo(99L);
    }

    // ── fetchPlayersByTeam ────────────────────────────────────────────────────

    @Test
    void fetchPlayersByTeam_nullApiResponse_returnsEmptyList() {
        // Guard against null response — ESPN occasionally returns no body on error paths
        when(restClient.get().uri(any(String.class), any(Object[].class)).retrieve()
                .body(NbaApiService.EspnRosterResponse.class))
                .thenReturn(null);

        List<NbaApiService.EspnAthlete> result = nbaApiService.fetchPlayersByTeam("1");

        assertThat(result).isEmpty();
    }

    // ── fetchStandings ────────────────────────────────────────────────────────

    @Test
    void fetchStandings_apiException_returnsEmptyListWithoutThrowing() {
        // If the standings endpoint throws (e.g. off-season, ESPN structure change),
        // the service should catch it and return an empty list — NOT propagate a 500 to the client.
        when(standingsClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnStandingsResponse.class))
                .thenThrow(new RestClientException("ESPN standings unavailable"));

        // Must NOT throw — try-catch in fetchStandings handles RestClientExceptions
        List<StandingsEntryDto> result = nbaApiService.fetchStandings(7L);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchStandings_nullApiResponse_returnsEmptyList() {
        // Null body from standings endpoint → graceful empty list
        when(standingsClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnStandingsResponse.class))
                .thenReturn(null);

        assertThat(nbaApiService.fetchStandings(7L)).isEmpty();
    }

    @Test
    void fetchStandings_sortsTeamsByWinsDescending() {
        // The team with more wins should appear at position 1 regardless of which
        // conference they're in — we rank all 30 teams globally, not by conference
        when(standingsClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnStandingsResponse.class))
                .thenReturn(standingsWithTwo(
                        40,  // team1 (Boston) — 40 wins — should be position 2
                        64)); // team2 (Cleveland) — 64 wins — should be position 1

        List<StandingsEntryDto> result = nbaApiService.fetchStandings(7L);

        assertThat(result).hasSize(2);
        // Cleveland (64 wins) should be ranked 1st
        assertThat(result.get(0).position()).isEqualTo(1);
        assertThat(result.get(0).team().name()).isEqualTo("Cleveland Cavaliers");
        assertThat(result.get(0).won()).isEqualTo(64);
        // Boston (40 wins) should be ranked 2nd
        assertThat(result.get(1).position()).isEqualTo(2);
        assertThat(result.get(1).team().name()).isEqualTo("Boston Celtics");
        assertThat(result.get(1).won()).isEqualTo(40);
    }

    @Test
    void fetchStandings_setsLeagueIdOnEachEntry() {
        // Each StandingsEntryDto should carry our DB league ID so the frontend
        // can link team rows back to the right league
        when(standingsClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnStandingsResponse.class))
                .thenReturn(standingsWithTwo(50, 45));

        List<StandingsEntryDto> result = nbaApiService.fetchStandings(7L);

        assertThat(result).allSatisfy(entry ->
                assertThat(entry.team().leagueId()).isEqualTo(7L));
    }

    @Test
    void fetchStandings_basketballHasNoDraws() {
        // Basketball games never end in a draw — drawn should always be 0
        when(standingsClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnStandingsResponse.class))
                .thenReturn(standingsWithTwo(50, 45));

        List<StandingsEntryDto> result = nbaApiService.fetchStandings(7L);

        assertThat(result).allSatisfy(entry ->
                assertThat(entry.drawn()).isEqualTo(0));
    }

    @Test
    void fetchStandings_playedEqualsWinsAndLosses() {
        // In the NBA there are no draws, so played = wins + losses exactly
        int wins = 50, losses = 32;
        when(standingsClient.get().uri(anyString()).retrieve()
                .body(NbaApiService.EspnStandingsResponse.class))
                .thenReturn(standingsWithTwo(wins, wins)); // both teams same wins for simplicity

        List<StandingsEntryDto> result = nbaApiService.fetchStandings(7L);

        result.forEach(entry ->
                assertThat(entry.played()).isEqualTo(entry.won() + entry.lost()));
    }
}
