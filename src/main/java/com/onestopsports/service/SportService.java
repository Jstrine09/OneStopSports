package com.onestopsports.service;

import com.onestopsports.dto.SportDto;
import com.onestopsports.model.Sport;
import com.onestopsports.repository.SportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Handles all business logic for Sports.
// Controllers call this service — the service calls the repository to talk to the database.
@Service // Marks this as a Spring-managed service so it can be injected into controllers
public class SportService {

    private final SportRepository sportRepository;

    // Spring automatically injects SportRepository here (constructor injection)
    public SportService(SportRepository sportRepository) {
        this.sportRepository = sportRepository;
    }

    // Returns all sports in the database as DTOs.
    // Called by GET /api/sports — the frontend uses this to build the sport selector.
    public List<SportDto> getAllSports() {
        return sportRepository.findAll().stream()
                .map(this::toDto) // Convert each Sport entity to a SportDto
                .toList();
    }

    // Looks up a sport by its slug (e.g. "football").
    // Throws a 404 if no sport with that slug exists.
    public SportDto getSportBySlug(String slug) {
        return sportRepository.findBySlug(slug)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sport not found: " + slug));
    }

    // Converts a Sport database entity into a SportDto for sending to the frontend.
    // We do this conversion manually to keep full control over what data is exposed.
    private SportDto toDto(Sport sport) {
        return new SportDto(sport.getId(), sport.getName(), sport.getSlug(), sport.getIconUrl());
    }
}
