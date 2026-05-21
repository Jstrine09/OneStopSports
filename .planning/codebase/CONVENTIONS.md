# Coding Conventions

**Analysis Date:** 2026-05-21

This document captures the conventions actually followed throughout OneStopSports — backend (Java 21 / Spring Boot 3.4.4) and frontend (React 18 / TypeScript 5.5 / Vite). New code must match these patterns; deviations require justification.

---

## Backend (Java)

### Package Naming

Single root package: `com.onestopsports`. One sub-package per layer — no further nesting.

```
com.onestopsports
├── config        @Configuration + CommandLineRunner classes
├── controller    @RestController + GlobalExceptionHandler
├── dto           Java records (one per file)
├── model         JPA @Entity classes
├── repository    Spring Data JpaRepository interfaces
├── security      JwtUtil + JwtAuthFilter
└── service       *Service classes (business logic + external APIs)
```

Each new class goes into exactly one of these — no `com.onestopsports.service.football` style sub-packages.

### Comment Style — MANDATORY

**Every new Java file must contain plain-English inline comments aimed at a junior developer.** This is a project-level rule from `~/.claude/projects/-Users-james-Projects-OneStopSports/memory/MEMORY.md` and is applied consistently to all 50 existing files.

Verified by sampling: `UserAccount.java`, `Player.java`, `AuthService.java`, `MatchService.java`, `NbaApiService.java`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, `LeagueRepository.java`, `RegisterRequest.java`, `MatchController.java`.

**Required style:**

1. **Class-level comment** explaining what the class does and any non-obvious "why".
   ```java
   // Handles user registration and login.
   // Also implements UserDetailsService so Spring Security knows how to load a user
   // from the database when checking a JWT token.
   @Service
   public class AuthService implements UserDetailsService { ... }
   ```

2. **Field-level comments** when the field's purpose isn't obvious from its name.
   ```java
   private String passwordHash; // The password is NEVER stored as plain text.
                                // BCrypt hashes it first so even if the DB is leaked, passwords are safe.
   ```

3. **Method-level header** describing what the method does AND what to do for edge cases. Use Javadoc for public service methods that other devs will call; use plain `//` blocks for private helpers.

4. **Inline rationale** for any non-obvious decision — sport-routing switches, lazy-init annotations, rate-limit sleeps, parsing fallbacks. Example from `AuthService.java:32-36`:
   ```java
   // @Lazy on AuthenticationManager breaks a circular dependency:
   // SecurityConfig needs AuthService (to set it as UserDetailsService),
   // but AuthService would need AuthenticationManager which lives in SecurityConfig.
   // @Lazy tells Spring to create the AuthenticationManager only when it's first actually used
   // (i.e. on the first login request), breaking the startup cycle.
   ```

5. **Section dividers** in larger files using `// ── Section Name ─────`. Example from `NbaApiService.java`:
   ```java
   // ── API Response Records ──────────────────────────────────────────────────
   // ── Public API Methods ────────────────────────────────────────────────────
   // ── Private Mapper Methods ────────────────────────────────────────────────
   ```

**Tone:** explanatory, not academic. Aim for "explain to a CS student in their first internship". Mention the *why* (cycle prevention, free-tier limits, ESPN's empty-string score format) — not just the *what*.

### DTOs — Java Records

All 17 DTOs in `src/main/java/com/onestopsports/dto/` are Java 21 records. No Lombok DTOs.

```java
public record PlayerDto(
        Long id,
        String name,
        String position,
        // ...
        Long teamId
) {}
```

**Validation annotations** go on record components directly:
```java
public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password
) {}
```

**File naming:** every DTO ends in `Dto` (`PlayerDto`, `MatchDto`, `StandingsEntryDto`, `ErrorResponseDto`). The only exceptions are request bodies for auth/favorites: `AuthRequest`, `RegisterRequest`, `FavoriteTeamRequest`, `FavoritePlayerRequest` — and `AuthResponse`. These predate the `Dto` convention and are kept for compatibility.

### Entities — Lombok (never `@Data`)

All entities in `src/main/java/com/onestopsports/model/` use the exact four-annotation Lombok stack:

```java
@Entity
@Table(name = "user_account")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount { ... }
```

**Never use `@Data` on entities** — it generates `toString`/`hashCode`/`equals` that recurse through bidirectional `@ManyToOne` / `@OneToMany` relationships and cause `StackOverflowError`.

**Other entity rules:**
- `@ManyToOne` relationships are always `fetch = FetchType.LAZY` (e.g. `Player.team`, `League.sport`).
- `@CreationTimestamp @Column(updatable = false) private LocalDateTime createdAt;` on every entity that needs an audit trail.
- One reserved-word workaround: `UserAccount` (the class) maps to `user_account` (the table). NEVER name it `User` — `user` is a reserved word in PostgreSQL and the schema will fail.
- `@Column(unique = true, nullable = false, length = N)` for required unique fields.

### Naming

| Item | Pattern | Example |
|---|---|---|
| Entity | `PascalCase`, singular | `UserAccount`, `League` |
| Table | `snake_case`, singular | `user_account`, `favorite_team` |
| DTO | `PascalCase` + `Dto` suffix | `PlayerDto`, `MatchDto` |
| Request body DTO | `PascalCase` + `Request` | `RegisterRequest`, `AuthRequest` |
| Service | `PascalCase` + `Service` | `AuthService`, `MatchService` |
| External-API service | Provider/sport in name + `Service` | `NbaApiService`, `NflApiService`, `ApiFootballService`, `BallDontLieService`, `ExternalApiService` (football-data.org — historical name, kept for migration churn reasons) |
| Controller | `PascalCase` + `Controller` | `MatchController`, `AuthController` |
| Repository | `PascalCase` + `Repository` | `LeagueRepository` |
| Repository derived method | `findBy*` / `existsBy*` / `countBy*` | `findByLeagueId`, `existsByTeamIdAndExternalIdIsNull`, `findBySport_Slug` |
| Constants | `UPPER_SNAKE_CASE` | `TEST_DATE`, `COMPETITION_IDS` |
| Inner records (external APIs) | Provider prefix | `EspnTeam`, `EspnEvent`, `ApiMatch`, `ApiCoach` |

**Repository derived queries — examples from `LeagueRepository.java`:**
```java
List<League> findBySportId(Long sportId);
Optional<League> findByExternalId(Integer externalId);
List<League> findBySport_Slug(String sportSlug);  // Underscore = traverse relationship
```

**Repository — JPQL avoided.** Every method in every repository in this codebase is a derived query (parsed from the method name). No `@Query` annotations anywhere.

### Service Layer

- One `*Service` class per concern. Constructors use manual injection (not `@RequiredArgsConstructor` from Lombok — kept explicit for clarity).
- `@Service` annotation on every service class.
- External-API services use `RestClient` (synchronous), never `WebClient`. URLs injected via `@Value("${external-api.<provider>.<key>}")`.
- Logging via SLF4J: `private static final Logger log = LoggerFactory.getLogger(<Class>.class);` — never `System.out.println`.
- Bracketed prefix on log messages: `log.warn("[NbaApiService] fetchStandings failed for season={}: {}", season, e.getMessage());`

### Multi-Sport Routing

Routing logic lives in the service layer and switches on `sport.getSlug()` strings — never on enums, never on instance-of checks.

Sport slugs (canonical):
| Slug | Sport | Service |
|---|---|---|
| `"football"` | Football / soccer | `ExternalApiService` (football-data.org) |
| `"basketball"` | NBA | `NbaApiService` (ESPN) |
| `"american-football"` | NFL | `NflApiService` (ESPN) |

**Canonical routing pattern** (`MatchService.getMatchesByLeagueAndDate` at `src/main/java/com/onestopsports/service/MatchService.java:97-119`):

```java
@Transactional(readOnly = true)
public List<MatchDto> getMatchesByLeagueAndDate(Long leagueId, LocalDate date) {
    if (leagueId == null || date == null) return Collections.emptyList();

    return leagueRepository.findById(leagueId).map(league -> {
        String sportSlug = league.getSport().getSlug();
        return switch (sportSlug) {
            case "basketball" ->
                nbaApiService.fetchGameDtosByDate(date, league.getId());
            case "american-football" ->
                nflApiService.fetchGameDtosByDate(date, league.getId());
            default ->
                league.getExternalId() != null
                    ? externalApiService.fetchMatchDtosByCompetition(league.getExternalId(), date)
                    : Collections.<MatchDto>emptyList();
        };
    }).orElse(Collections.emptyList());
}
```

Same pattern is used in `LeagueService.getStandings()` and `PlayerService.getPlayerCareerStats()`.

### `@Transactional` Pattern

Any service method that walks a lazy `@ManyToOne` chain (`league.getSport()`, `player.getTeam().getLeague()`) needs `@Transactional(readOnly = true)` so the Hibernate session stays open. Always use `readOnly = true` for read paths — it lets Hibernate skip dirty-checking.

### Null Handling

- Guard clauses at the top of public service methods: `if (leagueId == null) return Collections.emptyList();`
- Repositories return `Optional<T>` for `findBy*Id` — never `null`. Callers use `.map(...)` / `.orElseThrow(...)` / `.orElse(...)`.
- External API responses are checked for `null` before walking nested fields. `RestClient.body(...)` can return null on a 204 or empty response.
- DTOs prefer `null` over default values when data is missing (e.g. `MatchDto.homeScore` is `null` for scheduled games — never `0`). Frontend renders "--" on null.

### Exception Handling

- Service-layer errors that should map to HTTP statuses throw `ResponseStatusException(HttpStatus.X, "message")`. Example: `AuthService.register` throws `HttpStatus.CONFLICT` for duplicate usernames.
- All exceptions are centralised in `GlobalExceptionHandler` (`controller/GlobalExceptionHandler.java`) — a single `@RestControllerAdvice` class that maps every common exception type to an `ErrorResponseDto`.
- **CRITICAL ORDERING:** `ResponseStatusException` must have its own `@ExceptionHandler` BEFORE the generic `Exception.class` handler — otherwise the catch-all intercepts it and returns 500 instead of the correct status.
- Never expose raw stack traces, SQL errors, or internal messages to the client. The `Exception.class` catch-all logs `ex` on the server but returns a generic "An unexpected error occurred" to the client.
- External-API services swallow `RestClientException` (timeouts, off-season endpoints, structure changes) and return empty lists / null. The user sees "No data available" instead of a 500.

### Config Injection

- Use `@Value("${path.to.key}")` constructor injection — never field injection.
- All external-API config lives under a single `external-api.*` namespace in `application.yml`:
  ```
  external-api.football-data.base-url
  external-api.football-data.api-key
  external-api.nba.base-url
  external-api.nba.standings-url
  external-api.nba.stats-url
  external-api.nfl.base-url
  external-api.balldontlie.base-url
  external-api.api-football.base-url
  ```
- **Documented for IDE auto-complete:** every key in `application.yml` has a matching entry in `src/main/resources/META-INF/additional-spring-configuration-metadata.json` with a description. New config keys must add an entry there too.
- Secrets (API keys, JWT secret) NEVER live in `application.yml` — they're overridden in `application-local.yml` (gitignored) or via env vars in `application-docker.yml`.
- JWT secret is **Base64-encoded** in config (jjwt 0.12.x requirement).

### External-API Response Records

Every external API service has a `// ── API Response Records ─────` section containing nested public records that mirror the upstream JSON shape:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record EspnTeamsResponse(List<EspnSport> sports) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record EspnSport(List<EspnLeague> leagues) {}
```

**Rules:**
- Every record is annotated `@JsonIgnoreProperties(ignoreUnknown = true)` so adding/removing upstream fields doesn't crash deserialisation.
- Records are `public` (not `private`) so test classes in the same package can construct them as fixtures.
- Name prefix matches the provider: `Espn*` (NBA/NFL), `Api*` (football-data.org). API-Football and balldontlie do the same within their service classes.

### Configuration Classes (`config/`)

- `PasswordConfig` lives separately from `SecurityConfig` to break the circular dependency that arose from `JwtAuthFilter → AuthService → PasswordEncoder` if it had stayed in `SecurityConfig`.
- `RedisConfig` builds a custom `ObjectMapper` (`JavaTimeModule` + `DefaultTyping.EVERYTHING`) — never use the no-arg `GenericJackson2JsonRedisSerializer`; it can't handle `LocalDateTime`.
- `WebSocketConfig` overrides `configureMessageConverters` to inject Spring Boot's auto-configured `ObjectMapper` (same root cause as Redis).
- `DataLoader`, `NbaDataLoader`, `NflDataLoader` implement `CommandLineRunner`. Each one has an idempotent skip-check at the top so re-running the app doesn't re-seed.

### Controllers

- `@RestController` + `@RequestMapping("/api/<resource>")` — never `@Controller` returning views.
- Constructor injection, manual constructor (no `@RequiredArgsConstructor`).
- Method per endpoint. Return `ResponseEntity<T>` for non-204 responses, even on 200 — `return ResponseEntity.ok(service.getX(id));`
- Path variables use `@PathVariable`. Query params use `@RequestParam(required = false)` when optional.
- Dates parsed via `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date` — never plain `String` + manual parsing.
- Request bodies use `@Valid @RequestBody RegisterRequest req` so Jakarta validation annotations on the record components fire automatically (caught by `GlobalExceptionHandler.handleValidation` → 400).

---

## Frontend (TypeScript / React)

### File Naming

| Item | Location | Pattern |
|---|---|---|
| Page component | `frontend/src/pages/` | `PascalCase` + `Page.tsx` (`LivePage.tsx`, `TeamDetailPage.tsx`) |
| Reusable component | `frontend/src/components/` | `PascalCase.tsx` (`MatchCard.tsx`, `StandingsTable.tsx`) |
| Layout component | `frontend/src/layout/` | `PascalCase.tsx` (`Sidebar.tsx`, `BottomNav.tsx`) |
| Hook | `frontend/src/hooks/` | `useCamelCase.ts` (`useLiveScores.ts`) |
| API client | `frontend/src/api/` | `lowercase.ts` (`matches.ts`, `client.ts`) |
| Context | `frontend/src/context/` | `PascalCase` + `Context.tsx` (`AuthContext.tsx`) |
| Types | `frontend/src/types/index.ts` | single barrel file |

### TypeScript Interface Naming — Mirrors Backend

Every backend Java record has a matching TypeScript `interface` with the same name in `frontend/src/types/index.ts`. The frontend's contract IS the backend's contract.

```ts
export interface PlayerDto {
  id: number
  name: string
  position: string | null
  // ...
  teamId: number
}
```

**Rules:**
- Suffix `Dto` is preserved (`PlayerDto`, not `Player`) — direct parity with backend record names.
- Optional / null backend fields are typed as `string | null`, not `string | undefined`. Backend JSON sends literal `null`, not undefined.
- New backend records require a matching frontend interface added in the same PR.

### React Query — Standardised `staleTime` Conventions

All data fetching uses `@tanstack/react-query` v5. `staleTime` matches how often the data actually changes upstream:

| Data type | `staleTime` | Example |
|---|---|---|
| Live scores | `30_000` (30s) | `queryKey: ['matches', 'live']` |
| Match list for a date | `30_000` | `queryKey: ['matches', leagueId, date]` |
| Standings | `5 * 60_000` (5m) | `queryKey: ['standings', leagueId]` |
| Team list / league list | `5 * 60_000` | `queryKey: ['leagues', sportSlug]` |
| User favourites | `2 * 60_000` | `queryKey: ['favorites', 'teams']` |
| Sports (rarely change) | `10 * 60_000` (10m) | `queryKey: ['sports']` |
| Search results | `30_000` | `queryKey: ['search', trimmed]` |

**Query key convention:** array of `[resource, ...identifiers]`. First element is the noun (`'matches'`, `'standings'`, `'favorites'`); subsequent elements narrow it down (id, date, sub-resource).

**Conditional queries:** use `enabled` for queries that depend on user input:
```ts
useQuery({
  queryKey: ['search', trimmed],
  queryFn: () => searchAll(trimmed),
  enabled: trimmed.length >= 2,
  staleTime: 30_000,
})
```

**Cache invalidation:** mutations call `queryClient.invalidateQueries({ queryKey: ['favorites', 'teams'] })`. WebSocket pushes use `queryClient.setQueryData(['matches', 'live'], updatedMatches)` to skip the refetch and re-render immediately.

### API Client Pattern

`frontend/src/api/client.ts` exports one shared `axios` instance:
- `baseURL: '/api'` (Vite proxy forwards to backend)
- A request interceptor reads `localStorage.getItem('token')` and adds `Authorization: Bearer ${token}` to every request.

API modules (`api/matches.ts`, `api/teams.ts`, etc.) export typed functions — never raw axios calls in components:
```ts
export const fetchLiveMatches = (): Promise<MatchDto[]> =>
  client.get('/matches/live').then((r) => r.data)
```

### Tailwind — Literal Class Strings Only

Tailwind classes are written as **literal string templates** — never built up via dynamic concatenation. The Tailwind JIT compiler must see the complete class name at build time.

**Acceptable** (conditional but each branch is a literal):
```tsx
className={`text-lg ${state === 'live'
  ? 'text-green-600 dark:text-green-400'
  : state === 'scheduled'
    ? 'text-stone-500 dark:text-zinc-400'
    : 'text-stone-900 dark:text-zinc-100'}`}
```

**Forbidden** (dynamic composition):
```tsx
className={`text-${color}-${shade}`}  // JIT cannot see "text-green-600" — Tailwind will purge it
```

**Theme convention:**
- Light mode default + `dark:` variant on every color class. No standalone `dark:` rules without a light counterpart.
- Colour palette: `stone` (light bg/text) + `zinc` (dark bg/text); `amber` is the brand accent.
- Status colours: `green` (live), `amber` (halftime / brand), `red` (errors).
- Use `tabular-nums` on all numeric scores so digits don't shift width when they change.

### Component Structure

Standard React component file layout (verified across `MatchCard.tsx`, `LivePage.tsx`, etc.):

```tsx
import { ... } from 'external-libs'
import { ... } from '../api/...'
import type { ... } from '../types'

// Module-level helpers (pure functions, constants)
function formatKickoff(...) { ... }

// Small sub-components co-located when only used here
function TeamCrest({ ... }) { ... }

// Props interface declared above the main component
interface Props {
  match: MatchDto
}

export default function MatchCard({ match }: Props) {
  // 1. Hooks (in order: state, context, custom hooks, useQuery, useEffect)
  // 2. Derived values (e.g. const state = getMatchState(match.status))
  // 3. Return JSX
}
```

**Other rules:**
- One default export per file (the main component); helpers/sub-components stay unexported.
- Functional components only — no class components anywhere.
- `'use client'` directives are NOT used (this is a Vite SPA, not Next.js).
- Props interfaces named `Props` for the local component; exported types live in `types/index.ts`.

### Navigation Patterns

- Routing via `react-router-dom` v6. `<Link to="..." state={data}>` is used to pass already-loaded data (e.g. a `MatchDto`) so the detail page can render without an extra fetch.
- Detail pages still call `useQuery` as a fallback for when the user navigates directly via URL (no router state available).
- Two parallel nav components: `Sidebar.tsx` (lg+ screens, vertical) and `BottomNav.tsx` (mobile, horizontal). Both consume the same `items` array structure: `{ to, icon, label }`. New routes must be added to both.

### Auth Context

`useAuth()` (from `frontend/src/context/AuthContext.tsx`) is the single source of truth for the JWT:
- Token lives in `localStorage` (`token` + `username` keys).
- The Axios interceptor reads from `localStorage` directly (not from React context) so it works during the initial request before any component renders.
- `useAuth()` throws if used outside `<AuthProvider>` — catches missing-provider bugs at runtime.

---

*Convention analysis: 2026-05-21*
