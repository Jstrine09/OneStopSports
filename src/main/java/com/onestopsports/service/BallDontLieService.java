package com.onestopsports.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.onestopsports.dto.PlayerBioDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

// Talks to the balldontlie.io API (free tier) to fetch NBA player biographical data.
//
// Free tier gives us: /players and /teams (no stats — those are paid tiers).
// Rate limit: 5 requests per minute.
// Auth: "Authorization: {api-key}" header (NOT "Bearer {key}" — just the raw key).
//
// We call this lazily — only when a player detail page is opened — so the rate
// limit is rarely a concern in normal use.
@Service
public class BallDontLieService {

    private static final Logger log = LoggerFactory.getLogger(BallDontLieService.class);

    private final RestClient restClient;

    // Spring wires these two values from application.yml / application-local.yml.
    // The API key is gitignored — stored only in application-local.yml.
    public BallDontLieService(
            @Value("${external-api.balldontlie.base-url:https://api.balldontlie.io/v1}") String baseUrl,
            @Value("${external-api.balldontlie.api-key:}") String apiKey) {

        // Build a RestClient that automatically sends the API key on every request.
        // balldontlie uses a plain key in the Authorization header — no "Bearer" prefix.
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", apiKey)
                .build();
    }

    // Searches balldontlie for an NBA player matching the given full name.
    // Returns bio data if found, or empty if no match or the API fails.
    //
    // IMPORTANT — balldontlie's "search" param matches on FIRST NAME only, not
    // full name. Strategy: extract the first word as firstName, then from the
    // results pick the entry whose lastName matches the remaining words.
    // e.g. "Jaylen Brown" → search "Jaylen", pick result with lastName "Brown".
    //
    // Called by PlayerService.getPlayerBioById() for the /api/players/{id}/bio endpoint.
    public Optional<PlayerBioDto> searchPlayerByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        try {
            // Split "Jaylen Brown" → firstName="Jaylen", lastName="Brown"
            // For multi-word last names (e.g. "Shai Gilgeous-Alexander") lastName
            // would be "Gilgeous-Alexander" — balldontlie stores it as one token.
            String[] parts = name.trim().split("\\s+", 2);
            String firstName = parts[0];
            String lastName  = parts.length > 1 ? parts[1] : "";

            // Search by first name (balldontlie's search param matches first_name only)
            BdlPlayersResponse response = restClient.get()
                    .uri("/players?search={first}&per_page=10", firstName)
                    .retrieve()
                    .body(BdlPlayersResponse.class);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                return Optional.empty();
            }

            // Find the result whose last name matches ours (case-insensitive).
            // If no last-name match, fall back to the first result so we don't
            // silently miss single-name players (rare in the NBA).
            BdlPlayer best = response.data().stream()
                    .filter(p -> lastName.equalsIgnoreCase(p.lastName()))
                    .findFirst()
                    .orElse(lastName.isBlank() ? response.data().get(0) : null);

            if (best == null) {
                return Optional.empty(); // First name exists but no matching last name
            }

            // The API returns weight as a String (e.g. "223") — parse to Integer.
            // Null-safe: some players have no weight listed.
            Integer weight = null;
            if (best.weight() != null && !best.weight().isBlank()) {
                try {
                    weight = Integer.parseInt(best.weight().trim());
                } catch (NumberFormatException ignored) {
                    // Leave weight null if the value isn't a clean number
                }
            }

            return Optional.of(new PlayerBioDto(
                    best.height(),
                    weight,
                    best.college(),
                    best.draftYear(),
                    best.draftRound(),
                    best.draftNumber()
            ));

        } catch (Exception e) {
            // Log but don't crash — this is enrichment data, not core content.
            // If balldontlie is down or rate-limited, the bio section simply won't show.
            log.warn("balldontlie player search failed for '{}': {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Inner records that mirror balldontlie's JSON structure ────────────────
    // These are private — no other class needs to know about the external API shape.

    // Top-level response wrapper: { "data": [...], "meta": {...} }
    private record BdlPlayersResponse(
            List<BdlPlayer> data
    ) {}

    // One player entry from balldontlie.
    // Field names use @JsonProperty to map from balldontlie's snake_case JSON keys.
    private record BdlPlayer(
            Long id,

            @JsonProperty("first_name")
            String firstName,

            @JsonProperty("last_name")
            String lastName,

            String position,     // "F", "G", "C", "G-F", etc. (abbreviated)

            String height,       // "6-6" (feet-inches format)

            // NOTE: The API returns "weight" as a String (e.g. "223"), NOT "weight_pounds".
            // We parse it to Integer in searchPlayerByName() before constructing the DTO.
            String weight,

            @JsonProperty("jersey_number")
            String jerseyNumber,

            String college,      // "California", "Duke", null for overseas players

            String country,      // "USA", "France", etc.

            @JsonProperty("draft_year")
            Integer draftYear,

            @JsonProperty("draft_round")
            Integer draftRound,

            @JsonProperty("draft_number")
            Integer draftNumber
    ) {}
}
