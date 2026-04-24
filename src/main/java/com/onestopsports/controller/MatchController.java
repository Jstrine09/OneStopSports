package com.onestopsports.controller;

import com.onestopsports.dto.MatchDto;
import com.onestopsports.dto.MatchEventDto;
import com.onestopsports.service.MatchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Handles HTTP requests for matches — live scores, date-filtered matches, and match details.
@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    // GET /api/matches?league=1&date=2024-04-23
    // Returns all matches for a specific league on a specific date.
    // Both parameters are optional — if omitted, an empty list is returned.
    // @DateTimeFormat(iso = ...) tells Spring how to parse the date string from the URL.
    @GetMapping
    public ResponseEntity<List<MatchDto>> getMatches(
            @RequestParam(required = false) Long league,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(matchService.getMatchesByLeagueAndDate(league, date));
    }

    // GET /api/matches/live
    // Returns all currently live matches.
    // This result is cached in Redis for 30 seconds to avoid hitting the API on every request.
    @GetMapping("/live")
    public ResponseEntity<List<MatchDto>> getLiveMatches() {
        return ResponseEntity.ok(matchService.getLiveMatches());
    }

    // GET /api/matches/{id}
    // Returns the full detail for a single match — teams, score, status, kick-off time.
    // Used as a fallback when someone opens a match URL directly rather than tapping
    // a match card (where the data is already passed via router state in the frontend).
    @GetMapping("/{id}")
    public ResponseEntity<MatchDto> getMatch(@PathVariable Long id) {
        return ResponseEntity.ok(matchService.getMatchById(id));
    }

    // GET /api/matches/{id}/events
    // Returns all goals, cards, and substitutions for a match.
    // Used to display the events timeline on MatchDetailPage.
    @GetMapping("/{id}/events")
    public ResponseEntity<List<MatchEventDto>> getMatchEvents(@PathVariable Long id) {
        return ResponseEntity.ok(matchService.getMatchEvents(id));
    }

    // GET /api/matches/{id}/stats
    // Match stats (possession, shots on target, etc.) are not available on the free API tier.
    // Returns an empty map — shown as "coming soon" in the frontend.
    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> getMatchStats(@PathVariable Long id) {
        return ResponseEntity.ok(matchService.getMatchStats(id));
    }

    // GET /api/matches/{id}/lineups
    // Lineups are also not available on the free API tier.
    // Returns an empty map — shown as "coming soon" in the frontend.
    @GetMapping("/{id}/lineups")
    public ResponseEntity<Map<String, Object>> getMatchLineups(@PathVariable Long id) {
        return ResponseEntity.ok(matchService.getMatchLineups(id));
    }
}
