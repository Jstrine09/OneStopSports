package com.onestopsports.repository;

import com.onestopsports.model.FavoriteTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Handles all database operations for the FavoriteTeam table (the user ↔ team favourites).
public interface FavoriteTeamRepository extends JpaRepository<FavoriteTeam, Long> {

    // Gets all of a user's favourited teams.
    // SQL: SELECT * FROM favorite_team WHERE user_id = ?
    List<FavoriteTeam> findByUserId(Long userId);

    // Quick check to see if a user has already favourited a specific team.
    // Used to avoid saving duplicate favourites.
    // SQL: SELECT EXISTS(SELECT 1 FROM favorite_team WHERE user_id = ? AND team_id = ?)
    boolean existsByUserIdAndTeamId(Long userId, Long teamId);

    // Removes a team from a user's favourites.
    // @Modifying means this is a write operation (not just a SELECT).
    // @Transactional ensures it runs in a database transaction so it either fully succeeds or fully rolls back.
    // SQL: DELETE FROM favorite_team WHERE user_id = ? AND team_id = ?
    @Modifying
    @Transactional
    void deleteByUserIdAndTeamId(Long userId, Long teamId);
}
