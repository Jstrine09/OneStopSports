package com.matchday.controller;

import com.matchday.dto.LeagueDto;
import com.matchday.dto.SportDto;
import com.matchday.service.LeagueService;
import com.matchday.service.SportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sports")
@RequiredArgsConstructor
public class SportController {

    private final SportService sportService;
    private final LeagueService leagueService;

    @GetMapping
    public ResponseEntity<List<SportDto>> getAllSports() {
        return ResponseEntity.ok(sportService.getAllSports());
    }

    @GetMapping("/{slug}/leagues")
    public ResponseEntity<List<LeagueDto>> getLeaguesBySport(@PathVariable String slug) {
        SportDto sport = sportService.getSportBySlug(slug);
        return ResponseEntity.ok(leagueService.getLeaguesBySport(sport.id()));
    }
}
