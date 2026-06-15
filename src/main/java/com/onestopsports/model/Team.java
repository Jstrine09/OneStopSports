package com.onestopsports.model;

import com.onestopsports.util.TextNormalizer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

// A Team (club/franchise) belongs to exactly one Sport and can take part in MANY
// leagues/competitions. e.g. "Real Madrid" plays in both La Liga and the Champions
// League — that's ONE Team row linked to two League rows, not two duplicate clubs.
// (Before this refactor a club was duplicated once per competition; the team↔league
// join table below is the structural fix for that.)
// Team data is seeded from football-data.org (football) and ESPN (NBA/NFL) on first startup.
@Entity
@Table(name = "team")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which sport this club belongs to. Stored directly on the team (rather than being
    // derived through a league) so sport routing — the switch on the slug in PlayerService
    // / TeamService — has a single unambiguous answer even when a club plays in several
    // competitions. A club's sport never changes, so this is safe to denormalize.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sport_id", nullable = false) // Foreign key column in the "team" table
    private Sport sport;

    // Every competition this club takes part in. Owning side of the team↔league
    // many-to-many — the `team_league` join table (created in migration V9) holds the
    // (team_id, league_id) pairs. LAZY: callers that only need the team header don't pay
    // to load the league list. Defaulted to an empty mutable set so the builder/addLeague
    // never hit a null collection.
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "team_league",
            joinColumns = @JoinColumn(name = "team_id"),
            inverseJoinColumns = @JoinColumn(name = "league_id")
    )
    private Set<League> leagues = new HashSet<>();

    @Column(nullable = false)
    private String name;      // Full name — e.g. "Manchester City FC"

    private String shortName; // Abbreviated name — e.g. "Man City"
    private String crestUrl;  // URL to the team's badge/crest image
    private String stadium;   // Stadium name — e.g. "Etihad Stadium"
    private String country;   // Country — e.g. "England"

    // Accent-stripped, lower-cased copy of `name`, kept in sync automatically by the
    // @PrePersist/@PreUpdate hook below. Search queries this column so "Atletico"
    // matches "Atlético". Nullable for rows created before this column existed —
    // the boot-time backfill (NameNormalizationBackfill) fills those in.
    @Column(name = "name_normalized")
    private String nameNormalized;

    // The upstream provider's team ID — stored so we can fetch historical rosters
    // without re-fetching the full team list at runtime.
    // NBA/NFL: ESPN string ID (e.g. "1"). Football: football-data.org integer as string.
    // Null for rows seeded before V7; data loaders backfill on next startup.
    @Column(name = "external_id", length = 50)
    private String externalId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // Runs automatically before every insert and update, so the normalized name can
    // never drift out of sync with `name` no matter which loader or service saved the
    // row. Centralising it here means callers never have to remember to set it.
    @PrePersist
    @PreUpdate
    private void syncNameNormalized() {
        this.nameNormalized = TextNormalizer.normalize(this.name);
    }

    // Links this club to a competition it takes part in. Idempotent — a Set ignores a
    // league it already contains — so a data loader can call this every time it sees the
    // club in a competition response without creating duplicate join rows.
    public void addLeague(League league) {
        if (this.leagues == null) {
            this.leagues = new HashSet<>();
        }
        this.leagues.add(league);
    }

    // The club's "primary" league: the competition it was first linked to, picked as the
    // one with the smallest id. Seed order guarantees domestic leagues (Premier League, La
    // Liga, …) are seeded before continental cups (the Champions League), so for a football
    // club this is its domestic league, and for an NBA/NFL franchise it's simply its only
    // league. Used wherever a single representative league is needed — the TeamDto header
    // and the API-Football stats lookup (which keys off the domestic competition).
    // Returns null only for a club not yet linked to any league (shouldn't happen in prod).
    public League getPrimaryLeague() {
        if (leagues == null || leagues.isEmpty()) {
            return null;
        }
        return leagues.stream()
                .min(Comparator.comparing(League::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }
}
