package com.onestopsports.dto;

public record MatchEventDto(
        String type,          // "GOAL" | "OWN_GOAL" | "PENALTY" | "YELLOW_CARD" | "RED_CARD" | "YELLOW_RED_CARD" | "SUBSTITUTION"
        Integer minute,
        Integer injuryMinute,
        String playerName,    // scorer / booked player / player coming off
        String assistName,    // goal assist, or player coming on (for SUBSTITUTION)
        String teamName
) {}
