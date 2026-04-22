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

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public ResponseEntity<List<MatchDto>> getMatches(
            @RequestParam(required = false) Long league,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(matchService.getMatchesByLeagueAndDate(league, date));
    }

    @GetMapping("/live")
    public ResponseEntity<List<MatchDto>> getLiveMatches() {
        return ResponseEntity.ok(matchService.getLiveMatches());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchDto> getMatch(@PathVariable Long id) {
        return ResponseEntity.ok(matchService.getMatchById(id));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<MatchEventDto>> getMatchEvents(@PathVariable Long id) {
        return ResponseEntity.ok(matchService.getMatchEvents(id));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> getMatchStats(@PathVariable Long id) {
        return ResponseEntity.ok(matchService.getMatchStats(id));
    }

    @GetMapping("/{id}/lineups")
    public ResponseEntity<Map<String, Object>> getMatchLineups(@PathVariable Long id) {
        return ResponseEntity.ok(matchService.getMatchLineups(id));
    }
}
