# OneStopSports — Custom instructions for Claude

> Paste this into the **Custom instructions** field of the Claude Cowork project.

---

You are working on OneStopSports, a Java 21 + Spring Boot 3.4.4 backend with a React 18 + TypeScript + Vite frontend. Follow these conventions on every change. When unsure of a convention, check `CONVENTIONS.md` (project knowledge).

## Coding style

- **Plain-English inline comments aimed at a junior developer on every new Java file.** Explain *why* (free-tier limits, cycle-prevention, ESPN's empty-score format), not just *what*. Use `// ── Section ─────` dividers in larger files. This is a project-level rule; do not skip it.
- **DTOs are Java 21 records**, never Lombok classes. Suffix every response DTO with `Dto` (`PlayerDto`, `MatchDto`). Request bodies use the `Request` suffix (`RegisterRequest`).
- **Entities use the exact four-annotation Lombok stack** — `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`. **Never `@Data`** — it recurses through bidirectional `@ManyToOne` relationships and stack-overflows.
- **Services use manual constructor injection** (not `@RequiredArgsConstructor`) — kept explicit for clarity.
- **External-API services use Spring 6 `RestClient`** (synchronous), never `WebClient`. URLs come from `@Value("${external-api.<provider>.<key>}")` and must be documented in `META-INF/additional-spring-configuration-metadata.json`.
- **Sport-slug routing** — when work touches multi-sport logic, dispatch on `league.getSport().getSlug()` via a `switch` over `"basketball"` / `"american-football"` / default (`"football"`). Same pattern in `MatchService.getMatchesByLeagueAndDate`, `LeagueService.getStandings`, `PlayerService.getPlayerCareerStats`.

## Critical gotchas — internalise these

- **`ResponseStatusException` `@ExceptionHandler` must appear BEFORE the catch-all `@ExceptionHandler(Exception.class)` in `GlobalExceptionHandler`.** If the catch-all comes first, 404s become 500s.
- **`SecurityConfig` matcher order is load-bearing.** `/api/users/me/**` `authenticated()` MUST be declared before the broad `GET /api/**` `permitAll()` — first match wins, so reversing it bypasses auth on protected GETs. (There's also an `AuthenticationEntryPoint` for clean 401 JSON.)
- **Glassmorphism is the house style** for field-backed surfaces (`.glass-card` over a `SportFieldBackdrop`). Any older "no glassmorphism" guidance is superseded. Reuse `SectionLabel` + `RowCard` for section headings and list surfaces.
- **Accessibility baseline exists** — keep it: there's a global `:focus-visible` ring and a `prefers-reduced-motion: reduce` block (disables Tailwind `animate-pulse/ping/spin` + smooth scroll). New looping animations should be gated the same way; decorative SVG backdrops are `aria-hidden`.
- **MapStruct annotation-processor order is load-bearing** in `pom.xml`: Lombok → `lombok-mapstruct-binding` → MapStruct. Reverse it and MapStruct can't see Lombok-generated getters.
- **jjwt 0.12.x API:** `Jwts.parser()` + `.verifyWith(key)` + `.parseSignedClaims(token)`. NOT 0.11.x's `parserBuilder()` / `setSigningKey()` / `parseClaimsJws()`.
- **`UserAccount` not `User`** — `user` is a PostgreSQL reserved word.
- **Redis serializer must be the custom `RedisConfig` `ObjectMapper`** (has `JavaTimeModule` + `DefaultTyping.EVERYTHING`). The no-arg `GenericJackson2JsonRedisSerializer` cannot serialize `LocalDateTime` and silently 500s any cached match with a `startTime`. Same issue for STOMP — `WebSocketConfig.configureMessageConverters` overrides with Boot's auto-configured `ObjectMapper`.
- **OSIV is on by default.** Methods like `PlayerService.toDto` walk `player.team.league.sport.slug` (three lazy hops). That works only inside a web request. If you need explicit transaction scope (writes, or non-web contexts), mark the method `@Transactional`.
- **`spring.jpa.hibernate.ddl-auto: validate`** — Flyway owns the schema. Entity changes without a matching `V<next>__*.sql` migration fail at boot. Never edit a migration that has been applied — always add the next number.
- **`application.yml` carries placeholder secrets** (`YOUR_API_KEY_HERE`). Real values live in `application-local.yml` (gitignored, for `local` profile) or `.env` (gitignored, for `docker` profile). Never commit real keys.

## External APIs — know these limits before you call

See `INTEGRATIONS.md` (project knowledge) for the full reference. Quick summary:

- **football-data.org**: 10 req/min on free tier, no stats / no lineups.
- **ESPN NBA + NFL**: undocumented public API, no key, three different subdomains per sport (main + standings + stats). Easy footgun: NBA standings use `site.web.api.espn.com`, NFL standings use `site.api.espn.com`.
- **balldontlie.io**: 5 req/min, only `/players` + `/teams` on free tier. Search matches **first name only** — filter the result list in code by last name.
- **api-sports.io** (football stats): **100 req/day** AND **season cap at 2024** on free tier. Search rejects diacritics — strip via `Normalizer.NFD` before sending. Player IDs cached lazily on `Player.external_id`.

## Frontend conventions

- **Tailwind classes must be literal strings** — the JIT compiler can't see `text-${color}-600`. Use full strings in conditionals: `state === 'live' ? 'text-green-600' : 'text-stone-500'`.
- **React Query `staleTime` matches upstream change rate:** 30s for live scores, 5m for standings, 24h for player bio/stats. See `CONVENTIONS.md` for the full table.
- **Every backend record has a matching TS interface** in `frontend/src/types/index.ts`. Same name (preserve the `Dto` suffix). Optional backend fields type as `string | null`, not `| undefined`.
- **One default export per file**; props interface declared above the component and named `Props`. Functional components only.

## Tooling

- **Backend run:** `mvn spring-boot:run -Dspring-boot.run.profiles=local` (port 8081)
- **Frontend run:** `cd frontend && npm run dev` (port 3000, proxies `/api` and `/ws` to 8081)
- **Tests:** `mvn test` (uses H2; Redis is disabled via `application-test.yml`)
- **Full stack via Docker:** `docker-compose up --build`
- **Swagger UI:** `http://localhost:8081/swagger-ui/index.html`

## Project memory

- The user wants every major milestone to refresh the PDF study guide at the project root (per `MEMORY.md`).
- The user's preferred comment style is "explain to a CS student in their first internship".
