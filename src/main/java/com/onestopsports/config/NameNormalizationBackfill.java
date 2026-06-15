package com.onestopsports.config;

import com.onestopsports.model.Player;
import com.onestopsports.model.Team;
import com.onestopsports.repository.PlayerRepository;
import com.onestopsports.repository.TeamRepository;
import com.onestopsports.util.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// One-time, self-healing backfill for the name_normalized column added in V8.
//
// New rows get their normalized name automatically via the @PrePersist hook on the
// Team/Player entities. But rows that already existed when the column was added start
// out null, and pure SQL can't accent-strip them in the migration. This runner finds
// those null rows on startup and populates them so search works for the existing data.
//
// It's safe to run every boot: once every row has a normalized name there's nothing to
// do and it returns immediately. @Order picks a high value so this runs after the
// sport data loaders have finished seeding (their rows are already normalized by the
// @PrePersist hook, so this is really only for data that predates the column).
@Component
@Order(100)
public class NameNormalizationBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NameNormalizationBackfill.class);

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    public NameNormalizationBackfill(TeamRepository teamRepository, PlayerRepository playerRepository) {
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Teams missing a normalized name — compute it from `name` and save.
        List<Team> teams = teamRepository.findByNameNormalizedIsNull();
        for (Team team : teams) {
            team.setNameNormalized(TextNormalizer.normalize(team.getName()));
        }
        if (!teams.isEmpty()) {
            teamRepository.saveAll(teams);
            log.info("[NameNormalizationBackfill] Backfilled name_normalized for {} team(s)", teams.size());
        }

        // Players missing a normalized name — same treatment.
        List<Player> players = playerRepository.findByNameNormalizedIsNull();
        for (Player player : players) {
            player.setNameNormalized(TextNormalizer.normalize(player.getName()));
        }
        if (!players.isEmpty()) {
            playerRepository.saveAll(players);
            log.info("[NameNormalizationBackfill] Backfilled name_normalized for {} player(s)", players.size());
        }
    }
}
