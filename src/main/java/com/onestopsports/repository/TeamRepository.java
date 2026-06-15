package com.onestopsports.repository;

import com.onestopsports.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// Handles all database operations for the Team table.
public interface TeamRepository extends JpaRepository<Team, Long> {

    // Finds all teams that take part in a specific league. Since a team now belongs to
    // MANY leagues (team↔league join table), Spring Data traverses the `leagues` collection
    // and matches on the league id. Replaces the old findByLeagueId, which assumed one
    // league per team.
    // SQL: SELECT t.* FROM team t JOIN team_league tl ON tl.team_id = t.id WHERE tl.league_id = ?
    List<Team> findByLeagues_Id(Long leagueId);

    // All teams belonging to a sport — used by the NBA / NFL loaders to find existing
    // franchises (each sport has a single league, so this is equivalent to "teams in the
    // NBA/NFL" without depending on the join table).
    // SQL: SELECT * FROM team WHERE sport_id = ?
    List<Team> findBySportId(Long sportId);

    // Find-or-create key for the football loader: a club is unique within a sport by its
    // upstream provider id (football-data.org team id), which is identical across every
    // competition the club appears in. JOIN FETCH the leagues so the caller can add a new
    // competition link to an already-seeded club without a LazyInitializationException —
    // the data loaders run outside an open-session-in-view web request.
    @Query("select t from Team t left join fetch t.leagues " +
            "where t.sport.id = :sportId and t.externalId = :externalId")
    Optional<Team> findBySportIdAndExternalIdWithLeagues(@Param("sportId") Long sportId,
                                                         @Param("externalId") String externalId);

    // Accent-insensitive partial name match — used by the global search feature.
    // Queries the pre-normalized (accent-stripped, lower-cased) name column so a
    // search for "Atletico" matches the stored "Atlético". The caller must pass an
    // already-normalized query (see TextNormalizer) since name_normalized holds the
    // normalized form on both sides of the comparison.
    // SQL: SELECT * FROM team WHERE name_normalized LIKE '%?%'
    List<Team> findByNameNormalizedContaining(String normalizedQuery);

    // All rows whose normalized name hasn't been populated yet (rows that pre-date the
    // name_normalized column). Used by the boot-time backfill to fill them in.
    List<Team> findByNameNormalizedIsNull();
}
