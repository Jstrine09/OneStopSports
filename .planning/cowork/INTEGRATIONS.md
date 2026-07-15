# OneStopSports — External Integrations

> **What this doc is:** Reference for every external service this app talks to. Five sports APIs (one per provider, one Spring bean per provider). Includes auth, free-tier limits, URL patterns, quirks, and the load-bearing constants the code depends on. Pull this in whenever work touches an external call — half the project's complexity lives here.

---

## At a glance

| Service | Service class | Auth | Free-tier limit | Used for |
|---|---|---|---|---|
| football-data.org v4 | `ExternalApiService` | `X-Auth-Token` header | **10 req/min** | Football (soccer) — matches, standings, squads, events |
| ESPN (NBA) | `NbaApiService` | None (public) | None documented | NBA — teams, rosters, scoreboard, standings, career stats |
| ESPN (NFL) | `NflApiService` | None (public) | None documented | NFL — teams, rosters, scoreboard, standings, career stats |
| balldontlie.io v1 | `BallDontLieService` | `Authorization: <key>` (no Bearer) | **5 req/min**, `/players` + `/teams` only | NBA player bios |
| api-sports.io v3 (football) | `ApiFootballService` | `x-apisports-key` header | **100 req/day**, season cap at **2024** | Football player career stats |

**Where the keys live:**
- Local dev → `src/main/resources/application-local.yml` (gitignored)
- Docker → `.env` at project root (gitignored), forwarded as env vars via `application-docker.yml`

---

## football-data.org v4

**Purpose:** Primary football (soccer) data source. Everything for the six seeded leagues — competitions, teams, squads, fixtures, results, standings, match events (goals, bookings, substitutions).

**Class:** `service/ExternalApiService.java` (~456 LOC — the biggest single integration)

**Auth:** `X-Auth-Token: <key>` header, applied via `RestClient.builder().defaultHeader(...)` in the constructor.

**Configuration:**
- `external-api.football-data.base-url` — defaults to `https://api.football-data.org/v4`
- `external-api.football-data.api-key` — placeholder in `application.yml`; real value in `application-local.yml` or `FOOTBALL_DATA_API_KEY` env var

**Free-tier limits:**
- **10 requests per minute** — `DataLoader` sleeps 6.2s between competition seeding calls to stay inside this
- No match stats — `MatchService.getMatchStats()` returns `Map.of()`
- No lineups — `MatchService.getMatchLineups()` returns `Map.of()`

**Endpoints we call:**
```
GET /competitions/{id}/teams          full squad seeding (DataLoader)
GET /competitions/{id}/matches?date=  fixtures + live matches
GET /competitions/{id}/standings      league tables
GET /matches/{id}                     single match (incl. events)
GET /persons/{id}                     player bio (basic only on free tier)
```

**Competition IDs (seeded in `DataLoader`):**
| League | football-data ID |
|---|---|
| Premier League | 2021 |
| La Liga | 2014 |
| Bundesliga | 2002 |
| Serie A | 2019 |
| Ligue 1 | 2015 |
| UEFA Champions League | 2001 |

Mapped to internal DB league IDs via `League.external_id` (Flyway `V4`).

**Quirks:**
- All inner records annotated `@JsonIgnoreProperties(ignoreUnknown = true)` so added upstream fields don't crash deserialisation
- Football timestamps are left as UTC (`MatchDto.timezone == null`) — only NBA/NFL convert to ET
- Match stats/lineups stubs exist in the controller surface so frontend doesn't 404, but they return empty

---

## ESPN — NBA (unofficial public API)

**Purpose:** NBA teams (with logos), rosters, scoreboards, standings, player career stats.

**Class:** `service/NbaApiService.java`

**Auth:** None — public unauthenticated.

**Configuration — THREE separate base URLs, THREE `RestClient` instances:**

| Key | URL | Used for |
|---|---|---|
| `external-api.nba.base-url` | `https://site.api.espn.com/apis/site/v2/sports/basketball/nba` | teams, rosters, scoreboard |
| `external-api.nba.standings-url` | `https://site.web.api.espn.com/apis/v2/sports/basketball/nba` | standings (note: `site.web.api`) |
| `external-api.nba.stats-url` | `https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba` | career stats (path: `{stats-url}/athletes/{espnAthleteId}/stats`) |

The production `@Autowired` constructor takes all three `@Value` URLs; a package-private test constructor `NbaApiService(RestClient, RestClient, RestClient)` lets `NbaApiServiceTest` inject mocks directly.

**Free-tier limits:** None documented — but the API is undocumented and unofficial, so ESPN can throttle or change it without notice. We guard with:
- `@JsonIgnoreProperties(ignoreUnknown = true)` on every record (handles added fields)
- Swallow `RestClientException`, log `warn`, return empty list (frontend shows "no data")

**NBA-specific quirks:**
- **Roster shape:** `athletes` is a **flat array** — no `positionGroups` wrapping like NFL
- **Positions are full names:** `"Center"`, `"Guard"`, `"Forward"` — no abbreviation expansion needed
- **`dateOfBirth` available** for NBA players (parsed from ISO-8601 string to `LocalDate`)
- **Scoreboard team logo** is a single `logo` string, NOT a `logos` array (different from teams endpoint)
- **Standings nesting:** 2 levels (Conference → Entries)
- **Live status:** ESPN returns `STATUS_IN_PROGRESS` for in-progress games, mapped to `"LIVE"` (NOT football's `"IN_PLAY"`). The frontend `getMatchState("LIVE")` handles green-score highlighting
- **Season label logic:** `month >= 10 ? year + 1 : year` — October–December belongs to the next season label (e.g. Oct 2024 → "2024-25")
- **UTC → ET conversion required** (see "Time zone handling" below)
- **Auto-migration in `NbaDataLoader`:** if all 30 teams exist but none have a `crestUrl`, the loader re-runs to update logos. Handles legacy data from before the ESPN switch.

**Endpoints we call:**
```
GET /teams                                  all 30 teams + logos
GET /teams/{id}/roster                      flat athletes[]
GET /scoreboard?dates=YYYY-MM-DD            games on a date
GET <standings-url>/standings?season=...    conference standings
GET <stats-url>/athletes/{id}/stats         career stats (categories: averages, totals, miscellaneous)
```

---

## ESPN — NFL (unofficial public API)

**Purpose:** Same pattern as NBA — teams (with logos), rosters, scoreboards, standings, career stats.

**Class:** `service/NflApiService.java` (~650 LOC)

**Auth:** None — public unauthenticated.

**Configuration — same three-URL pattern, but note subdomain difference:**

| Key | URL | Used for |
|---|---|---|
| `external-api.nfl.base-url` | `https://site.api.espn.com/apis/site/v2/sports/football/nfl` | teams, rosters, scoreboard |
| `external-api.nfl.standings-url` | `https://site.api.espn.com/apis/v2/sports/football/nfl` | standings (**`site.api`**, NOT `site.web.api`) |
| `external-api.nfl.stats-url` | `https://site.web.api.espn.com/apis/common/v3/sports/football/nfl` | career stats |

**Footgun:** NBA standings live on `site.web.api.espn.com`. NFL standings live on `site.api.espn.com`. Easy to copy the NBA pattern wrong when adding a new sport. The yml comments call this out.

**NFL-specific quirks:**
- **Roster grouped by side** — `EspnPositionGroup` wraps `items: List<EspnAthlete>` with `position` set to `"offense"` / `"defense"` / `"specialTeam"`. Must iterate all groups (~53 active players per team).
- **No `dateOfBirth`** for NFL players — ESPN doesn't expose it on this endpoint
- **Position abbreviations stored as-is** — "QB", "WR", "CB" (opposite of NBA where positions are full names)
- **Standings nesting:** 3 levels (Conference → Division → Group entries)
- **Hardcoded division map** — `NflApiService.DIVISION_BY_ABBR` maps all 32 abbreviations to divisions (AFC East/North/South/West, NFC equivalents). NFL divisions have been fixed since 2002. Footgun: if a team relocates and changes abbreviation (OAK → LV in 2020), the map needs updating.
- **Scoreboard date format:** `YYYYMMDD` (no dashes) — different from NBA which uses `YYYY-MM-DD`
- **Team/player IDs from ESPN are strings** — parsed to `Long` for the DB
- **Season label logic:** `month < 9 ? year - 1 : year` — January–August belongs to the previous season (e.g. Feb 2025 → "2024" season). Different from NBA.
- **Seeding sleep:** `NflDataLoader` sleeps 1500ms between roster fetches to avoid hitting ESPN throttles. Skip condition: `teamRepository.findByLeagueId(nfl.getId()).size() >= 32`.

---

## balldontlie.io v1

**Purpose:** NBA player biographical enrichment — height, weight, college, draft year/round/number. Called lazily on player detail page open via `GET /api/players/{id}/bio`.

**Class:** `service/BallDontLieService.java`

**Auth:** `Authorization: <api-key>` header — **plain key, NO "Bearer" prefix.** Applied via `RestClient.builder().defaultHeader("Authorization", apiKey)`.

**Configuration:**
- `external-api.balldontlie.base-url` — defaults to `https://api.balldontlie.io/v1`
- `external-api.balldontlie.api-key` — placeholder in `application.yml`; real value in `application-local.yml`

**Free-tier limits:**
- **5 requests per minute** — easy to trip if a user clicks through a roster fast (failures return `Optional.empty()` and the bio card hides silently)
- Only `/players` and `/teams` endpoints — no stats (those are paid tiers)

**Endpoints we call:**
```
GET /players?search={firstName}&per_page=10
```

**Quirks:**
- **Search matches first name only** — strategy is split full name on whitespace, search by first word, filter results in code by lastname match (`lastName.equalsIgnoreCase(...)`)
- **Hyphenated last names stored as one token** ("Gilgeous-Alexander") — works today but means the comparison is exact match
- **Weight is a String** in the response ("223") — parsed to `Integer` in `searchPlayerByName()` with `NumberFormatException` swallowed (null on parse failure)
- **Soft-fail** — any exception logs `warn` and returns `Optional.empty()`. The bio section just doesn't render. Enrichment is non-critical.
- **snake_case JSON fields** — `@JsonProperty("first_name")`, `@JsonProperty("last_name")`, `@JsonProperty("draft_year")`, etc., on the private `BdlPlayer` record

---

## api-sports.io v3 (football) — "API-Football"

**Purpose:** Football (soccer) player career stats — fills the gap left by football-data.org's free tier (which has no stats). Powers the per-season stats card on the football player detail page.

**Class:** `service/ApiFootballService.java`

**Auth:** `x-apisports-key: <key>` header (direct api-sports.io signup). If signed up via RapidAPI instead, swap to `x-rapidapi-key` + `x-rapidapi-host` headers and use base URL `https://api-football-v1.p.rapidapi.com/v3`.

**Configuration:**
- `external-api.api-football.base-url` — defaults to `https://v3.football.api-sports.io`
- `external-api.api-football.api-key` — placeholder in `application.yml`; real value in `application-local.yml`

**Free-tier limits — TWO restrictions both matter:**
1. **100 requests per DAY** — very tight; production would need server-side caching with a 24h+ TTL
2. **Season cap at 2024** — any `season > 2024` returns `{ "errors": { "plan": "Free plans do not have access to this season..." } }`. Enforced by `private static final int FREE_TIER_MAX_SEASON = 2024;` in `ApiFootballService.java`. As of mid-2026, the service serves the **2024-25 season** rather than the in-progress 2025-26 season.

**Two-step flow because search requires a league filter:**
1. `searchPlayerId(name, leagueId, season)` → finds the API-SPORTS player ID by name match
2. `fetchPlayerStats(playerId, season)` → fetches the actual season stats

Result of step 1 is **persisted to `Player.external_id`** (Flyway `V6`) so future requests skip step 1 entirely. Combined with React Query's 24h `staleTime` on the frontend, this keeps the 100/day quota manageable for personal use.

**League mapping — hardcoded in the service:**

| football-data ID | API-SPORTS ID | League |
|---|---|---|
| 2021 | 39 | Premier League |
| 2014 | 140 | La Liga |
| 2002 | 78 | Bundesliga |
| 2019 | 135 | Serie A |
| 2015 | 61 | Ligue 1 |
| 2001 | 2 | UEFA Champions League |

Competitions outside this map (domestic cups, lower divisions) return no stats with no user-facing explanation.

**Name-matching quirks:**
- **Search rejects diacritics** — API returns `{ "errors": { "search": "The Search field may only contain alpha-numeric characters and spaces." } }` if you send "Dembélé". Strip via `Normalizer.NFD` + remove combining marks before sending.
- **`stripAccents()` is applied on BOTH sides** of every comparison (search term AND post-fetch match-back). Both branches — exact-match and loose lastname fallback — normalise before comparing.
- **Search term must be ≥ 4 characters.** Service tries lastname first, falls back to firstname, returns `Optional.empty()` if neither is long enough.
- **Match resolution strategy:** exact case-insensitive full-name match → loose lastname match → `Optional.empty()`. Deliberately does NOT fall back to "first result" — wrong-player risk is too high.
- **Mid-season transfers** — API returns one stat block per team the player represented; rendered as separate rows with team + competition disambiguating.

**Curated stat columns (10 of ~60 API fields):**
`APPS`, `MIN`, `GOALS`, `AST`, `SHOTS`, `ON`, `PASS%`, `YEL`, `RED`, `RATING`

**Soft-fail behaviour:** any `RestClientException` (429, 5xx, network) is caught, logged `warn`, returns `null`. Frontend stats card just doesn't render.

---

## Time zone handling

NBA and NFL game times are converted **UTC → ET** in the backend so the DB stores ET wall-clock time as a naive `LocalDateTime`. The conversion lives in both `NbaApiService` and `NflApiService`:

```java
startTime = OffsetDateTime.parse(event.date())
        .atZoneSameInstant(ZoneId.of("America/New_York"))
        .toLocalDateTime();
```

`ZoneId.of("America/New_York")` handles EDT/EST transitions automatically.

**`MatchDto.timezone` field** (9th component): `"ET"` for NBA/NFL games, `null` for football. The frontend's `formatKickoff(utc, match.timezone)` appends "ET" to the display string when set. The time itself displays correctly in any browser locale because the backend already stored the ET wall-clock value.

Football times stay UTC (football-data.org gives them that way; no conversion).

---

## Image / CDN URLs we depend on

| Asset | Pattern | Notes |
|---|---|---|
| NBA team logo | `https://a.espncdn.com/i/teamlogos/nba/500/{abbr}.png` (lowercase abbr, e.g. `bos`) | Captured at seed time into `team.crest_url` |
| NFL team logo | `https://a.espncdn.com/i/teamlogos/nfl/500/{abbr}.png` | Captured at seed time into `team.crest_url` |
| NBA player headshot | `https://a.espncdn.com/i/headshots/nba/players/full/{espnId}.png` | **Reconstructed on the fly** by `PlayerService.resolvePhotoUrl` from `player.external_id` + sport slug — no DB column needed for NBA/NFL |
| NFL player headshot | `https://a.espncdn.com/i/headshots/nfl/players/full/{espnId}.png` | Same — reconstructed from `external_id` |
| Football crest | football-data.org's `crestUrl` field | Persisted to `team.crest_url` at seed time |
| Football player photo | `https://media.api-sports.io/football/players/{apiSportsId}.png` | **Reconstructed on the fly** (commit `71e74b7`, QA U1), same as NBA/NFL — keyed off `player.external_id`, which is the api-sports.io player ID, populated lazily on first career-stats lookup. Players never stats-viewed have no `external_id` yet and fall through to the frontend's initials-tile fallback. |

`PlayerService.resolvePhotoUrl` has three layers: persisted `photoUrl` → derived CDN URL (ESPN for NBA/NFL, API-SPORTS media CDN for football) → null. The remaining gap is footballers whose `external_id` was never lazily populated — tracked as HARD-04 (career-stats name-match hardening), not a missing-wiring gap.

---

## Adding a new external API

Follow the existing shape — never mix providers:

1. New `@Service` class in `service/` named `<Provider>ApiService.java`
2. One `RestClient` field, built in the constructor from `@Value("${external-api.<provider>.<key>}")` properties
3. Inner records mirroring the provider's JSON, each annotated `@JsonIgnoreProperties(ignoreUnknown = true)`
4. Mapper methods that convert those records to project DTOs
5. New keys under `external-api.<provider>` in `application.yml` AND a matching entry in `src/main/resources/META-INF/additional-spring-configuration-metadata.json` (for IDE auto-complete)
6. If the provider corresponds to a **new sport**, extend the `switch` arms in `MatchService.getMatchesByLeagueAndDate`, `LeagueService.getStandings`, and `PlayerService.getPlayerCareerStats` to cover the new `Sport.slug`
7. If live scores are involved, extend `MatchService.fetchNonFootballLiveMatches`

---

## Authentication / identity (not external — for completeness)

- Self-hosted username/password with JWT bearer tokens. No external identity provider.
- Password hashing: BCrypt via `PasswordEncoder` bean in `config/PasswordConfig.java` (deliberately separated from `SecurityConfig` to break the `JwtAuthFilter → AuthService → PasswordEncoder → JwtAuthFilter` cycle)
- JWT signing: HMAC with Base64-encoded secret in `jwt.secret`; library `jjwt 0.12.6`
- Token lifetime: 24h (`jwt.expiration-ms: 86400000`)
- `AuthenticationManager` injected with `@Lazy` in `AuthService`'s manual constructor

---

## Observability

- **Logging:** SLF4J via `LoggerFactory.getLogger(MatchService.class)`; bracketed prefix on log lines (`log.warn("[NbaApiService] fetchStandings failed for season={}: {}", season, e.getMessage())`)
- **Metrics / tracing:** None
- **API docs:** Swagger UI at `/swagger-ui/index.html`; raw spec at `/v3/api-docs` — **dev/local only**; disabled in the `prod` profile (`springdoc.api-docs.enabled=false` / `swagger-ui.enabled=false`, commit `ff9fc60`, QA finding S2) so the API surface isn't publicly enumerable
- **Error responses:** `GlobalExceptionHandler` returns consistent `ErrorResponseDto(status, error, message, timestamp)` for every exception type
