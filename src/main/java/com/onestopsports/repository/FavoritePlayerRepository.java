package com.onestopsports.repository;

import com.onestopsports.model.FavoritePlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Handles all database operations for the FavoritePlayer table (the user ↔ player favourites).
public interface FavoritePlayerRepository extends JpaRepository<FavoritePlayer, Long> {

    // Gets all of a user's favourited players.
    // SQL: SELECT * FROM favorite_player WHERE user_id = ?
    List<FavoritePlayer> findByUserId(Long userId);

    // Quick check to avoid saving duplicate favourites.
    // SQL: SELECT EXISTS(SELECT 1 FROM favorite_player WHERE user_id = ? AND player_id = ?)
    boolean existsByUserIdAndPlayerId(Long userId, Long playerId);

    // Removes a player from a user's favourites.
    // SQL: DELETE FROM favorite_player WHERE user_id = ? AND player_id = ?
    @Modifying
    @Transactional
    void deleteByUserIdAndPlayerId(Long userId, Long playerId);
}
