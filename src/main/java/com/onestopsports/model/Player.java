package com.onestopsports.model;

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

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
