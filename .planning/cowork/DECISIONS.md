# OneStopSports — Decisions Log

> **What this doc is:** A "why we picked X over Y" record of the non-obvious choices already settled in this codebase. Read this when something looks weird and you're tempted to "fix" it — there's usually a reason. Use it to avoid re-litigating decisions you don't yet have full context for.

---

## ESPN over balldontlie for NBA team data

**Choice:** Source NBA teams, rosters, scoreboards, and standings from ESPN's unofficial API rather than balldontlie.io.

**Why:**
- ESPN provides **team logos** on the free tier — balldontlie doesn't
- ESPN provides **standings** on the free tier — balldontlie's standings require their paid plan
- ESPN matches the same pattern we'd use for NFL (one API provider, two sports) — keeps the codebase shape consistent
- No API key required — one fewer secret to manage

**Tradeoff:** ESPN's API is undocumented and unofficial. No SLA. No changelog. A silent rename or removal breaks us. We mitigate with `@JsonIgnoreProperties(ignoreUnknown = true)` on every record and soft-fail behaviour (swallow `RestClientException`, return empty list).

**balldontlie is still in the stack** — just narrowly scoped to player bio enrichment (height, weight, college, draft info), which ESPN doesn't expose. Best of both.

## API-Football for soccer player stats (and the 2024 cap)

**Choice:** Use `v3.football.api-sports.io` for football (soccer) player career stats. Cap the season at 2024 via the `FREE_TIER_MAX_SEASON` constant.

**Why:**
- football-data.org (our main football source) **does not provide player stats on free tier** — discovered when wiring `/api/players/{id}/career-stats`
- API-Football was the most generous free-tier option researched (100 req/day)
- Direct api-sports.io signup is simpler than RapidAPI's marketplace proxy (single header, no host header dance)

**Cap rationale:** The free tier returns an explicit `{"errors": {"plan": "Free plans do not have access to this season..."}}` for any `season > 2024`. The clamp lets users see real (if slightly stale) 2024-25 data instead of an empty stats card. When the user upgrades, bump or remove the constant.

**Open issue:** The UI doesn't tell users why they're seeing 2024-25 stats — they may think it's current. See ROADMAP.md.

## ESPN CDN headshot URLs derived from `externalId` (not persisted)

**Choice:** For NBA / NFL player photos, construct the URL on the fly from `Player.external_id + sport.slug` rather than persisting a `photoUrl` column for each player.

**Why:**
- ESPN's URL pattern is **deterministic**: `https://a.espncdn.com/i/headshots/{nba|nfl}/players/full/{espnId}.png`
- We already store `external_id` on every NBA/NFL player (it's how we hit career stats), so the URL is reconstructable from data we already have
- **Zero new DB columns, zero new API calls, zero seed-time changes** — the headshot is just a derivable field
- Falls back gracefully — if ESPN ever changes the URL pattern, we just stop showing photos rather than serving 404s

**Where:** `PlayerService.resolvePhotoUrl` — three layers (persisted `Player.photoUrl` → ESPN CDN URL → null). Football players will fall through to layer 1 once the lazy-capture write is wired (see ROADMAP.md).

## Lazy `external_id` lookup for football players

**Choice:** For football players, don't pre-seed the API-Football player ID. Look it up on the first `/career-stats` request and persist the result so subsequent requests skip the search.

**Why:**
- API-Football has a **100 req/day** free-tier limit. Pre-seeding ~600 football players would burn the entire daily budget on seeding alone.
- Many players will never have their stats page opened — pre-seeding them is wasted budget.
- The search step is the expensive part; once we have the API-Football ID, direct lookups are cheap.
- React Query's 24h `staleTime` on the frontend means subsequent visits from the same browser don't even hit the backend.

**Tradeoff:** First visit per player pays the cost of a search call. Players whose names don't match (typos, name variants, players not in API-Football's database) silently get no stats — no retry mechanism.

**Compare to NBA/NFL:** ESPN athlete IDs are captured **at seed time** by `NbaDataLoader` / `NflDataLoader`, populating `Player.external_id` for every player. No lazy lookup needed because ESPN has no rate limit.

## `stripAccents` on both sides of the comparison

**Choice:** API-Football's search term is accent-stripped (the API rejects diacritics), and **both sides** of the post-fetch name comparison are also accent-stripped before equality.

**Why:** Originally only the search term was normalised, but the match-back logic compared accented strings on both sides. For a player like "Vinícius":
1. Search sends "Vinicius" (stripped) → API returns "Vinicius" in `lastname` field
2. Old code: compare API's "Vinicius" (no accent) against our DB's "Vinícius" (accent) → mismatch
3. Player silently invisible

Both branches — exact full-name match and loose lastname fallback — now run through `stripAccents() + toLowerCase()` before equality. Players like "Vinícius" / "Dembélé" / "Lukáš" match correctly regardless of which side carries the accents.

## Multi-sport routing via `sport.slug` switch (not polymorphism)

**Choice:** Route between external APIs using a `switch` statement on `league.getSport().getSlug()` strings rather than via a `SportProvider` interface with polymorphic adapters.

**Why:**
- **Discoverability** — anyone grepping for `"basketball"` finds every routing decision instantly
- The three routing methods (`MatchService.getMatchesByLeagueAndDate`, `LeagueService.getStandings`, `PlayerService.getPlayerCareerStats`) each route to **different upstream endpoints with different signatures** — no clean interface unifies them without contortion
- Each sport has provider-specific quirks (NBA's `externalId` is the league ID, football's is the competition ID, etc.) that are easier to express inline than to abstract behind a uniform interface
- Switch statements are exhaustive in modern Java — adding a new sport surfaces every place that needs updating

**Tradeoff:** Adding a new sport means updating three switch statements (not one polymorphic registration). Acceptable — there have only ever been three.

## `UserAccount` (not `User`)

**Choice:** The JPA entity for users is named `UserAccount`, mapping to table `user_account`.

**Why:** `user` is a **reserved word in PostgreSQL** (per the SQL standard). Naming the entity `User` would mean either quoting `"user"` in every query (ugly, easy to forget) or working around it at runtime. `UserAccount` sidesteps the entire problem.

**Side effects:**
- REST paths still use `/api/users/...` (idiomatic English wins)
- The mapping triad (`UserAccount` entity → `user_account` table → `/api/users` paths) is mildly confusing — documented in comments

## `application-local.yml` gitignore (not Vault / external secret manager)

**Choice:** Real API keys and JWT secrets live in `src/main/resources/application-local.yml` (gitignored) for local dev and `.env` (gitignored) forwarded as env vars for Docker. No HashiCorp Vault, no AWS Secrets Manager, no Doppler.

**Why:**
- This is a **personal side-project** — there's exactly one developer and one deploy target
- A secret manager would be over-engineering and add a dependency
- The pattern is well-understood Spring Boot — `application-{profile}.yml` is the documented mechanism
- The placeholders in `application.yml` (`YOUR_API_KEY_HERE`) make missing-secret errors obvious

**Caveat to remember:** the JWT secret placeholder is a real-looking Base64 string. Don't accidentally ship it to production without overriding. (Tracked in ROADMAP.md security concerns.)

## Java 21 records for DTOs (not Lombok classes)

**Choice:** Every DTO is a Java 21 `record`. Even when validation annotations are needed, they go on record components directly.

**Why:**
- Records give us **immutability, equality, and toString** for free with no boilerplate
- They cannot inherit, which is fine — DTOs don't need inheritance
- Jackson 2.x (and Spring Boot 3) deserialise records natively — no extra config
- Jakarta validation annotations work on record components without modification
- They visually distinguish DTOs from entities at a glance (entities are mutable Lombok classes, DTOs are immutable records)

**Why not Lombok DTOs:** Mutability isn't needed for the wire format, and `@Value`/`@Data` would visually blur the line between entities and DTOs.

## Lombok quartet on entities (never `@Data`)

**Choice:** Every JPA entity uses the exact stack `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`. Never `@Data`.

**Why:** `@Data` generates `toString` / `hashCode` / `equals` that recurse through bidirectional `@ManyToOne` / `@OneToMany` relationships and **stack-overflow** when an entity is logged or placed in a `Set` or `HashMap`. The explicit quartet gives us getters/setters/builder/constructors without the recursive methods.

**Where this bit us:** Initial entity design used `@Data` and crashed the first time a `Match` was logged. Fixed by switching to the quartet; the fix is now codified across all 7 entities.

## `PasswordConfig` extracted from `SecurityConfig` (cycle break)

**Choice:** The `PasswordEncoder` bean lives in its own `config/PasswordConfig.java` rather than inside `SecurityConfig`.

**Why:** The original wiring had a cycle: `JwtAuthFilter → AuthService → PasswordEncoder → SecurityConfig → JwtAuthFilter`. Moving `PasswordEncoder` to a standalone `@Configuration` with no other dependencies breaks the cycle cleanly. Combined with `@Lazy AuthenticationManager` injection in `AuthService`, the app boots without a circular reference.

## `RestClient` (synchronous) — not `WebClient`

**Choice:** All external API calls use Spring 6's `RestClient` (synchronous, blocking). `spring-boot-starter-webflux` is in the dependencies (for `RestClient`) but we never use the reactive stack.

**Why:**
- The entire app is otherwise synchronous Servlet — mixing reactive WebClient would force `block()` / `Mono → CompletableFuture` adapters everywhere
- `RestClient` is the modern Spring 6 idiom for synchronous HTTP; lighter than `RestTemplate`, less ceremony than `WebClient`
- The scheduler runs once every 30s — no throughput pressure that would benefit from non-blocking I/O
- Service classes stay simple — `restClient.get().uri(...).retrieve().body(EspnTeamsResponse.class)` reads top-to-bottom

## Custom `ObjectMapper` for Redis cache + STOMP converter

**Choice:** `RedisConfig` and `WebSocketConfig` both override the default serialiser to use a custom `ObjectMapper` with `JavaTimeModule` registered.

**Why:** The no-arg `GenericJackson2JsonRedisSerializer()` constructor creates a bare `ObjectMapper` that **cannot serialise `LocalDateTime`** (no Java time module). Any cached match with a `startTime` would silently 500. Same root cause for STOMP — the default STOMP `MappingJackson2MessageConverter` also creates a bare mapper.

The fix: register `JavaTimeModule` + `DefaultTyping.EVERYTHING` on the cache serialiser, and inject Boot's auto-configured `ObjectMapper` into the STOMP converter list. Both fixes are codified in their respective `@Configuration` classes.

## `MatchDto.timezone` field for ET display

**Choice:** Add a `timezone` field to `MatchDto` (`"ET"` for NBA/NFL games, `null` for football). Frontend appends "ET" to the time string when set.

**Why:** ESPN returns game times as UTC ISO-8601. Originally the backend used `.toLocalDateTime()` which strips the offset, producing a naive `LocalDateTime` that displayed in the browser's locale — a "7:30 PM ET" Knicks game showed as "11:30 PM" in an Ireland browser.

The fix lives in both adapters:
```java
startTime = OffsetDateTime.parse(event.date())
        .atZoneSameInstant(ZoneId.of("America/New_York"))
        .toLocalDateTime();
```
This stores the **ET wall-clock time** as a naive `LocalDateTime`. The `timezone: "ET"` field tells the frontend to append the label so users know the time is in ET, not their local timezone. Football times stay UTC (football-data.org gives them that way; the user is expected to interpret in their own locale).

## "Live" status `"LIVE"` for NBA, `"IN_PLAY"` for football

**Choice:** Keep ESPN's `"LIVE"` status string for NBA games rather than normalising to football's `"IN_PLAY"`.

**Why:**
- Both strings end up at `getMatchState(status) === 'live'` on the frontend (in `types/index.ts`) — UI behaviour is identical
- Normalising would require an extra mapping layer that adds nothing
- Keeping the upstream-native value preserves a paper trail for debugging — if a game shows wrong state, we know to look at ESPN's response directly without unwinding a translation

The frontend's `getMatchState` switch handles both: `case 'IN_PLAY': case 'LIVE': return 'live'`.

## NFL division mapping hardcoded (not API-derived)

**Choice:** `NflApiService.DIVISION_BY_ABBR` is a hand-maintained `Map.of(...)` of all 32 NFL team abbreviations → division names.

**Why:**
- ESPN's standings response groups by conference but NOT by division — the divisions are only available on a separate endpoint
- NFL divisions have been **fixed since 2002** — they don't change. Adding an extra API call per standings render to fetch them would be wasteful
- A hardcoded map costs ~30 lines, runs in constant time, and is easy to read

**Footgun:** If a team relocates and changes abbreviation (OAK → LV in 2020), the team falls into "Unknown Division" until the map is updated. Worth a comment to that effect.

## React Query `staleTime` matched to upstream change rate

**Choice:** `staleTime` per query type is calibrated to how often the data actually changes upstream — not a global default.

**Why:** A single global `staleTime` would either burn requests refetching live scores too often or cache static data (sports list) too aggressively.

The table is documented in CONVENTIONS.md:
- Live scores: 30s (matches the 30s WebSocket scheduler)
- Match list / search: 30s
- Favourites: 2m
- Standings: 5m
- Teams/leagues: 5m
- Sports: 10m
- Player bio/stats: 24h (the data really doesn't change daily)

## Tailwind: literal class strings only

**Choice:** Tailwind classes are always written as **complete literal strings** (possibly inside conditionals). Never dynamically composed.

**Why:** The Tailwind JIT compiler scans the source for class name strings and purges anything it doesn't see. `text-${color}-${shade}` produces `text-green-600` only at runtime — the JIT never sees it, the class is purged, and the styling silently breaks in production.

**Acceptable:** ternary expressions where each branch is a literal:
```tsx
className={state === 'live' ? 'text-green-600' : 'text-stone-500'}
```

**Forbidden:** any string interpolation that produces a class name dynamically.

## Plain-English inline comments aimed at a junior developer

**Choice:** Every new Java file carries plain-English inline comments — class header, field rationale, inline notes on non-obvious decisions, `// ── Section ─────` dividers in larger files.

**Why:** User memory file explicitly requests this. Treats the codebase as a teaching project. Has been retroactively applied to all ~50 existing files. The tone target is "explain to a CS student in their first internship". Mention the why (cycle prevention, free-tier limits, ESPN quirks), not just the what.

This is **mandatory** — not a style preference. New code without comments doesn't match the codebase.

## Security matcher order + AuthenticationEntryPoint (post-QA, commit `bc1a890`)

**Choice:** In `SecurityConfig`, declare `/api/users/me/**` `authenticated()` BEFORE the broad `GET /api/**` `permitAll()`, and add an `AuthenticationEntryPoint` that returns a 401 JSON envelope.

**Why:** Spring evaluates matchers top-to-bottom, first match wins. With the broad GET permitAll first, a `GET /api/users/me/...` matched the public rule and skipped auth entirely — a real bypass that only avoided leaking data via a null-principal NPE → 500. A QA persona found it. Order now guarantees the auth check runs; the entry point replaces the empty 403 with a proper 401 body.

## 500 → 4xx exception handlers (post-QA)

**Choice:** `GlobalExceptionHandler` maps `MethodArgumentTypeMismatchException`→400, `MissingServletRequestParameterException`→400, `HttpRequestMethodNotSupportedException`→405.

**Why:** These bubbled to the generic `Exception`→500 catch-all, so `/players/abc`, `/search` without `q`, and wrong HTTP verbs all returned 500s. Each is a client error and deserves the right 4xx.

## Accessibility baseline (post-QA)

**Choice:** Global `:focus-visible` outline; `prefers-reduced-motion: reduce` block disabling Tailwind's built-in `animate-pulse/ping/spin` + smooth scroll.

**Why:** Tailwind Preflight strips default focus outlines, leaving the app unusable by keyboard (WCAG 2.4.7). Our *custom* field animations were already reduced-motion-gated, but the built-in Tailwind animations were not (WCAG 2.3.3). These are two small global CSS rules that fix both app-wide. (Note: glassmorphism via `.glass-card` IS now part of the house style for field-backed surfaces — any older "no glassmorphism" guidance is superseded.)

## Football stale-season badge (post-QA)

**Choice:** `CareerStatsTable` renders a "Showing the {season} season — most recent available on the current data plan" note for football.

**Why:** api-sports.io free tier caps at season 2024, so football career stats lag the live season. Presenting a year-old season with no caveat reads as current/wrong data. The badge makes the limitation honest rather than hidden.
