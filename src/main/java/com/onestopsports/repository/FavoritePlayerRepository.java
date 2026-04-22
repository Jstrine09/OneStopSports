package com.onestopsports.repository;

import com.onestopsports.model.FavoritePlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FavoritePlayerRepository extends JpaRepository<FavoritePlayer, Long> {
    List<FavoritePlayer> findByUserId(Long userId);
    boolean existsByUserIdAndPlayerId(Long userId, Long playerId);

    @Modifying
    @Transactional
    void deleteByUserIdAndPlayerId(Long userId, Long playerId);
}
