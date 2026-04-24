package com.onestopsports.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// This class lives in its own file to avoid a circular dependency.
// SecurityConfig needs a PasswordEncoder, and AuthService also needs one.
// If PasswordEncoder was defined inside SecurityConfig, Spring would get confused
// because AuthService is indirectly referenced by SecurityConfig.
// Putting it here keeps everything cleanly separate.
@Configuration // Tells Spring this class contains beans (shared objects) to register
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt is a secure hashing algorithm designed for passwords.
        // It automatically adds a random "salt" so the same password always hashes differently.
        // When a user logs in, BCrypt hashes their input and compares it to the stored hash.
        return new BCryptPasswordEncoder();
    }
}
