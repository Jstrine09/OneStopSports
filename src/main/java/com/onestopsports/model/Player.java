package com.onestopsports.model;

import com.onestopsports.util.TextNormalizer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

// A Player belongs to a Team.
// Player data is pulled from football-data.org when the database is first seeded.
@Entity
@Table(name = "player")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which team this player is on
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false) // Foreign key column in the "player" table
    private Team team;

    @Column(nullable = false)
    private String name;         // Full name — e.g. "Erling Haaland"

    private String position;     // e.g. "Forward", "Midfielder", "Defender", "Goalkeeper"
    private String nationality;  // e.g. "Norway"
    private LocalDate dateOfBirth; // Used to calculate the player's age in the frontend
    private Integer jerseyNumber;  // The number on their shirt
    private String photoUrl;     // Player headshot (currently not populated from the API)

    // Accent-stripped, lower-cased copy of `name`, kept in sync automatically by the
    // @PrePersist/@PreUpdate hook below so search can match "Dembele" against "Dembélé".
    // Nullable for rows created before this column existed — NameNormalizationBackfill
    // fills those on startup.
    @Column(name = "name_normalized")
    private String nameNormalized;

    // External-API identifier used by the career-stats endpoints.
    // Meaning is sport-specific (see migration V6 for the full breakdown):
    //   • basketball / american-football → ESPN athlete ID  (set at seed time by NbaDataLoader / NflDataLoader)
    //   • football (soccer)              → API-Football player ID (set lazily on first stats lookup)
    // Stored as VARCHAR(64) for flexibility — ESPN IDs are numeric but other APIs use alphanumeric.
    @Column(name = "external_id", length = 64)
    private String externalId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // Runs automatically before every insert and update so the normalized name stays
    // in sync with `name` regardless of which loader or service persisted the row.
    @PrePersist
    @PreUpdate
    private void syncNameNormalized() {
        this.nameNormalized = TextNormalizer.normalize(this.name);
    }
}
