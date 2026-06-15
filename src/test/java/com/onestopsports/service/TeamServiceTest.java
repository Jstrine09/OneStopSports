package com.onestopsports.service;

import com.onestopsports.dto.TeamDto;
import com.onestopsports.model.League;
import com.onestopsports.model.Sport;
import com.onestopsports.model.Team;
import com.onestopsports.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests for TeamService focused on the team↔league many-to-many association.
//
// The interesting behaviour after that refactor is the DTO mapping: a club is now a single
// row that can belong to several competitions, so TeamDto must expose BOTH a single primary
// league (for the team-page header) AND the full list of league ids. We also confirm the
// "teams in a league" lookup goes through the join-table query and that search no longer
// needs the old collapse-by-name de-duplication.
//
// All DB access is mocked — plain Mockito, no Spring context, no Hibernate.
@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private NbaApiService  nbaApiService;
    @Mock private NflApiService  nflApiService;

    @InjectMocks
    private TeamService teamService;

    private static Sport footballSport() {
        return Sport.builder().id(1L).slug("football").name("Futbol").build();
    }

    private static League league(Long id, String name, Integer externalId) {
        return League.builder().id(id).sport(footballSport()).name(name).externalId(externalId).build();
    }

    // A club linked to two competitions, deliberately added cup-first so we can prove the
    // primary-league pick is by smallest id (domestic), not insertion order.
    private static Team clubInTwoLeagues() {
        Team team = Team.builder()
                .id(100L)
                .sport(footballSport())
                .name("Real Madrid CF")
                .shortName("RMA")
                .country("Spain")
                .build();
        team.addLeague(league(6L, "UEFA Champions League", 2001)); // higher id (seeded later)
        team.addLeague(league(2L, "La Liga", 2014));                // lower id  (domestic, primary)
        return team;
    }

    @Test
    void getTeamById_clubInMultipleLeagues_exposesPrimaryAndAllLeagueIds() {
        when(teamRepository.findById(100L)).thenReturn(Optional.of(clubInTwoLeagues()));

        TeamDto dto = teamService.getTeamById(100L);

        // Primary league is the smallest id (La Liga = 2), i.e. the domestic competition.
        assertThat(dto.leagueId()).isEqualTo(2L);
        // All competitions the club takes part in, sorted ascending.
        assertThat(dto.leagueIds()).containsExactly(2L, 6L);
    }

    @Test
    void getTeamsByLeague_queriesByLeagueMembership() {
        Team team = clubInTwoLeagues();
        when(teamRepository.findByLeagues_Id(2L)).thenReturn(List.of(team));

        List<TeamDto> teams = teamService.getTeamsByLeague(2L);

        assertThat(teams).singleElement()
                .extracting(TeamDto::name).isEqualTo("Real Madrid CF");
        // Confirms it goes through the join-table query, not the old single-league column.
        verify(teamRepository).findByLeagues_Id(2L);
    }

    @Test
    void searchTeams_returnsEveryMatchWithoutCollapsingByName() {
        // Two genuinely distinct clubs whose names both contain the query. Since the structural
        // de-duplication makes each club a single row, search must NOT drop either of them —
        // the old presentation-layer collapse-by-normalized-name is gone.
        Team city = Team.builder().id(1L).sport(footballSport()).name("Manchester City FC").build();
        city.addLeague(league(2L, "Premier League", 2021));
        Team united = Team.builder().id(2L).sport(footballSport()).name("Manchester United FC").build();
        united.addLeague(league(2L, "Premier League", 2021));
        when(teamRepository.findByNameNormalizedContaining("manchester"))
                .thenReturn(List.of(city, united));

        List<TeamDto> results = teamService.searchTeams("Manchester");

        assertThat(results).extracting(TeamDto::name)
                .containsExactly("Manchester City FC", "Manchester United FC");
    }
}
