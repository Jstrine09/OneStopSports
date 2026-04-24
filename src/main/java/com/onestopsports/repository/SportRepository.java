package com.onestopsports.repository;

import com.onestopsports.model.Sport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Handles all database operations for the Sport table.
// JpaRepository gives us save(), findById(), findAll(), delete(), count(), etc. for free.
// We only need to add methods for queries that aren't already built-in.
public interface SportRepository extends JpaRepository<Sport, Long> {

    // Spring generates the SQL automatically from the method name:
    // SELECT * FROM sport WHERE slug = ?
    Optional<Sport> findBySlug(String slug);
}
