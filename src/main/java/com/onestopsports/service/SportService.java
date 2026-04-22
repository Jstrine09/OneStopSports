package com.onestopsports.service;

import com.onestopsports.dto.SportDto;
import com.onestopsports.model.Sport;
import com.onestopsports.repository.SportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SportService {

    private final SportRepository sportRepository;

    public SportService(SportRepository sportRepository) {
        this.sportRepository = sportRepository;
    }

    public List<SportDto> getAllSports() {
        return sportRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public SportDto getSportBySlug(String slug) {
        return sportRepository.findBySlug(slug)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sport not found: " + slug));
    }

    private SportDto toDto(Sport sport) {
        return new SportDto(sport.getId(), sport.getName(), sport.getSlug(), sport.getIconUrl());
    }
}
