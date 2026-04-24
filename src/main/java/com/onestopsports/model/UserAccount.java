package com.onestopsports.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Represents a registered user of the app.
// Note: the class is called "UserAccount" (not "User") because "user" is a reserved
// keyword in PostgreSQL — using it as a table name would cause a SQL error.
@Entity
@Table(name = "user_account") // Stored in the "user_account" table to avoid the SQL reserved word issue
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50) // Each username must be unique
    private String username;

    @Column(unique = true, nullable = false, length = 150) // Each email must be unique
    private String email;

    @Column(nullable = false)
    private String passwordHash; // The password is NEVER stored as plain text.
                                 // BCrypt hashes it first so even if the DB is leaked, passwords are safe.

    private String avatarUrl; // Optional profile picture (not currently used in the UI)

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
