> ⚠️ **SNAPSHOT — 2026-05-21.** This codebase map is a point-in-time analysis and is now partially STALE. For current state see `/CLAUDE.md` and `.planning/cowork/`. Major changes since this snapshot: full "sport field" frontend redesign (`SportFieldBackdrop` + `.glass-card` + `SectionLabel`/`RowCard`), player career stats + bio + ESPN-CDN headshots, live game clock (`MatchDto.clock`, now 10 fields), match box score (`BoxScoreDto`), migrations V6+V7, two new services (`ApiFootballService`, `BallDontLieService`), production deploy (Render + Neon, single-origin via `SpaForwardingConfig`), and the 5-persona QA fixes (auth-bypass fix + `AuthenticationEntryPoint`, 500→4xx handlers, a11y focus/reduced-motion). Tests: 57. Regenerate this map with `/gsd:map-codebase`.

# External Integrations

**Analysis Date:** 2026-05-21

OneStopSports talks to five external services for sports data plus one internal infra service (Redis). Each external API is wrapped by a single Spring `@Service` class, configured under the `external-api.*` prefix in `src/main/resources/application.yml`, and uses Spring 6's synchronous `RestClient`. All API keys live in the gitignored `src/main/resources/application-local.yml` (for `local` profile) or the gitignored `.env` (for `docker` profile).

## Integration Summary

| Service | Service class | Auth | Free-tier limit | Sport |
|---------|---------------|------|-----------------|-------|
| football-data.org v4 | `ExternalApiService.java` | `X-Auth-Token` header | 10 req/min | Football (soccer) |
| ESPN (NBA) | `NbaApiService.java` | None (public) | None documented | NBA |
| ESPN (NFL) | `NflApiService.java` | None (public) | None documented | NFL |
| balldontlie.io v1 | `BallDontLieService.java` | `Authorization: <key>` (no Bearer) | 5 req/min, `/players` + `/teams` only | NBA (player bios) |
| API-Football (api-sports.io v3) | `ApiFootballService.java` | `x-apisports-key` header | **100 req/day** + **`season <= 2024`** | Football (player career stats) |

## football-data.org v4

**Purpose:** Primary football (soccer) data source — competitions, teams, squads, fixtures, results, standings, match events.

**Service class:** `src/main/java/com/onestopsports/service/ExternalApiService.java`

**Auth mechanism:** `X-Auth-Token: <key>` request header, applied via `RestClient.builder().defaultHeader(...)` in the constructor.

**Configuration:**
- `external-api.football-data.base-url` — defaults to `https://api.football-data.org/v4`
- `external-api.football-data.api-key` — placeholder `YOUR_API_KEY_HERE` in `application.yml`; real value goes in `application-local.yml` (local profile) or `FOOTBALL_DATA_API_KEY` env var (docker profile, via `application-docker.yml`)

**Free-tier limits:**
- 10 requests per minute
- No match stats (`MatchService.getMatchStats()` returns `Map.of()`)
- No lineups (`MatchService.getMatchLineups()` returns `Map.of()`)

**Endpoints used:**
- `GET /competitions/{id}/teams` — full squad seeding (`DataLoader`)
- `GET /competitions/{id}/matches?date=...` — fixtures and live matches
- `GET /competitions/{id}/standings` — league tables
- `GET /matches/{id}` — single match detail (incl. events: goals, bookings, substitutions)

**Competition IDs used (`DataLoader`):**
| Competition | football-data ID |
|-------------|------------------|
| Premier League | 2021 |
| La Liga | 2014 |
| Bundesliga | 2002 |
| Serie A | 2019 |
| Ligue 1 | 2015 |
| UEFA Champions League | 2001 |

**Quirks / workarounds:**
- 10 req/min cap → `DataLoader` sleeps **6.2 seconds** between competition seeding calls
- The 6 competition IDs are mapped to internal DB league IDs via `League.external_id` (added in Flyway `V4__add_league_external_id.sql`)
- API response records are private inner records on `ExternalApiService`; all annotated `@JsonIgnoreProperties(ignoreUnknown = true)` so added fields don't crash deserialisation
- Football timestamps from this API are left as UTC (`MatchDto.timezone == null`); only NBA/NFL get UTC→ET conversion

## ESPN — NBA (unofficial public API)

**Purpose:** NBA teams (with logos), rosters, scoreboards (live scores), standings, player career stats.

**Service class:** `src/main/java/com/onestopsports/service/NbaApiService.java`

**Auth mechanism:** None — public unauthenticated API.

**Configuration (3 separate base URLs, 3 `RestClient` instances):**
- `external-api.nba.base-url` — `https://site.api.espn.com/apis/site/v2/sports/basketball/nba` — teams, rosters, scoreboard
- `external-api.nba.standings-url` — `https://site.web.api.espn.com/apis/v2/sports/basketball/nba` — standings (different subdomain `site.web.api` vs `site.api`)
- `external-api.nba.stats-url` — `https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba` — career stats (path pattern: `{stats-url}/athletes/{espnAthleteId}/stats`)

**Free-tier limits:** None documented — but ESPN reserves the right to throttle or change the API without notice (it is undocumented).

**Quirks / workarounds:**
- **Three URLs, three `RestClient`s** — the production `@Autowired` constructor takes all three `@Value` URLs; a package-private test constructor `NbaApiService(RestClient, RestClient, RestClient)` exists for `NbaApiServiceTest` to inject mocks directly
- **Roster shape differs from NFL** — `athletes` is a **flat array** of `EspnAthlete`, NOT grouped by offense/defense/specialTeam like NFL
- **Position values are full names** ("Center", "Guard", "Forward") — no abbreviation expansion needed
- **`dateOfBirth` IS available** for NBA players (ISO-8601 string parsed to `LocalDate`); NFL doesn't expose this
- **Scoreboard team logo** is a single `logo` string, NOT a `logos` array
- **Standings nesting:** 2 levels (Conference → Entries) — flatter than NFL
- **UTC → ET conversion required:** ESPN returns game times as UTC ISO-8601 (e.g. `"2025-04-26T23:30Z"`). Backend converts with `OffsetDateTime.parse(event.date()).atZoneSameInstant(ZoneId.of("America/New_York")).toLocalDateTime()` so the stored naive `LocalDateTime` is the ET wall-clock time. `MatchDto.timezone == "ET"` flags this for the frontend to append the "ET" label.
- **Season label logic:** `month >= 10 ? year + 1 : year` → e.g. October 2024 belongs to the "2024-25" season
- **NBA live status:** ESPN reports in-progress games as `STATUS_IN_PROGRESS` → mapped to `"LIVE"` (NOT football's `"IN_PLAY"`); the frontend `getMatchState("LIVE")` handles green score highlighting
- **Auto-migration:** `NbaDataLoader` re-runs if all 30 teams exist but **no team has a crestUrl** — handles legacy data that pre-dated the ESPN switch

**Endpoints used:**
- `GET /teams` — all 30 teams + ESPN CDN logo URLs (e.g. `https://a.espncdn.com/i/teamlogos/nba/500/bos.png`)
- `GET /teams/{id}/roster` — full roster
- `GET /scoreboard?dates={YYYYMMDD}` — games on a date
- `GET <standings-url>/standings?season=...&type=...` — conference standings
- `GET <stats-url>/athletes/{id}/stats` — career stats

## ESPN — NFL (unofficial public API)

**Purpose:** NFL teams (with logos), rosters, scoreboards (live scores), standings, player career stats.

**Service class:** `src/main/java/com/onestopsports/service/NflApiService.java`

**Auth mechanism:** None — public unauthenticated API.

**Configuration (3 separate base URLs, 3 `RestClient` instances — same pattern as NBA):**
- `external-api.nfl.base-url` — `https://site.api.espn.com/apis/site/v2/sports/football/nfl`
- `external-api.nfl.standings-url` — `https://site.api.espn.com/apis/v2/sports/football/nfl` (apis/v2 not apis/site/v2 — same difference as NBA, but note this one stays on `site.api`, NOT `site.web.api`)
- `external-api.nfl.stats-url` — `https://site.web.api.espn.com/apis/common/v3/sports/football/nfl`

**Free-tier limits:** None documented (undocumented public API — same caveat as NBA).

**Quirks / workarounds:**
- **Roster grouped by side** — `EspnPositionGroup` has a `position` field (`"offense"` / `"defense"` / `"specialTeam"`) wrapping an `items: List<EspnAthlete>`. Must iterate ALL groups to collect every player (~53 active players per team).
- **No `dateOfBirth`** for NFL players (ESPN doesn't expose it on this endpoint)
- **Position abbreviations stored as-is** — "QB", "WR", "CB" (no full-name expansion, opposite of NBA)
- **Standings nesting:** 3 levels (Conference → Division → Group entries) — deeper than NBA. Teams sorted by wins descending within each division.
- **Date format for scoreboard:** `YYYYMMDD` (no dashes) e.g. `"20250209"` — different from NBA which uses `YYYY-MM-DD`
- **Team/player IDs from ESPN are strings** — parsed to `Long` for the DB
- **Hardcoded division map** — `DIVISION_BY_ABBR` in `NflApiService` maps the 32 team abbreviations to divisions (AFC East/North/South/West, NFC equivalents). NFL divisions have been fixed since 2002, so this avoids an extra endpoint call.
- **UTC → ET conversion** — same logic as NBA; `MatchDto.timezone == "ET"`
- **Season label logic:** `month < 9 ? year - 1 : year` → e.g. February 2025 belongs to the "2024" season (NFL season spans Sept → early Feb)
- **Seeding sleep:** `NflDataLoader` sleeps 1500ms between roster fetches (to avoid hitting ESPN throttles); skip condition is `teamRepository.findByLeagueId(nfl.getId()).size() >= 32`

**Endpoints used:** Mirror NBA — `/teams`, `/teams/{id}/roster`, `/scoreboard?dates={YYYYMMDD}`, `<standings-url>/standings`, `<stats-url>/athletes/{id}/stats`.

## balldontlie.io v1

**Purpose:** NBA player biographical enrichment (height, weight, college, draft year/round/number). Called lazily on player detail page open via `GET /api/players/{id}/bio`.

**Service class:** `src/main/java/com/onestopsports/service/BallDontLieService.java`

**Auth mechanism:** `Authorization: <api-key>` header — **plain key, NO "Bearer" prefix**. Applied via `RestClient.builder().defaultHeader("Authorization", apiKey)` in the constructor.

**Configuration:**
- `external-api.balldontlie.base-url` — defaults to `https://api.balldontlie.io/v1`
- `external-api.balldontlie.api-key` — placeholder in `application.yml`; real value in `application-local.yml`

**Free-tier limits:**
- **5 requests per minute**
- Only `/players` and `/teams` endpoints (no stats — those are paid tiers)

**Endpoints used:**
- `GET /players?search={firstName}&per_page=10` — player search

**Quirks / workarounds:**
- **Search matches first name only** — strategy: split full name on whitespace, search by first word, then filter results in code where `lastName.equalsIgnoreCase(p.lastName())`
- **Multi-word last names** are stored as a single token by balldontlie (e.g. "Gilgeous-Alexander") — the second `split("\\s+", 2)` handles this
- **Weight is a String** in the response (e.g. `"223"`) — parsed to `Integer` in `searchPlayerByName()` with `NumberFormatException` swallowed (null on parse failure)
- **Soft-fail on errors** — any exception logs `warn` and returns `Optional.empty()`. The frontend bio section simply doesn't render. Enrichment data is non-critical.
- **JSON field mapping** — `@JsonProperty("first_name")`, `@JsonProperty("last_name")`, `@JsonProperty("draft_year")`, etc., on the private `BdlPlayer` record (balldontlie uses snake_case)

## API-Football (api-sports.io v3)

**Purpose:** Football (soccer) player career stats — fills the gap left by football-data.org which does not expose player stats on its free tier. Powers the per-season stats card on the football player detail page.

**Service class:** `src/main/java/com/onestopsports/service/ApiFootballService.java`

**Auth mechanism:** `x-apisports-key: <key>` header (direct api-sports.io signup). If signed up via RapidAPI instead, swap to `x-rapidapi-key` + `x-rapidapi-host` headers and use base URL `https://api-football-v1.p.rapidapi.com/v3`.

**Configuration:**
- `external-api.api-football.base-url` — defaults to `https://v3.football.api-sports.io`
- `external-api.api-football.api-key` — placeholder in `application.yml`; real value in `application-local.yml`

**Free-tier limits — TWO restrictions both matter:**
1. **100 requests per DAY** (very tight — production would need server-side caching with 24h+ TTL)
2. **Season cap at 2024** — any `season > 2024` returns `{ "errors": { "plan": "Free plans do not have access to this season..." } }`. Enforced in code by `FREE_TIER_MAX_SEASON = 2024` constant in `ApiFootballService.java`.

**Quirks / workarounds:**
- **Two-step flow** — free tier requires a league filter on `/players` search:
  1. `searchPlayerId(name, leagueId, season)` — find API-SPORTS player ID by name match
  2. `fetchPlayerStats(playerId, season)` — fetch the actual season stats
- **Player ID caching** — step 1's result is persisted to `Player.externalId` (Flyway `V6__add_player_external_id.sql`) so future requests skip straight to step 2. Combined with React Query's 24h `staleTime` on the frontend, this keeps the 100/day quota manageable.
- **DB league ID → API-SPORTS league ID mapping** — hardcoded `FOOTBALL_DATA_TO_API_SPORTS_LEAGUE` map in the service:
  | football-data.org ID | API-SPORTS ID | League |
  |----------------------|---------------|--------|
  | 2021 | 39 | Premier League |
  | 2014 | 140 | La Liga |
  | 2002 | 78 | Bundesliga |
  | 2019 | 135 | Serie A |
  | 2015 | 61 | Ligue 1 |
  | 2001 | 2 | UEFA Champions League |
- **Accent stripping required** — API-SPORTS' search param rejects diacritics and any non-alphanumeric character. The service decomposes via `Normalizer.NFD` then strips combining marks: "Dembélé" → "Dembele", "Müller" → "Muller". Name matching against the response is done against the ORIGINAL (accented) string.
- **Search term must be ≥ 4 characters** — service tries last name first, falls back to first name, returns `Optional.empty()` if neither is long enough
- **Match resolution strategy** — exact case-insensitive full-name match first, then loose lastname-only match, then `Optional.empty()` (deliberately does NOT fall back to "first result" — risk of wrong player too high)
- **`currentSeason()`** — `month >= 7 ? year : year - 1`, then clamped to `FREE_TIER_MAX_SEASON`. As of mid-2026 this means the service serves the **2024-25 season** instead of the current 2025-26 season.
- **Single-season at a time** — free tier returns one season per request. The `PlayerCareerStatsDto.StatCategory.careerTotals` field is left `null` (the DTO explicitly allows this).
- **Mid-season transfers** — API returns one stat block per team the player represented that season; each is rendered as a separate row with the team name carrying the difference.
- **Soft-fail on 429/5xx** — `RestClientException` is caught, logged at `warn`, and the method returns `null`. The frontend stats card simply doesn't show.

**Curated stat columns (football, 10 of ~60 API fields):**
`APPS`, `MIN`, `GOALS`, `AST`, `SHOTS`, `ON`, `PASS%`, `YEL`, `RED`, `RATING`

## Data Storage

**Database:** PostgreSQL 16
- Database name: `onestopsports`
- Connection (local): `jdbc:postgresql://localhost:5432/onestopsports` (`application.yml`)
- Connection (docker): `jdbc:postgresql://postgres:5432/onestopsports` (`application-docker.yml`)
- Credentials via `DB_PASSWORD` env var; default username `postgres`
- Schema managed by Flyway; Hibernate runs in `validate` mode
- Volume: `onestopsports_postgres_data` (named volume in docker-compose)

**File Storage:** None — no S3/blob storage. Team/player crest URLs point to external CDNs (football-data.org and ESPN CDN at `a.espncdn.com`).

**Caching:** Redis 7
- Host: `localhost:6379` (local) or `redis:6379` (docker)
- TTL: 30s on `matches` cache (live match feed)
- Serialiser: custom `GenericJackson2JsonRedisSerializer` configured in `RedisConfig.java` with `JavaTimeModule` registered + `DefaultTyping.EVERYTHING` enabled

## Authentication & Identity

**Strategy:** Self-hosted username/password + JWT bearer tokens. No external identity provider.

**Implementation:**
- Password hashing: BCrypt via `PasswordEncoder` bean defined in `config/PasswordConfig.java` (deliberately separated from `SecurityConfig` to break the cycle `JwtAuthFilter → AuthService → PasswordEncoder → JwtAuthFilter`)
- JWT signing: HMAC with Base64-encoded secret in `jwt.secret`; library is `jjwt 0.12.6`
- Default token lifetime: 24h (`jwt.expiration-ms: 86400000`)
- Filter: `security/JwtAuthFilter.java` reads `Authorization: Bearer ...`, validates via `security/JwtUtil.java`
- `AuthenticationManager` injected with `@Lazy` in `AuthService` (manual constructor — not `@RequiredArgsConstructor`) to defer resolution until first `login()` call

## Monitoring & Observability

- **Error tracking:** None
- **Logs:** SLF4J / Logback; per-class loggers; warn-level on external API failures
- **Metrics / tracing:** None (no Micrometer or Actuator endpoints currently configured)
- **API docs:** Swagger UI at `http://localhost:8081/swagger-ui/index.html`

## CI/CD & Deployment

- **CI pipeline:** None checked into the repo
- **Hosting:** Container target — `Dockerfile` is multi-stage and produces a self-contained image; no PaaS/k8s manifests
- **Manual deploy path:** `docker-compose up --build` brings up the full stack locally; production deployment is not wired in

## Environment Configuration

**Required env vars (`docker` profile, set in `.env`):**
- `DB_PASSWORD` — Postgres password
- `FOOTBALL_DATA_API_KEY` — football-data.org key
- `JWT_SECRET` — Base64-encoded JWT signing secret

**Not exposed via env vars** (must be set in `application-local.yml` for `local` profile):
- `external-api.balldontlie.api-key`
- `external-api.api-football.api-key`

**Secrets location:**
- Local profile → `src/main/resources/application-local.yml` (gitignored)
- Docker profile → `.env` at project root (gitignored); read by Docker Compose and forwarded into the `app` container as env vars; `application-docker.yml` references them as `${VAR}`

## Webhooks & Callbacks

- **Incoming:** None
- **Outgoing:** None

## WebSocket / Realtime

**Inbound from external services:** None — all external APIs are polled, not pushed.

**Outbound to clients:** STOMP-over-WebSocket
- Endpoint: `/ws` (SockJS)
- Topic: `/topic/matches/live`
- Producer: `MatchService.refreshLiveMatchCache()` — `@Scheduled(fixedDelay = 30_000)`, diffs against `previousSnapshot: Map<Long, String>` and only pushes on score/status change
- Consumer: `useLiveScores` hook in the React frontend; updates React Query cache via `queryClient.setQueryData(["matches","live"], matches)`

---

*Integration audit: 2026-05-21*
