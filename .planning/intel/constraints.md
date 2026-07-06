# Constraints Intel

Extracted from SPEC-type sources. Constraint types: api-contract | schema | nfr | protocol.

Both SPEC sources are now synthesized (the prior-run cross-reference cycle involving
ARCHITECTURE.md was broken — its `cross_refs` is now empty — so no docs are excluded).

Source SPECs:
- /Users/james/Projects/OneStopSports/.planning/cowork/ARCHITECTURE.md (internal architecture / layering / flows)
- /Users/james/Projects/OneStopSports/.planning/cowork/INTEGRATIONS.md (external API integrations)

---

## CON-layered-architecture
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ARCHITECTURE.md
- type: schema
- content: Classic layered Spring Boot `controller -> service -> repository -> DB`. Layer ownership: Application root (`OneStopSportsApplication` `@SpringBootApplication @EnableCaching @EnableScheduling`); `config/` (Security, Password, Redis, WebSocket, OpenAPI, three CommandLineRunner data loaders); `controller/` (thin `@RestController` per resource family returning `ResponseEntity<>`, plus `GlobalExceptionHandler` `@RestControllerAdvice`); `service/` (business logic + multi-sport routing + external-API adapters); `repository/` (Spring Data JPA, DERIVED QUERIES ONLY — no `@Query` anywhere); `model/` (JPA entities, mutable Lombok classes); `dto/` (Java 21 records); `security/` (`JwtUtil` + `JwtAuthFilter` `OncePerRequestFilter`). DB schema is sport-agnostic (`sport -> league -> team -> player`); per-sport quirks live only in adapter services; migrations don't know about NBA/NFL.

## CON-multi-sport-routing-pattern
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ARCHITECTURE.md
- type: protocol
- content: Strategy-by-sport-slug routing inside the service tier. Whenever a request could hit a different upstream per sport, the service `switch`es on a sport slug. Two resolution paths: via `league.getSport().getSlug()` (`MatchService.getMatchesByLeagueAndDate`, `LeagueService.getStandings`) and via `team.getSport().getSlug()` (`PlayerService.getPlayerCareerStats` / `PlayerService.resolvePhotoUrl` / `TeamService.getRosterForSeason` — a direct `Team.sport` link added in the V9 team<->league many-to-many refactor, since a team can now belong to several leagues so a league hop would be ambiguous). Canonical LOAD-BEARING slugs: `"football"`, `"basketball"`, `"american-football"` — switch literals must match exactly. Adding a sport = new arm in ALL THREE router methods. One Spring bean per external API; never mix providers in one class.

## CON-rest-request-flow
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ARCHITECTURE.md
- type: protocol
- content: REST flow (e.g. `GET /api/matches?league=1&date=...`): browser axios (`api/client.ts`) attaches `Authorization: Bearer <jwt>` from localStorage if present -> `JwtAuthFilter.doFilterInternal` parses/validates via `JwtUtil`, loads `UserDetails` via `AuthService.loadUserByUsername`, populates `SecurityContextHolder` (fully stateless, no server session) -> `SecurityConfig.filterChain` checks route rules (`/api/users/me/**` `authenticated()` declared BEFORE broad `GET /api/**` `permitAll`; order load-bearing, first match wins; unauthenticated protected hits get 401 JSON via `AuthenticationEntryPoint`) -> controller delegates to service -> service resolves league, walks `getSport().getSlug()`, routes via switch to the adapter -> adapter calls upstream via `RestClient`, maps JSON to `MatchDto` records -> controller wraps in `ResponseEntity.ok(...)`, serialised by Boot's auto-configured `ObjectMapper`.

## CON-websocket-live-push-flow
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ARCHITECTURE.md
- type: protocol
- content: Live-score push (home/live page never polls as primary path): 1) Scheduler `MatchService.refreshLiveMatchCache` fires every 30s (`@Scheduled(fixedDelay = 30_000)`). 2) Fetch football live via `ExternalApiService.fetchLiveMatchDtos()`, then iterate `leagueRepository.findBySport_Slug("basketball")` + `findBySport_Slug("american-football")` for NBA/NFL live games today. 3) Diff — build a snapshot `Map<Long,String>` of `"home:away:status"` vs `previousSnapshot` (a `volatile ConcurrentHashMap`). 4) Push ONLY on change — write combined list to Redis (`cacheManager.getCache("matches").put(SimpleKey.EMPTY, current)`) AND `messagingTemplate.convertAndSend("/topic/matches/live", current)`. 5) STOMP converter (`WebSocketConfig.configureMessageConverters`) serialises with Boot's `ObjectMapper` (has `JavaTimeModule`). 6) Browser `useLiveScores` calls `queryClient.setQueryData(['matches','live'], matches)` — instant re-render, no refetch. 7) REST fallback `GET /api/matches/live` polls every 60s from Redis (`@Cacheable("matches")`, key `SimpleKey.EMPTY`). Diff-then-push avoids flooding clients during quiet ticks.

## CON-authentication-flow
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ARCHITECTURE.md
- type: protocol
- content: Register -> `AuthService.register` checks duplicates (throws `HttpStatus.CONFLICT` via `ResponseStatusException`), BCrypts (`PasswordEncoder` bean in `PasswordConfig`, not `SecurityConfig`), saves `UserAccount`, returns `AuthResponse(token, username)`. Login -> `AuthService.login` calls `authenticationManager.authenticate(...)`; `AuthenticationManager` is `@Lazy`-injected via a manual constructor (not `@RequiredArgsConstructor`) to break a startup cycle. Token minted by `JwtUtil.generateToken` (HMAC + Base64-decoded `jwt.secret`, default 24h `jwt.expiration-ms: 86400000`, jjwt 0.12.x `Jwts.parser().verifyWith(key).parseSignedClaims(token)`). Filter `JwtAuthFilter` (`OncePerRequestFilter`) extracts/validates the Bearer token, populates `SecurityContextHolder`. Client `AuthContext` stores token+username in localStorage; the axios interceptor reads localStorage directly (not React context) so it works before any component renders.

## CON-persistence-osiv-ddl
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ARCHITECTURE.md
- type: schema
- content: All `@ManyToOne` are `fetch = FetchType.LAZY`; walking them (`player.team.league.sport`) triggers SQL on access. OSIV (Open Session In View) is enabled by default — keeps the Hibernate session alive for the full HTTP request, so mappers like `PlayerService.toDto` can walk `player.team.league.sport.slug` without an explicit `@Transactional`. Methods needing explicit transaction scope (writes, or walks outside a web context) are `@Transactional` / `@Transactional(readOnly = true)` (e.g. `MatchService.getMatchesByLeagueAndDate`, `LeagueService.getStandings`, `PlayerService.getPlayerCareerStats`). `spring.jpa.hibernate.ddl-auto: validate` — schema is Flyway-owned; entity changes without a matching migration fail at boot; never edit an applied migration, always add the next version.

## CON-caching-design
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ARCHITECTURE.md
- type: schema
- content: One cache name `matches`, one entry keyed `SimpleKey.EMPTY` (the no-arg `getLiveMatches()` cache key). 30-second TTL set programmatically in `RedisConfig` (overrides YAML default when a custom `RedisCacheManager` is in play). `RedisConfig` builds a custom `ObjectMapper` (`JavaTimeModule` + `DefaultTyping.EVERYTHING`); the no-arg `GenericJackson2JsonRedisSerializer` cannot serialise `LocalDateTime` (silent 500). Same root cause for STOMP (`WebSocketConfig.configureMessageConverters` injects Boot's mapper). `refreshLiveMatchCache` writes manually via `cacheManager.getCache("matches").put(SimpleKey.EMPTY, current)` so WS push and REST fallback see the same data.

## CON-error-handling-contract
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ARCHITECTURE.md
- type: api-contract
- content: `GlobalExceptionHandler` (`@RestControllerAdvice`) is the single source of HTTP-status truth; every exception maps to `ErrorResponseDto(status, error, message, timestamp)`. `MethodArgumentNotValidException` -> 400 (field errors concatenated); `HttpMessageNotReadableException` -> 400 ("Malformed or missing request body"); `ResponseStatusException` -> passthrough (MUST come BEFORE the catch-all); `BadCredentialsException` -> 401 (deliberately vague); `AccessDeniedException` -> 403; `DataIntegrityViolationException` -> 409 (duplicate username/email); `Exception` -> 500 (full stack logged server-side, vague client message). ORDERING RULE: if `@ExceptionHandler(Exception.class)` precedes `@ExceptionHandler(ResponseStatusException.class)`, 404s come back as 500s.

## CON-anti-patterns
- source: /Users/james/Projects/OneStopSports/.planning/cowork/ARCHITECTURE.md
- type: protocol
- content: Real footguns that have caused real bugs — DON'T: use `@Data` on entities (recurses -> StackOverflowError; use the quartet); declare `@ExceptionHandler(Exception.class)` first (catch-all steals `ResponseStatusException` -> 404 becomes 500); use no-arg `GenericJackson2JsonRedisSerializer` (bare ObjectMapper -> 500 on `LocalDateTime`); mix two providers in one `@Service` (differing record shapes/quirks — new provider = new `@Service` + new switch arm); persist live match data (scores change minute-by-minute — keep ephemeral, Redis 30s TTL + scheduler); add a parameter to `getLiveMatches()` (cache key changes from `SimpleKey.EMPTY`, WS push uses the wrong key). Additional constraints: Tomcat thread-per-request; scheduler on a separate thread with `volatile ConcurrentHashMap` snapshot; `UserAccount` not `User` (reserved word); Spring profiles default/local/docker/test (test disables Redis); NBA+NFL convert UTC->ET in code (football stays UTC).

## CON-football-data-org
- source: /Users/james/Projects/OneStopSports/.planning/cowork/INTEGRATIONS.md
- type: api-contract
- provider: football-data.org v4 (`ExternalApiService`)
- content: Auth `X-Auth-Token` header. Free tier = 10 req/min (DataLoader sleeps 6.2s between competition seeds). No match stats, no lineups on free tier (`getMatchStats()`/`getMatchLineups()` return `Map.of()`). Base URL `https://api.football-data.org/v4`. Endpoints: `/competitions/{id}/teams`, `/competitions/{id}/matches?date=`, `/competitions/{id}/standings`, `/matches/{id}`, `/persons/{id}`. Competition IDs seeded: PL=2021, La Liga=2014, Bundesliga=2002, Serie A=2019, Ligue 1=2015, UCL=2001 (mapped via `League.external_id`, Flyway V4). Football timestamps stay UTC (`MatchDto.timezone == null`).

## CON-espn-nba
- source: /Users/james/Projects/OneStopSports/.planning/cowork/INTEGRATIONS.md
- type: api-contract
- provider: ESPN NBA unofficial (`NbaApiService`)
- content: No auth (public). THREE base URLs / THREE RestClient instances: main `https://site.api.espn.com/apis/site/v2/sports/basketball/nba` (teams/rosters/scoreboard), standings `https://site.web.api.espn.com/apis/v2/sports/basketball/nba` (note `site.web.api`), stats `https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba` (`/athletes/{espnId}/stats`). Quirks: roster is a flat `athletes[]`; positions are full names; `dateOfBirth` present; scoreboard team logo is a single `logo` string; standings nest 2 levels (Conference -> Entries); live status `STATUS_IN_PROGRESS` mapped to `"LIVE"`; season label `month >= 10 ? year+1 : year`; scoreboard date format `YYYY-MM-DD`; UTC->ET conversion required. Undocumented — guard with `@JsonIgnoreProperties(ignoreUnknown = true)` + swallow RestClientException -> empty list. Package-private 3-arg test constructor for mock injection.

## CON-espn-nfl
- source: /Users/james/Projects/OneStopSports/.planning/cowork/INTEGRATIONS.md
- type: api-contract
- provider: ESPN NFL unofficial (`NflApiService`)
- content: No auth (public). Same three-URL pattern BUT standings live on `https://site.api.espn.com/apis/v2/sports/football/nfl` (`site.api`, NOT `site.web.api` — footgun vs NBA). Main `https://site.api.espn.com/apis/site/v2/sports/football/nfl`; stats `https://site.web.api.espn.com/apis/common/v3/sports/football/nfl`. Quirks: roster grouped by side (`EspnPositionGroup.items[]` offense/defense/specialTeam — iterate all, ~53 players); no `dateOfBirth`; position abbreviations stored as-is (QB/WR/CB); standings nest 3 levels (Conference -> Division -> Group entries); hardcoded `DIVISION_BY_ABBR` map of all 32 abbreviations (divisions fixed since 2002); scoreboard date format `YYYYMMDD` (no dashes); team/player IDs parsed string -> Long; season label `month < 9 ? year-1 : year`; `NflDataLoader` sleeps 1500ms between roster fetches, skip when >= 32 teams seeded.

## CON-balldontlie
- source: /Users/james/Projects/OneStopSports/.planning/cowork/INTEGRATIONS.md
- type: api-contract
- provider: balldontlie.io v1 (`BallDontLieService`)
- content: Auth `Authorization: <api-key>` header — PLAIN key, NO "Bearer" prefix. Free tier = 5 req/min; only `/players` + `/teams` (no stats). Base URL `https://api.balldontlie.io/v1`. Endpoint `GET /players?search={firstName}&per_page=10`. NBA player bio only (height/weight/college/draft), called lazily on player detail via `GET /api/players/{id}/bio`. Quirks: search matches FIRST NAME only (filter results by lastname in code); hyphenated lastnames one token; weight is a String (parsed, NFE swallowed -> null); snake_case JSON fields; soft-fail -> `Optional.empty()` (bio card hides).

## CON-api-football
- source: /Users/james/Projects/OneStopSports/.planning/cowork/INTEGRATIONS.md
- type: api-contract
- provider: api-sports.io v3 football / "API-Football" (`ApiFootballService`)
- content: Auth `x-apisports-key` header (direct signup); RapidAPI alternative uses `x-rapidapi-key`+`x-rapidapi-host` and base `https://api-football-v1.p.rapidapi.com/v3`. Base URL `https://v3.football.api-sports.io`. TWO free-tier limits BOTH matter: (1) 100 req/DAY; (2) season cap 2024 (`FREE_TIER_MAX_SEASON = 2024`; season > 2024 is plan-blocked; serves 2024-25 as of mid-2026). Two-step flow: `searchPlayerId(name, leagueId, season)` then `fetchPlayerStats(playerId, season)`; step-1 result persisted to `Player.external_id` (Flyway V6). League map (football-data ID -> API-SPORTS ID): 2021->39, 2014->140, 2002->78, 2019->135, 2015->61, 2001->2. Name-matching: search rejects diacritics (strip via Normalizer.NFD); `stripAccents()` on BOTH comparison sides; search term >= 4 chars; resolution = exact case-insensitive full-name -> loose lastname -> `Optional.empty()` (never "first result"); mid-season transfers yield one block per team. Curated columns: APPS, MIN, GOALS, AST, SHOTS, ON, PASS%, YEL, RED, RATING. Soft-fail -> null.

## CON-timezone-handling
- source: /Users/james/Projects/OneStopSports/.planning/cowork/INTEGRATIONS.md
- type: protocol
- content: NBA/NFL game times converted UTC->ET in the backend and stored as a naive `LocalDateTime` (ET wall-clock) via `OffsetDateTime.parse(event.date()).atZoneSameInstant(ZoneId.of("America/New_York")).toLocalDateTime()`. `ZoneId.of("America/New_York")` handles EDT/EST automatically. `MatchDto.timezone` = `"ET"` for NBA/NFL, `null` for football; frontend `formatKickoff(utc, match.timezone)` appends "ET". Football times stay UTC (no conversion).

## CON-cdn-image-urls
- source: /Users/james/Projects/OneStopSports/.planning/cowork/INTEGRATIONS.md
- type: api-contract
- content: NBA team logo `https://a.espncdn.com/i/teamlogos/nba/500/{abbr}.png` (lowercase abbr) -> `team.crest_url` at seed. NFL team logo `https://a.espncdn.com/i/teamlogos/nfl/500/{abbr}.png` -> `team.crest_url`. NBA/NFL player headshot `https://a.espncdn.com/i/headshots/{nba|nfl}/players/full/{espnId}.png` — reconstructed on the fly by `PlayerService.resolvePhotoUrl` from `player.external_id` + sport slug (no DB column). Football crest = football-data.org `crestUrl` persisted at seed. Football player photo = API-Football `player.photo` — CURRENTLY NOT WIRED (football players show no photo).

## CON-auth-identity
- source: /Users/james/Projects/OneStopSports/.planning/cowork/INTEGRATIONS.md
- type: protocol
- content: Self-hosted username/password + JWT bearer (no external IdP). BCrypt via `PasswordEncoder` bean in `config/PasswordConfig.java` (separated to break the JwtAuthFilter->AuthService->PasswordEncoder cycle). JWT signing = HMAC with Base64-encoded secret `jwt.secret`, library jjwt 0.12.6 (`Jwts.parser()`/`.verifyWith(key)`/`.parseSignedClaims(token)`). Token lifetime 24h (`jwt.expiration-ms: 86400000`). `AuthenticationManager` injected `@Lazy` in `AuthService`.

## CON-observability
- source: /Users/james/Projects/OneStopSports/.planning/cowork/INTEGRATIONS.md
- type: nfr
- content: Logging via SLF4J `LoggerFactory.getLogger(...)` with bracketed prefixes (`[NbaApiService] ...`). No metrics/tracing. API docs via Swagger UI at `/swagger-ui/index.html`, raw spec `/v3/api-docs`. Errors returned as consistent `ErrorResponseDto(status, error, message, timestamp)` for every exception type via `GlobalExceptionHandler`.

## CON-adding-new-external-api
- source: /Users/james/Projects/OneStopSports/.planning/cowork/INTEGRATIONS.md
- type: protocol
- content: To add a provider: new `@Service` `<Provider>ApiService.java`; one RestClient from `@Value("${external-api.<provider>.<key>}")`; inner records mirroring provider JSON, each `@JsonIgnoreProperties(ignoreUnknown = true)`; mapper methods to project DTOs; new keys under `external-api.<provider>` in `application.yml` AND `META-INF/additional-spring-configuration-metadata.json`. If a new sport: extend the switch arms in `MatchService.getMatchesByLeagueAndDate`, `LeagueService.getStandings`, `PlayerService.getPlayerCareerStats`; if live scores, extend `MatchService.fetchNonFootballLiveMatches`. Never mix providers.
