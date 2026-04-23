package com.onestopsports.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// A League belongs to a Sport and contains Teams.
// e.g. "Premier League" is a league under "Football".
// Each league has an externalId that maps it to football-data.org's competition IDs
// so we know which API endpoint to call for live matches and standings.
@Entity
@Table(name = "league")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which sport this league belongs to (Football, Basketball, etc.)
    // LAZY means we don't load the Sport object from the DB until we actually need it
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sport_id", nullable = false) // Foreign key column in the "league" table
    private Sport sport;

    /** football-data.org competition ID — e.g. 2021 = Premier League, 2001 = Champions League */
    private Integer externalId;

    @Column(nullable = false)
    private String name;    // e.g. "Premier League"

    private String country; // e.g. "England"
    private String logoUrl; // URL to the league's logo image
    private String season;  // e.g. "2024/25"

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
