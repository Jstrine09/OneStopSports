package com.onestopsports.dto;

// Represents a single event that happened during a match — a goal, card, or substitution.
// These are fetched from football-data.org's match detail endpoint and shown
// in the timeline on the MatchDetailPage.
public record MatchEventDto(
        String type,          // What happened: "GOAL" | "OWN_GOAL" | "PENALTY" | "YELLOW_CARD" | "RED_CARD" | "YELLOW_RED_CARD" | "SUBSTITUTION"
        Integer minute,       // Which minute of the match this happened (e.g. 45)
        Integer injuryMinute, // Extra time added on — shown as e.g. "45+2'" (null if not in injury time)
        String playerName,    // The main player involved: scorer, booked player, or player coming OFF
        String assistName,    // For goals: who assisted. For substitutions: player coming ON
        String teamName       // Which team the event belongs to (shown on the right of the timeline)
) {}
