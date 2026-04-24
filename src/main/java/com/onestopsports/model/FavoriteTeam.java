package com.onestopsports.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// This is a join table that records which teams a user has favourited.
// Each row means "this user has starred this team".
// The unique constraint makes sure the same user can't favourite the same team twice.
@Entity
@Table(
    name = "favorite_team",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "team_id"}) // Prevents duplicate favourites
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user who favourited the team
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    // The team that was favourited
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt; // When the user starred this team
}
