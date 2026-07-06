# Decisions Intel

Extracted from ADR-type sources. Precedence: `ADR (0) > SPEC > PRD > DOC`. The single ADR
source carries a manifest precedence override of `0` (highest), so its decisions win over any
contradicting SPEC/PRD/DOC. No decision is `locked` (the source is a multi-decision log, not a
set of individually Accepted-status ADRs), so no LOCKED-vs-LOCKED evaluation applies.

Source ADR: `/Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md` (precedence 0, locked: false)

---

## DEC-espn-over-balldontlie-nba
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: NBA team/roster/scoreboard/standings data source
- decision: Source NBA teams, rosters, scoreboards, and standings from ESPN's unofficial API rather than balldontlie.io. ESPN gives free-tier team logos + standings, matches the NFL provider pattern, needs no API key. balldontlie stays narrowly scoped to NBA player bio enrichment (height/weight/college/draft).
- tradeoff: ESPN is undocumented/unofficial, no SLA/changelog. Mitigated with `@JsonIgnoreProperties(ignoreUnknown = true)` on every record + soft-fail (swallow RestClientException, return empty list).

## DEC-api-football-and-2024-cap
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: football (soccer) player career stats
- decision: Use `v3.football.api-sports.io` for football player career stats; cap the season at 2024 via `FREE_TIER_MAX_SEASON`. football-data.org has no free-tier player stats. Free tier = 100 req/day; season > 2024 is plan-blocked, so clamp to serve real (stale) 2024-25 data instead of an empty card.
- open-issue: UI does not tell users why they see 2024-25 stats (later addressed by the stale-season badge below).

## DEC-espn-cdn-headshots-derived
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: NBA/NFL player photos
- decision: Construct headshot URLs on the fly from `Player.external_id` + `sport.slug` (`https://a.espncdn.com/i/headshots/{nba|nfl}/players/full/{espnId}.png`) rather than persisting a photoUrl column. Zero new DB columns/API calls/seed changes; falls back to null if the pattern changes. Implemented in `PlayerService.resolvePhotoUrl` (persisted photoUrl -> ESPN CDN -> null).

## DEC-lazy-external-id-football
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: football player external_id (API-Football player ID)
- decision: Do not pre-seed API-Football player IDs. Look up on first `/career-stats` request and persist to `Player.external_id` so later requests skip the search. Pre-seeding ~600 players would burn the 100/day budget. NBA/NFL capture ESPN athlete IDs at seed time instead (no rate limit).
- tradeoff: first visit per player pays a search call; unmatched names silently get no stats, no retry.

## DEC-stripaccents-both-sides
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: API-Football name matching
- decision: Accent-strip BOTH sides of the name comparison (search term and post-fetch match-back), both the exact full-name and loose lastname branches, via `stripAccents() + toLowerCase()`. Fixes players like Vinícius / Dembélé / Lukáš matching regardless of which side carries the accent.

## DEC-sport-slug-switch-routing
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: multi-sport external-API routing
- decision: Route between external APIs with a `switch` on `league.getSport().getSlug()` strings, not a polymorphic `SportProvider` interface. Chosen for grep-discoverability, because the three routing methods hit different-signature endpoints, and per-sport quirks are easier inline. Lives in `MatchService.getMatchesByLeagueAndDate`, `LeagueService.getStandings`, `PlayerService.getPlayerCareerStats`.
- tradeoff: adding a sport means updating three switch statements (acceptable — only three sports ever).

## DEC-useraccount-entity-name
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: user JPA entity naming
- decision: Name the user entity `UserAccount` -> table `user_account`, because `user` is a PostgreSQL reserved word. REST paths stay `/api/users/...`.

## DEC-secrets-in-gitignored-yml
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: secrets management
- decision: API keys + JWT secret live in gitignored `application-local.yml` (local) and `.env` (docker), not Vault/AWS Secrets Manager/Doppler. Justified as a one-developer personal project; standard Spring Boot mechanism.
- caveat: JWT placeholder secret looks real — must be overridden in prod (tracked as a security concern).

## DEC-java-records-dtos
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: DTO representation
- decision: Every DTO is a Java 21 `record` (never Lombok class); Jakarta validation annotations go on record components. Gives immutability/equality/toString free, native Jackson deserialisation, and visually distinguishes DTOs (immutable records) from entities (mutable Lombok classes).

## DEC-lombok-quartet-entities
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: JPA entity annotations
- decision: Every entity uses exactly `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` — never `@Data`, because `@Data`'s toString/hashCode/equals recurse through bidirectional relationships and StackOverflow. Codified across all 7 entities.

## DEC-passwordconfig-cycle-break
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: Spring DI wiring
- decision: `PasswordEncoder` bean lives in its own `config/PasswordConfig.java` (not in `SecurityConfig`) to break the cycle `JwtAuthFilter -> AuthService -> PasswordEncoder -> SecurityConfig -> JwtAuthFilter`. Combined with `@Lazy AuthenticationManager` in `AuthService`.

## DEC-restclient-not-webclient
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: external HTTP client
- decision: All external API calls use Spring 6 synchronous `RestClient`, never reactive `WebClient` (webflux is present only for RestClient). The app is otherwise synchronous Servlet; the 30s scheduler has no throughput pressure that needs non-blocking I/O.

## DEC-custom-objectmapper-redis-stomp
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: Redis cache + STOMP serialisation
- decision: `RedisConfig` and `WebSocketConfig` override the default serialiser with a custom `ObjectMapper` (JavaTimeModule + `DefaultTyping.EVERYTHING` for Redis; Boot's auto-configured ObjectMapper injected for STOMP). The bare default cannot serialise `LocalDateTime` and silently 500s any cached match with a startTime.

## DEC-matchdto-timezone-field
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: match time display
- decision: Add a `timezone` field to `MatchDto` (`"ET"` for NBA/NFL, `null` for football). Backend stores ET wall-clock as a naive LocalDateTime via `OffsetDateTime.parse(...).atZoneSameInstant(ZoneId.of("America/New_York")).toLocalDateTime()`; frontend appends "ET". Football times stay UTC.

## DEC-live-status-strings
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: live match status
- decision: Keep ESPN's `"LIVE"` string for NBA rather than normalising to football's `"IN_PLAY"`. Both map to `getMatchState(...) === 'live'` on the frontend; keeping the upstream-native value preserves a debugging paper trail.

## DEC-nfl-division-map-hardcoded
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: NFL standings divisions
- decision: `NflApiService.DIVISION_BY_ABBR` is a hand-maintained `Map.of(...)` of all 32 NFL abbreviations -> divisions. ESPN standings group by conference, not division; NFL divisions are fixed since 2002 so an extra API call is wasteful.
- footgun: a relocated/renamed team abbreviation (OAK -> LV) falls into "Unknown Division" until the map is updated.

## DEC-react-query-staletime-per-type
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: frontend caching
- decision: React Query `staleTime` is calibrated per query type to upstream change rate, not a global default. Live scores/match list/search 30s, favourites 2m, standings/teams/leagues 5m, sports 10m, player bio/stats 24h.

## DEC-tailwind-literal-classes
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0)
- scope: Tailwind usage
- decision: Tailwind classes are always complete literal strings (ternary branches allowed, each a full literal). Never dynamic interpolation — the JIT compiler purges class names it cannot see at build time (`text-${color}-${shade}` breaks silently in prod).

## DEC-inline-comments-junior
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0) — MANDATORY
- scope: Java code comments
- decision: Every new Java file carries plain-English inline comments aimed at a junior developer (class header, field rationale, inline notes on non-obvious decisions, `// -- Section --` dividers). Explain the why (cycle prevention, free-tier limits, ESPN quirks). Mandated by user memory; retroactively applied to all ~50 files. This is mandatory, not a style preference.

## DEC-security-matcher-order-entrypoint
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0), post-QA commit bc1a890
- scope: Spring Security authorization
- decision: In `SecurityConfig`, declare `/api/users/me/**` `authenticated()` BEFORE the broad `GET /api/**` `permitAll()` (first match wins; reversed order was a real auth bypass). Add an `AuthenticationEntryPoint` returning a 401 JSON envelope instead of an empty 403.

## DEC-500-to-4xx-handlers
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0), post-QA
- scope: exception -> HTTP status mapping
- decision: `GlobalExceptionHandler` maps `MethodArgumentTypeMismatchException` -> 400, `MissingServletRequestParameterException` -> 400, `HttpRequestMethodNotSupportedException` -> 405, instead of falling to the 500 catch-all. `ResponseStatusException` handler must precede the `Exception` catch-all.

## DEC-accessibility-baseline
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0), post-QA
- scope: accessibility CSS baseline
- decision: Global `:focus-visible` outline (Tailwind Preflight strips defaults; WCAG 2.4.7) + a `prefers-reduced-motion: reduce` block disabling Tailwind's built-in `animate-pulse/ping/spin` + smooth scroll (WCAG 2.3.3).
- note: glassmorphism via `.glass-card` IS the house style for field-backed surfaces; any older "no glassmorphism" guidance is superseded.

## DEC-football-stale-season-badge
- source: /Users/james/Projects/OneStopSports/.planning/cowork/DECISIONS.md
- status: settled (precedence 0), post-QA
- scope: football career-stats UI honesty
- decision: `CareerStatsTable` renders a "Showing the {season} season — most recent available on the current data plan" note for football, because api-sports.io free tier caps at season 2024 and a year-old season with no caveat reads as current. This resolves the open-issue noted under DEC-api-football-and-2024-cap.
