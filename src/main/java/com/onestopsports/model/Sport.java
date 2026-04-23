package com.onestopsports.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// A Sport is the top-level category — e.g. "Football", "Basketball".
// Everything else (leagues, teams, players) belongs to a sport.
// Currently only Football is seeded, but the architecture supports adding more.
@Entity                 // Marks this as a database table row
@Table(name = "sport")  // Maps to the "sport" table in PostgreSQL
@Getter                 // Lombok: auto-generates all getters (getSport(), etc.)
@Setter                 // Lombok: auto-generates all setters
@Builder                // Lombok: allows the builder pattern — Sport.builder().name("Football").build()
@NoArgsConstructor      // Lombok: generates a no-args constructor (required by JPA)
@AllArgsConstructor     // Lombok: generates a constructor with all fields (used by @Builder)
public class Sport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB auto-increments the ID (1, 2, 3...)
    private Long id;

    @Column(nullable = false) // This column cannot be NULL in the database
    private String name;      // e.g. "Football"

    @Column(unique = true, nullable = false) // URL-friendly identifier, must be unique
    private String slug;                     // e.g. "football"

    private String iconUrl; // URL to the sport's icon image

    @CreationTimestamp              // Hibernate automatically sets this to the current time when created
    @Column(updatable = false)      // Once set, this value can never be updated
    private LocalDateTime createdAt;
}
