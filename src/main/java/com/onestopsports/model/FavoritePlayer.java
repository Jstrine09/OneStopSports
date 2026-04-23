package com.onestopsports.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Same idea as FavoriteTeam — records which players a user has favourited.
// Each row means "this user has starred this player".
@Entity
@Table(
    name = "favorite_player",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "player_id"}) // Prevents duplicate favourites
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoritePlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user who favourited the player
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    // The player that was favourited
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt; // When the user starred this player
}
