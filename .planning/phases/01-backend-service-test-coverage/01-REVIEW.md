---
phase: 01-backend-service-test-coverage
reviewed: 2026-07-08T00:00:00Z
depth: standard
files_reviewed: 12
files_reviewed_list:
  - src/main/java/com/onestopsports/service/ApiFootballService.java
  - src/main/java/com/onestopsports/service/BallDontLieService.java
  - src/main/java/com/onestopsports/service/ExternalApiService.java
  - src/main/java/com/onestopsports/service/NflApiService.java
  - src/test/java/com/onestopsports/controller/GlobalExceptionHandlerTest.java
  - src/test/java/com/onestopsports/service/ApiFootballServiceTest.java
  - src/test/java/com/onestopsports/service/BallDontLieServiceTest.java
  - src/test/java/com/onestopsports/service/ExternalApiServiceTest.java
  - src/test/java/com/onestopsports/service/NflApiServiceTest.java
  - src/test/java/com/onestopsports/service/PlayerServiceTest.java
  - src/test/java/com/onestopsports/service/SportServiceTest.java
  - src/test/java/com/onestopsports/service/UserServiceTest.java
findings:
  critical: 0
  warning: 4
  info: 3
  total: 7
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-07-08T00:00:00Z
**Depth:** standard
**Files Reviewed:** 12
**Status:** issues_found

## Summary

This phase's production changes are exactly what they claim to be: each of the four external-API services (`ApiFootballService`, `BallDontLieService`, `ExternalApiService`, `NflApiService`) gained a package-private test-only constructor plus an `@Autowired` annotation on the original production constructor to disambiguate Spring's constructor selection, and `BallDontLieService`'s two inner response records were widened from `private` to package-private so the new test class can build fixtures directly. I traced every test-seam change against its corresponding test file: constructor parameter counts, order, and types all line up correctly, and the `@Autowired` disambiguation is necessary and correctly applied (Spring cannot pick a constructor automatically once there is more than one candidate). No production behavior was altered and no visibility was over-widened beyond what the tests actually require.

The eight new test classes are mostly genuine — they build real (not degenerate) API response fixtures, stub the RestClient deep-stub chain with the correct method-overload matchers for each production call site, and assert on concrete mapped values (e.g. `ApiFootballServiceTest.fetchPlayerStats_happyPath` checks every column of the mapped stat row; `ExternalApiServiceTest` correctly distinguishes the `TOTAL` vs `HOME`/`AWAY` standings-group filter). I did not find any vacuous/tautological assertions (e.g. asserting a mock returns what it was told to return with no real service logic in between).

That said, two things fall short of "clean": (1) one latent bug surfaced while tracing `NflApiService.fetchStandings` — an unguarded null-dereference path that the new tests don't exercise and therefore didn't catch; and (2) coverage is noticeably uneven — the trickiest, most bug-prone logic in `ExternalApiService` (the own-goal/card box-score derivation) and `NflApiService` (`fetchBoxScore`'s position-group flattening) has zero test coverage, while several simpler methods are well tested. Neither of these blocks shipping, but both should be addressed before calling backend service coverage "done."

## Warnings

### WR-01: `NflApiService.fetchStandings` can throw an uncaught NPE that breaks the documented soft-fail contract

**File:** `src/main/java/com/onestopsports/service/NflApiService.java:472` and `:687-690`
**Issue:** Inside the `byDivision` grouping loop, the code explicitly guards against a null team on a standings entry:
```java
String abbr = entry.team() != null ? entry.team().abbreviation() : null;
```
But a few lines later, in the same `try` block, `toStandingsEntryDto(entry, ...)` is called for every entry, and that method dereferences `entry.team()` unconditionally:
```java
EspnStandingsTeam t = entry.team();
String crestUrl = (t.logos() != null && !t.logos().isEmpty()) ? t.logos().get(0).href() : null;
```
If any `EspnStandingsEntry` in ESPN's (unofficial, "structure could change without warning" per the class's own header comment) response has a null `team`, this throws a `NullPointerException`. `fetchStandings`'s enclosing `try` only catches `RestClientException` (line 507), not the broader `Exception` — so the NPE propagates out of the service, past `NflApiService`, and is only caught by `GlobalExceptionHandler`'s generic 500 handler. Every other soft-fail path in this same class (`fetchGameDtosByDate`'s null-body guard, `fetchCareerStats`'s `RestClientException` catch) is designed specifically to avoid ever surfacing a 500 to the client for an upstream data hiccup — this path breaks that contract. The new `NflApiServiceTest` doesn't exercise a null-team entry, so this wasn't caught.
**Fix:** Null-guard the same way the grouping loop does, and widen the catch (or add a defensive null check) so a malformed entry degrades gracefully instead of 500ing the whole standings request:
```java
private StandingsEntryDto toStandingsEntryDto(EspnStandingsEntry entry, Long dbLeagueId,
                                               int rank, String conference, String division,
                                               double leaderWins, double leaderLosses) {
    EspnStandingsTeam t = entry.team();
    if (t == null) {
        return new StandingsEntryDto(rank, new TeamDto(null, "TBD", "TBD", null, null, null, dbLeagueId),
                0, 0, 0, 0, 0, 0, 0, conference, division, 0.0, 0.0);
    }
    ...
```

### WR-02: `BallDontLieService.searchPlayerByName` catches generic `Exception`, unlike its three sibling services

**File:** `src/main/java/com/onestopsports/service/BallDontLieService.java:117`
**Issue:** `ApiFootballService`, `ExternalApiService`, and `NflApiService` all narrow their soft-fail catches to `RestClientException` — the specific, expected failure mode for an upstream HTTP call. `BallDontLieService.searchPlayerByName` instead does `catch (Exception e)`, which also silently swallows genuine programming bugs in the surrounding mapping code (e.g. an `ArrayIndexOutOfBoundsException` from the name-splitting logic, or an NPE from a future refactor) and logs them at `WARN` with the same message shape as a legitimate rate-limit/network failure ("balldontlie player search failed for..."). That makes a real bug indistinguishable from an expected external-API hiccup in production logs, and the new `BallDontLieServiceTest` only exercises the `RestClientException` path, so a masked-bug scenario isn't covered either.
**Fix:** Narrow to `RestClientException` (and let unexpected `RuntimeException`s propagate, matching the pattern in the other three services), or if a broader catch is intentionally desired here, split it so unexpected exceptions are logged at `ERROR` with a distinct message rather than folded into the same `WARN` line as rate-limiting.

### WR-03: `ExternalApiServiceTest` skips the class's trickiest, most bug-prone logic

**File:** `src/test/java/com/onestopsports/service/ExternalApiServiceTest.java`
**Issue:** The test class covers `fetchStandings`'s group-filtering and the `RestClientException`/null-body guards on `fetchFootballBoxScore`, but never asserts on `fetchFootballBoxScore`'s actual happy-path output. That method's own goal/card-attribution logic is explicitly called out in the production code's comments as needing care ("Own goals count for the OPPOSING team... that's already how football-data.org labels the team on an own goal") — exactly the kind of subtle, easy-to-get-backwards logic a test suite exists to pin down, and it's untested. The class's four `toMatchDto`/`toMatchDtoFromDetail` mapping methods, `fetchLiveMatchDtos`, and `fetchMatchEventDtos` (goal/booking/substitution merging + minute-sort) are also entirely untested — none of `fetchStandings`'s sibling data-shaping methods have any coverage at all.
**Fix:** Add at least one happy-path test for `fetchFootballBoxScore` that includes an own-goal entry and asserts it's credited to the correct (opposing) team's goal tally, plus a basic happy-path test for `toMatchDto`/`fetchMatchEventDtos` covering the minute-sort and event-type mapping.

### WR-04: `NflApiServiceTest` never exercises `fetchBoxScore`, and the standings happy-path test doesn't cover games-behind/tie-breaking math

**File:** `src/test/java/com/onestopsports/service/NflApiServiceTest.java`
**Issue:** `fetchBoxScore` (ESPN `/summary` → `BoxScoreDto`, including the multi-position-group flattening described in the production code's own comments as needing "the first non-empty table's columns") has zero test coverage — not even the `RestClientException`/null-body soft-fail paths that every other method in this class gets tested. Separately, `fetchStandings_twoConferences_mapsRankedEntriesWithConferenceAndDivision` only puts one team in each conference/division, so it can never exercise the games-behind calculation, the win-loss tie-break (`thenComparingDouble` on losses), or the `DIVISION_BY_ABBR.getOrDefault(abbr, "Unknown")` fallback branch — all real logic that a "two teams per division" fixture would trivially cover.
**Fix:** Add a `fetchBoxScore` happy-path + soft-fail test pair (mirroring the pattern already used for `fetchStandings`/`fetchCareerStats` in this same file), and extend the standings fixture to two teams in the same division to assert `gamesBehind` and rank ordering.

## Info

### IN-01: Unused import `java.util.Collections` in `ApiFootballService.java`

**File:** `src/main/java/com/onestopsports/service/ApiFootballService.java:15`
**Issue:** `Collections` is imported but never referenced anywhere in the file (verified via grep — the only occurrence is the import line itself). Dead import, no functional effect.
**Fix:** Remove the unused import.

### IN-02: `ApiFootballServiceTest` doesn't cover the package-private `currentSeason()`/`mapLeagueId()` helpers

**File:** `src/test/java/com/onestopsports/service/ApiFootballServiceTest.java`
**Issue:** Both helpers contain real conditional logic — `currentSeason()`'s free-tier clamp to `FREE_TIER_MAX_SEASON` (2024) and the July-rollover month check, and `mapLeagueId()`'s lookup table plus null-passthrough — and both are package-private, so they're trivially callable from this same-package test class without any additional seam. Neither is exercised at all.
**Fix:** Add direct unit tests, e.g. asserting `mapLeagueId(2021)` returns `39` and `mapLeagueId(9999)` returns `null`, and that `currentSeason()` never exceeds `2024` regardless of `LocalDate.now()`.

### IN-03: `GlobalExceptionHandlerTest` doesn't cover `handleValidation`/`handleTypeMismatch`

**File:** `src/test/java/com/onestopsports/controller/GlobalExceptionHandlerTest.java`
**Issue:** Of the ten `@ExceptionHandler` methods in `GlobalExceptionHandler`, this test covers eight (including both dispatch-order-sensitive cases). `handleValidation` (`MethodArgumentNotValidException`, triggered by `@Valid` request-body failures) and `handleTypeMismatch` (`MethodArgumentTypeMismatchException`, e.g. `GET /players/abc` — explicitly called out as a documented scenario) are untested. These are direct-call-style tests like the ones already present for `handleMissingParam`/`handleUnreadable`, so the gap isn't a construction-difficulty issue (a `BindingResult` mock plus `new MethodArgumentNotValidException(...)` is standard), just an omission.
**Fix:** Add direct-call tests for both handlers following the existing pattern in this file.

---

_Reviewed: 2026-07-08T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
