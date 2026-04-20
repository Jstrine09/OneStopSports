package com.matchday.service;

import com.matchday.dto.MatchDto;
import com.matchday.dto.StandingsEntryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExternalApiService {

    private final WebClient.Builder webClientBuilder;

    @Value("${external-api.football-data.base-url}")
    private String baseUrl;

    @Value("${external-api.football-data.api-key}")
    private String apiKey;

    public List<MatchDto> fetchLiveMatches() {
        // TODO: implement GET {baseUrl}/matches?status=LIVE with X-Auth-Token header
        return Collections.emptyList();
    }

    public List<StandingsEntryDto> fetchStandings(Long leagueId) {
        // TODO: implement GET {baseUrl}/competitions/{leagueId}/standings
        return Collections.emptyList();
    }

    @Scheduled(fixedDelay = 30_000)
    public void refreshLiveMatchCache() {
        // TODO: call fetchLiveMatches(), push updates via SimpMessagingTemplate
        //       to /topic/matches/live when scores change
    }
}
