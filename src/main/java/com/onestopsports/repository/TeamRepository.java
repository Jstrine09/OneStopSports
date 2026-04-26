package com.onestopsports.repository;

import com.onestopsports.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Handles all database operations for the Team table.
public interface TeamRepository extends JpaRepository<Team, Long> {

    // Finds all teams in a specific league.
    // SQL: SELECT * FROM team WHERE league_id = ?
    List<Team> findByLeagueId(Long leagueId);

    // Case-insensitive partial name match — used by the global search feature.
    // SQL: SELECT * FROM team WHERE LOWER(name) LIKE LOWER('%?%')
    List<Team> findByNameContainingIgnoreCase(String query);
}
