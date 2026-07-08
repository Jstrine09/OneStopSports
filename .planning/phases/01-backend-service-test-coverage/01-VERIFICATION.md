---
phase: 01-backend-service-test-coverage
verified: 2026-07-08T23:40:00Z
status: passed
score: 9/9 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification: false
---

# Phase 1: Backend Service Test Coverage Verification Report

**Phase Goal:** The backend services that currently have zero tests are covered by unit tests that mock their external providers, so their routing, mapping, and soft-fail behaviour is verified and regressions are caught by `mvn test`.
**Verified:** 2026-07-08T23:40:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | NflApiService + ExternalApiService: unit-tested happy-path mapping AND swallow-RestClientException soft-fail, no live network calls | ✓ VERIFIED | `NflApiServiceTest.java` (6 tests): scoreboard mapping (status/scores/timezone/leagueId), null-body guard, standings mapping (conference+division derivation), standings soft-fail→empty list, career-stats happy-path, career-stats soft-fail→null. `ExternalApiServiceTest.java` (5 tests): TOTAL-group mapping, non-TOTAL filter, standings soft-fail→empty list, box-score soft-fail on exception, box-score soft-fail on null body. Both use `@Mock(answer=RETURNS_DEEP_STUBS) RestClient` via the 01-01 test constructors — no `RestClient.builder()`/real HTTP anywhere in either file. |
| 2 | ApiFootballService + BallDontLieService: unit-tested happy-path mapping AND swallow-exception soft-fail, no live network calls | ✓ VERIFIED | `ApiFootballServiceTest.java` (7 tests): exact-name match, accent-insensitive match, too-short-name no-HTTP guard (`verifyNoInteractions`), RestClientException→`Optional.empty()`, FOOTBALL_LABELS stat mapping, empty-response→null, RestClientException→null. `BallDontLieServiceTest.java` (5 tests): first-name-search/last-name-match happy path with parsed integer weight, last-name mismatch→empty, blank/null guard (`verifyNoInteractions`), exception→empty. Fixtures built directly from the package-private `BdlPlayersResponse`/`BdlPlayer` records widened in 01-01 — no JSON/Jackson round-trip needed, proving that widening actually works. |
| 3 | UserService: 404 guards (missing user/team/player), skip-if-already-favourited guard, happy-path save/list/remove delegation | ✓ VERIFIED | `UserServiceTest.java` (11 tests): `getCurrentUser` 404 on unknown username (`ResponseStatusException`, "User not found"); `addFavoriteTeam`/`addFavoritePlayer` skip-if-exists (`verify(..., never()).save(...)` and `never()).findById(...)`); 404 on missing team/player; happy-path save (`verify(..., times(1)).save(...)`); `getFavoriteTeams`/`getFavoritePlayers` proven as pure delegation to `TeamService.toDto`/`PlayerService.toDto` (stubbed DTO flows through unchanged); `removeFavoriteTeam`/`removeFavoritePlayer` delegate to `deleteByUserIdAndTeamId`/`deleteByUserIdAndPlayerId` with the **resolved** user id. |
| 4 | SportService: getAllSports mapping + getSportBySlug happy path/404 | ✓ VERIFIED | `SportServiceTest.java` (4 tests): two-entity mapping preserving id/name/slug/iconUrl, empty-list case, slug happy path, 404 with "Sport not found" message. |
| 5 | GlobalExceptionHandler: documented status mappings AND ResponseStatusException-passthrough-precedes-catch-all proven via real Spring MVC dispatch (not direct method calls) | ✓ VERIFIED | `GlobalExceptionHandlerTest.java` (8 tests): two dispatch-order tests built via `MockMvcBuilders.standaloneSetup(new ThrowingTestController()).setControllerAdvice(new GlobalExceptionHandler())` — a thrown `ResponseStatusException(NOT_FOUND, "Player not found: 999")` dispatches to 404 with the passthrough message (not the catch-all), and a thrown raw `RuntimeException` dispatches to 500 with the generic message and explicitly asserts the raw exception text is NOT leaked. Six additional direct-call tests cover `handleBadCredentials` (401), `handleAccessDenied` (403), `handleMethodNotSupported` (405), `handleMissingParam` (400), `handleUnreadable` (400), `handleDataIntegrity` (409, raw SQL detail not leaked). |
| 6 | PlayerService.resolvePhotoUrl: persisted photoUrl wins → ESPN NBA CDN derivation → ESPN NFL CDN derivation → null (no externalId / unsupported sport) | ✓ VERIFIED | `PlayerServiceTest.java`: all four branches exercised through the public `getPlayerById` entry point — persisted URL wins over a basketball+externalId combo that COULD derive a CDN URL (Layer 1 short-circuit proven); basketball+externalId→`.../nba/players/full/{id}.png`; american-football+externalId→`.../nfl/players/full/{id}.png`; no-externalId/football sport→null; externalId present but unsupported sport→null. |
| 7 | PlayerService.toDto: full field mapping (id/name/position/nationality/dateOfBirth/jerseyNumber/photoUrl/teamId) | ✓ VERIFIED | `PlayerServiceTest.java#getPlayerById_mapsAllNonPhotoFieldsAndTeamId` asserts every scalar field plus the lazily-loaded team id. |
| 8 | PlayerService.searchPlayers: query normalization + delegation to findByNameNormalizedContaining + cap at 10 | ✓ VERIFIED | `PlayerServiceTest.java#searchPlayers_normalizesQueryAndCapsResultsAtTen` stubs the repository to return 12 results and asserts exactly 10 come back, with the stub keyed on `TextNormalizer.normalize("Dembélé")` (proving the query is normalized before the repository call); `searchPlayers_noMatches_returnsEmptyList` covers the zero-hit case. |
| 9 | Full suite passes via `mvn test`; baseline 66 tests not regressed; new tests mock providers via RestClient deep-stubs / package-private test constructors (NbaApiServiceTest pattern) | ✓ VERIFIED | Independently re-ran `mvn -q test` (not trusting SUMMARY claims) — exit code 0. `target/surefire-reports/*.txt` confirms **120 tests across 17 classes, 0 failures, 0 errors, 0 skipped**. All 9 pre-existing classes retain their original counts (`OneStopSportsApplicationTests`=1, `AuthServiceTest`=6, `AuthControllerTest`=7, `MatchServiceTest`=13, `LeagueServiceTest`=9, `PlayerServiceCareerStatsTest`=9, `NbaApiServiceTest`=13, `TeamServiceTest`=3, `TextNormalizerTest`=5 = 66 baseline intact). 8 new classes add exactly 54 tests (7+5+8+5+4+11+8+6). Every new external-API test constructs the service via a package-private test constructor with `@Mock(answer=Answers.RETURNS_DEEP_STUBS) RestClient` — no `RestClient.builder()` or live HTTP call appears in any new test file. |

**Score:** 9/9 truths verified (0 present-but-behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/onestopsports/service/NflApiService.java` | `@Autowired` production ctor + package-private `(RestClient, RestClient, RestClient)` test ctor | ✓ VERIFIED | Confirmed at lines 89-115; junior-dev comment present. |
| `src/main/java/com/onestopsports/service/ExternalApiService.java` | `@Autowired` production ctor + package-private `(RestClient, LeagueRepository)` test ctor | ✓ VERIFIED | Confirmed at lines 46-68. |
| `src/main/java/com/onestopsports/service/ApiFootballService.java` | `@Autowired` production ctor + package-private `(RestClient)` test ctor | ✓ VERIFIED | Confirmed at lines 64-82. |
| `src/main/java/com/onestopsports/service/BallDontLieService.java` | `@Autowired` production ctor + package-private `(RestClient)` test ctor + package-private response records | ✓ VERIFIED | Confirmed at lines 32-52; `BdlPlayersResponse`/`BdlPlayer` records confirmed package-private (no `private` modifier) at lines 133/139. |
| `src/test/java/com/onestopsports/service/NflApiServiceTest.java` | Happy-path + soft-fail, no live network | ✓ VERIFIED | 6 tests, all pass. |
| `src/test/java/com/onestopsports/service/ExternalApiServiceTest.java` | Happy-path + soft-fail, no live network | ✓ VERIFIED | 5 tests, all pass. |
| `src/test/java/com/onestopsports/service/ApiFootballServiceTest.java` | Happy-path + soft-fail, no live network | ✓ VERIFIED | 7 tests, all pass. |
| `src/test/java/com/onestopsports/service/BallDontLieServiceTest.java` | Happy-path + soft-fail, no live network | ✓ VERIFIED | 5 tests, all pass. |
| `src/test/java/com/onestopsports/service/UserServiceTest.java` | 404 guards, skip-if-favourited, delegation | ✓ VERIFIED | 11 tests, all pass. |
| `src/test/java/com/onestopsports/service/SportServiceTest.java` | Listing + slug 404 | ✓ VERIFIED | 4 tests, all pass. |
| `src/test/java/com/onestopsports/service/PlayerServiceTest.java` | resolvePhotoUrl (3 branches + null) / toDto / searchPlayers | ✓ VERIFIED | 8 tests, all pass; `PlayerServiceCareerStatsTest.java` untouched as required. |
| `src/test/java/com/onestopsports/controller/GlobalExceptionHandlerTest.java` | Dispatch-order + handler-mapping coverage | ✓ VERIFIED | 8 tests, all pass; dispatch proven via real `standaloneSetup` MVC, not direct calls. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| 01-01 package-private test constructors | Wave-2 adapter tests (01-02, 01-03) | Direct construction in `@BeforeEach` (`new NflApiService(restClient, standingsClient, statsClient)`, etc.) | ✓ WIRED | Confirmed in all four Wave-2 test files — each constructs the service under test via the exact 01-01 test-constructor signature. |
| `@Autowired` on production `@Value` constructors | Spring context bean resolution | `OneStopSportsApplicationTests` full-context load | ✓ WIRED | Full suite run (including `OneStopSportsApplicationTests`) passed with 0 errors — Spring still resolves each production constructor unambiguously after the second constructor was added. |
| `GlobalExceptionHandler` handler methods | Spring's `@ExceptionHandler` dispatch resolver | `MockMvcBuilders.standaloneSetup(...).setControllerAdvice(new GlobalExceptionHandler())` | ✓ WIRED | Both dispatch-order tests pass, proving Spring picks `handleResponseStatus` over `handleGeneric` for a `ResponseStatusException`, and falls through to `handleGeneric` for an unrelated `RuntimeException`, without leaking the raw exception message. |
| `UserService` favourite mapping | `TeamService.toDto` / `PlayerService.toDto` | Mocked collaborator stubbed to return a known DTO, asserted to flow through unchanged | ✓ WIRED | `getFavoriteTeams_mapsEachRowThroughTeamServiceToDto` / `getFavoritePlayers_mapsEachRowThroughPlayerServiceToDto` both pass. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full backend suite green, baseline preserved | `mvn -q test` (run once, independently, not trusting SUMMARY) | Exit 0; `target/surefire-reports/*.txt` shows 120 tests / 17 classes, 0 failures/errors/skipped | ✓ PASS |
| No debt-marker comments introduced by this phase | `grep -n -E "TBD\|FIXME\|XXX\|TODO\|HACK\|PLACEHOLDER"` on all files this phase modified/created | One hit: `NflApiService.java:677` — a pre-existing string literal `"TBD"` used as a placeholder team **name value** (not a code comment about incomplete work), confirmed via `git show c0089f0` to be untouched by this phase's diff | ✓ PASS (non-issue, pre-existing) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| HARD-01 | 01-01, 01-02, 01-03, 01-04, 01-05 (all 5) | Close backend service test-coverage gaps across NflApiService, ExternalApiService, ApiFootballService, BallDontLieService, UserService, SportService, PlayerService (search/resolvePhotoUrl/toDto), GlobalExceptionHandler; mock external providers, never call live APIs | ✓ SATISFIED | All 9 observable truths above verified; REQUIREMENTS.md intentionally leaves the HARD-01 checkbox unchecked per its own instruction ("HARD-01 spans all 5 plans and closes only at phase completion") — this phase's completion is what satisfies it. No orphaned Phase 1 requirements (only HARD-01 maps to Phase 1 in REQUIREMENTS.md). |

### Anti-Patterns Found

None blocking. One pre-existing, out-of-scope string literal (`"TBD"` team-name placeholder in `NflApiService.emptyTeam()`) noted above — not a debt-marker comment, not touched by this phase's commits.

### Human Verification Required

None. Every must-have truth was verified through direct code inspection, independent test-file reading, and an independently re-run `mvn test` — no visual, real-time, or external-service behavior in scope for this phase.

### Gaps Summary

No gaps. All 4 ROADMAP success criteria are met:
1. NflApiService/ExternalApiService/ApiFootballService/BallDontLieService — happy-path + soft-fail, no live network calls. ✓
2. UserService/SportService/GlobalExceptionHandler unit-tested, including MVC-dispatch-proven passthrough ordering. ✓
3. PlayerService resolvePhotoUrl/toDto/search unit-tested. ✓
4. Full suite green via `mvn test`, baseline 66 not regressed (confirmed 66 intact + 54 new = 120), RestClient deep-stubs/test-constructor pattern followed throughout. ✓

All 5 plan SUMMARY.md claims were independently verified against the actual test file contents and a fresh `mvn test` run — no discrepancies found between claimed and actual behavior.

---

*Verified: 2026-07-08T23:40:00Z*
*Verifier: Claude (gsd-verifier)*
