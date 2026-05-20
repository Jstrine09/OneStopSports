package com.onestopsports.dto;

// Rich biographical data for an NBA player, sourced from the balldontlie.io API.
// These fields don't come from football-data.org or ESPN, so they live in a
// separate DTO rather than being bolted onto PlayerDto.
//
// All fields are nullable — balldontlie may not have data for every player
// (e.g. undrafted players have null draftYear/draftRound/draftNumber).
public record PlayerBioDto(
        String height,         // Height in feet-inches format — e.g. "6-8"  (null if unknown)
        Integer weightPounds,  // Weight in pounds — e.g. 210                (null if unknown)
        String college,        // College or university — e.g. "Duke"        (null if unknown / overseas)
        Integer draftYear,     // Year player was drafted — e.g. 2017        (null if undrafted)
        Integer draftRound,    // Round in which they were picked — 1 or 2   (null if undrafted)
        Integer draftNumber    // Overall pick number — e.g. 3               (null if undrafted)
) {}
