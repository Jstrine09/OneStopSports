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

// Handles HTTP requests related to Sports.
// @RestController means every method automatically serialises its return value to JSON.
// @RequestMapping("/api/sports") means all endpoints in this class start with /api/sports
@RestController
@RequestMapping("/api/sports")
public class SportController {

    private final SportService sportService;
    private final LeagueService leagueService;

    public SportController(SportService sportService, LeagueService leagueService) {
        this.sportService = sportService;
        this.leagueService = leagueService;
    }

    // GET /api/sports
    // Returns all sports in the database (e.g. Football).
    // The frontend calls this at startup to know which sports exist and build the navigation.
    @GetMapping
    public ResponseEntity<List<SportDto>> getAllSports() {
        return ResponseEntity.ok(sportService.getAllSports());
    }

    // GET /api/sports/football/leagues
    // Returns all leagues for a given sport (identified by its slug, e.g. "football").
    // First looks up the sport by slug to get its ID, then fetches leagues for that sport.
    @GetMapping("/{slug}/leagues")
    public ResponseEntity<List<LeagueDto>> getLeaguesBySport(@PathVariable String slug) {
        SportDto sport = sportService.getSportBySlug(slug); // Throws 404 if slug doesn't match a sport
        return ResponseEntity.ok(leagueService.getLeaguesBySport(sport.id()));
    }
}
