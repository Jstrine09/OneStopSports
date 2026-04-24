package com.onestopsports.repository;

import com.onestopsports.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Handles all database operations for the UserAccount table.
public interface UserRepository extends JpaRepository<UserAccount, Long> {

    // Used during login and when loading a user from a JWT token.
    // SQL: SELECT * FROM user_account WHERE username = ?
    Optional<UserAccount> findByUsername(String username);

    // Used during registration to check if an email is already taken.
    // SQL: SELECT * FROM user_account WHERE email = ?
    Optional<UserAccount> findByEmail(String email);
}
