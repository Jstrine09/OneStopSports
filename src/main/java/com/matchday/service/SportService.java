package com.matchday.service;

import com.matchday.dto.SportDto;
import com.matchday.model.Sport;
import com.matchday.repository.SportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SportService {

    private final SportRepository sportRepository;

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
