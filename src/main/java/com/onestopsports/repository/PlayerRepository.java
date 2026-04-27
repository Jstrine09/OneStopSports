package com.onestopsports.repository;

import com.onestopsports.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Handles all database operations for the Player table.
public interface PlayerRepository extends JpaRepository<Player, Long> {

    // Finds all players on a specific team — used to load a team's squad.
    // SQL: SELECT * FROM player WHERE team_id = ?
    List<Player> findByTeamId(Long teamId);

    // Counts how many players are on a specific team — used by data loaders to check
    // whether a team has already been seeded, so we can skip or re-seed as needed.
    // SQL: SELECT COUNT(*) FROM player WHERE team_id = ?
    long countByTeamId(Long teamId);

    // Case-insensitive partial name match — used by the global search feature.
    // SQL: SELECT * FROM player WHERE LOWER(name) LIKE LOWER('%?%')
    List<Player> findByNameContainingIgnoreCase(String query);
}
