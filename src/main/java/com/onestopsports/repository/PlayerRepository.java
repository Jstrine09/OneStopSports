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

    // True if any player on this team is missing their external-API ID. Used by the
    // NBA / NFL data loaders to spot rosters that pre-date the V6 migration so they
    // can be wiped and re-fetched with externalIds populated.
    // SQL: SELECT EXISTS(SELECT 1 FROM player WHERE team_id = ? AND external_id IS NULL)
    boolean existsByTeamIdAndExternalIdIsNull(Long teamId);

    // Accent-insensitive partial name match — used by the global search feature.
    // Queries the pre-normalized (accent-stripped, lower-cased) name column so a
    // search for "Dembele" matches the stored "Dembélé". The caller must pass an
    // already-normalized query (see TextNormalizer).
    // SQL: SELECT * FROM player WHERE name_normalized LIKE '%?%'
    List<Player> findByNameNormalizedContaining(String normalizedQuery);

    // All rows whose normalized name hasn't been populated yet (rows that pre-date the
    // name_normalized column). Used by the boot-time backfill to fill them in.
    List<Player> findByNameNormalizedIsNull();
}
