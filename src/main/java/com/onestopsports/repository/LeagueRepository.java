package com.onestopsports.repository;

import com.onestopsports.model.League;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Handles all database operations for the League table.
public interface LeagueRepository extends JpaRepository<League, Long> {

    // Finds all leagues that belong to a specific sport.
    // SQL: SELECT * FROM league WHERE sport_id = ?
    List<League> findBySportId(Long sportId);

    // Finds a league by its football-data.org competition ID.
    // Used by the DataLoader to check if a league is already seeded,
    // and by ExternalApiService to map API match data back to our DB league IDs.
    // SQL: SELECT * FROM league WHERE external_id = ?
    Optional<League> findByExternalId(Integer externalId);

    // Finds all leagues belonging to a sport identified by its slug.
    // The underscore in Sport_Slug explicitly tells Spring Data to traverse the
    // sport relationship and then match on the slug field.
    // SQL equivalent: SELECT l.* FROM league l JOIN sport s ON l.sport_id = s.id WHERE s.slug = ?
    List<League> findBySport_Slug(String sportSlug);
}
