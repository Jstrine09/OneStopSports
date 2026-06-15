package com.onestopsports.model;

import com.onestopsports.util.TextNormalizer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// A Team belongs to a League and has Players.
// e.g. "Manchester City" is a team in the "Premier League".
// Team data is seeded from football-data.org on first startup.
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

    // Which league this team plays in
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id", nullable = false) // Foreign key column in the "team" table
    private League league;

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
}
