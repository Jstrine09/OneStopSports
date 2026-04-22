package com.onestopsports.repository;

import com.onestopsports.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByLeagueId(Long leagueId);
}
