package com.onestopsports.repository;

import com.onestopsports.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Handles all database operations for the Team table.
public interface TeamRepository extends JpaRepository<Team, Long> {

    // Finds all teams in a specific league.
    // SQL: SELECT * FROM team WHERE league_id = ?
    List<Team> findByLeagueId(Long leagueId);

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
