package com.onestopsports.repository;

import com.onestopsports.model.FavoriteTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FavoriteTeamRepository extends JpaRepository<FavoriteTeam, Long> {
    List<FavoriteTeam> findByUserId(Long userId);
    boolean existsByUserIdAndTeamId(Long userId, Long teamId);

    @Modifying
    @Transactional
    void deleteByUserIdAndTeamId(Long userId, Long teamId);
}
