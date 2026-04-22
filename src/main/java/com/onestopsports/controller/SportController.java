package com.onestopsports.controller;

import com.onestopsports.dto.LeagueDto;
import com.onestopsports.dto.SportDto;
import com.onestopsports.service.LeagueService;
import com.onestopsports.service.SportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sports")
public class SportController {

    private final SportService sportService;
    private final LeagueService leagueService;

    public SportController(SportService sportService, LeagueService leagueService) {
        this.sportService = sportService;
        this.leagueService = leagueService;
    }

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
