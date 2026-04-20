package com.matchday.repository;

import com.matchday.model.League;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeagueRepository extends JpaRepository<League, Long> {
    List<League> findBySportId(Long sportId);
}
