# OneStopSports — Conventions

> **What this doc is:** The conventions actually followed across this codebase — backend (Java 21 / Spring Boot) and frontend (React 18 / TypeScript / Vite). New code must match these patterns. When you spot a deviation in old code, that's an opportunity, not a precedent.

---

## Backend (Java)

### Package structure

Single root package `com.onestopsports`, one sub-package per layer, no further nesting.

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

No `com.onestopsports.service.football` style sub-nesting — multi-sport logic lives in routing methods inside the existing services, not in package boundaries.

### Comment style — MANDATORY

**Every new Java file must contain plain-English inline comments aimed at a junior developer.** This is a project-level rule from user memory; all ~50 existing files follow it.

Required:
1. **Class-level comment** — what the class does + any non-obvious why
2. **Field-level comments** — when the purpose isn't obvious from the name
3. **Method-level header** — Javadoc for public service methods; plain `//` blocks for private helpers
4. **Inline rationale** for non-obvious decisions — `@Lazy` annotations, sport-routing switches, rate-limit sleeps, parsing fallbacks
5. **Section dividers** in larger files using `// ── Section Name ─────`

Tone: explanatory, not academic. Aim for "explain to a CS student in their first internship". Mention the *why* (cycle prevention, free-tier limits, ESPN's empty-string score format) — not just the *what*.

Example (from `AuthService.java`):
```java
// @Lazy on AuthenticationManager breaks a circular dependency:
// SecurityConfig needs AuthService (to set it as UserDetailsService),
// but AuthService would need AuthenticationManager which lives in SecurityConfig.
// @Lazy tells Spring to create the AuthenticationManager only when it's
// first actually used (i.e. on the first login request), breaking the cycle.
```

### DTOs — Java records, never Lombok

Every DTO in `src/main/java/com/onestopsports/dto/` is a Java 21 record. No Lombok DTO classes anywhere.

```java
public record PlayerDto(
        Long id,
        String name,
        String position,
        String nationality,
        LocalDate dateOfBirth,
        Integer jerseyNumber,
        String photoUrl,
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

**Naming:**
- Response DTOs: `<Resource>Dto` suffix (`PlayerDto`, `MatchDto`, `StandingsEntryDto`, `ErrorResponseDto`)
- Request DTOs: `<Resource>Request` suffix (`AuthRequest`, `RegisterRequest`, `FavoriteTeamRequest`)
- The few legacy exceptions are `AuthResponse`, `AuthRequest`, etc. — kept for compatibility. New DTOs must use the `Dto` suffix.

### Entities — Lombok quartet, never `@Data`

All entities in `model/` use the exact four-annotation Lombok stack:

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

**Never `@Data` on entities** — it generates `toString` / `hashCode` / `equals` that recurse through bidirectional `@ManyToOne` / `@OneToMany` relationships and cause `StackOverflowError`.

Other entity rules:
- `@ManyToOne` always `fetch = FetchType.LAZY`
- `@CreationTimestamp @Column(updatable = false) private LocalDateTime createdAt;` on entities that need audit
- One reserved-word workaround: `UserAccount` (class) maps to `user_account` (table). Never name an entity `User` — `user` is reserved in PostgreSQL
- `@Column(unique = true, nullable = false, length = N)` for required unique fields

### Naming conventions

| Item | Pattern | Example |
|---|---|---|
| Entity | `PascalCase`, singular | `UserAccount`, `League`, `Sport` |
| Table | `snake_case`, singular | `user_account`, `favorite_team` |
| DTO | `PascalCase` + `Dto` suffix | `PlayerDto`, `MatchDto` |
| Request DTO | `PascalCase` + `Request` suffix | `RegisterRequest`, `AuthRequest` |
| Service | `PascalCase` + `Service` | `AuthService`, `MatchService` |
| External-API service | Provider in name + `Service` | `NbaApiService`, `ApiFootballService` |
| Controller | `PascalCase` + `Controller` | `MatchController`, `AuthController` |
| Repository | `PascalCase` + `Repository` | `LeagueRepository` |
| Repository derived method | `findBy*` / `existsBy*` / `countBy*` | `findByLeagueId`, `existsByTeamIdAndExternalIdIsNull` |
| Repository relationship traversal | Underscore-separated | `findBySport_Slug(String)` joins through the `sport` relationship |
| Constants | `UPPER_SNAKE_CASE` | `TEST_DATE`, `COMPETITION_IDS`, `FREE_TIER_MAX_SEASON` |
| Inner records (external APIs) | Provider prefix | `EspnTeam`, `EspnEvent`, `ApiMatch`, `BdlPlayer` |
| Migrations | `V<n>__snake_case_description.sql` | `V6__add_player_external_id.sql` |

### Repository pattern — derived queries only

Every method in every repository is a Spring Data derived query parsed from the method name. **No `@Query` annotations anywhere in the codebase.** Keep it that way unless absolutely necessary.

```java
List<League> findBySportId(Long sportId);
Optional<League> findByExternalId(Integer externalId);
List<League> findBySport_Slug(String sportSlug);  // Underscore = traverse relationship
boolean existsByTeamIdAndExternalIdIsNull(Long teamId);
```

### Service layer

- One `*Service` class per concern. `@Service` annotation on every service.
- Constructors use **manual injection** — not `@RequiredArgsConstructor` from Lombok. Kept explicit for clarity, and required where `@Lazy` is needed on a parameter (e.g. `AuthService`'s `AuthenticationManager`).
- External-API services use Spring 6 **`RestClient`** (synchronous) — never `WebClient`.
- URLs injected via `@Value("${external-api.<provider>.<key>}")`.
- Logging via SLF4J: `private static final Logger log = LoggerFactory.getLogger(<Class>.class);` — never `System.out.println`, never Lombok's `@Slf4j`.
- Bracketed prefix on log messages: `log.warn("[NbaApiService] fetchStandings failed for season={}: {}", season, e.getMessage());`

### Multi-sport routing

Routing lives in the service layer and switches on `sport.getSlug()` strings — never on enums, never on instance-of checks.

**Canonical slugs:**

| Slug | Sport | Adapter service |
|---|---|---|
| `"football"` | Football / soccer | `ExternalApiService` (football-data.org) |
| `"basketball"` | NBA | `NbaApiService` (ESPN) |
| `"american-football"` | NFL | `NflApiService` (ESPN) |

The pattern (from `MatchService.getMatchesByLeagueAndDate`):

```java
@Transactional(readOnly = true)
public List<MatchDto> getMatchesByLeagueAndDate(Long leagueId, LocalDate date) {
    if (leagueId == null || date == null) return Collections.emptyList();

    return leagueRepository.findById(leagueId).map(league -> {
        String sportSlug = league.getSport().getSlug();
        return switch (sportSlug) {
            case "basketball"        -> nbaApiService.fetchGameDtosByDate(date, league.getId());
            case "american-football" -> nflApiService.fetchGameDtosByDate(date, league.getId());
            default                  -> league.getExternalId() != null
                ? externalApiService.fetchMatchDtosByCompetition(league.getExternalId(), date)
                : Collections.<MatchDto>emptyList();
        };
    }).orElse(Collections.emptyList());
}
```

Same shape in `LeagueService.getStandings` and `PlayerService.getPlayerCareerStats`.

### `@Transactional` pattern

- Service methods that walk a lazy `@ManyToOne` chain (e.g. `league.getSport()`, `player.getTeam().getLeague()`) need `@Transactional(readOnly = true)` so the Hibernate session stays open.
- Always use `readOnly = true` on read paths — Hibernate skips dirty-checking.
- Methods that may **write** during the request (e.g. `PlayerService.getPlayerCareerStats` lazily backfilling `Player.external_id` for football) use plain `@Transactional` (no `readOnly`).

### Null handling

- Guard clauses at the top of public service methods: `if (leagueId == null) return Collections.emptyList();`
- Repositories return `Optional<T>` from `findById` etc. — never `null`. Callers use `.map(...)` / `.orElseThrow(...)` / `.orElse(...)`.
- External API responses are null-checked before walking nested fields — `RestClient.body(...)` can return null on a 204 or empty response.
- DTOs prefer `null` over default values when data is missing (e.g. `MatchDto.homeScore` is `null` for scheduled games — never `0`). Frontend renders "—" on null.

### Exception handling

- Service-layer errors that should map to HTTP statuses throw `new ResponseStatusException(HttpStatus.X, "message")`. Example: `AuthService.register` throws `HttpStatus.CONFLICT` for duplicate usernames.
- All exceptions are centralised in `GlobalExceptionHandler` (`@RestControllerAdvice`).
- **CRITICAL ORDERING:** `@ExceptionHandler(ResponseStatusException.class)` must appear BEFORE `@ExceptionHandler(Exception.class)`. The catch-all intercepts otherwise and returns 500 instead of the carried status.
- Never expose raw stack traces, SQL errors, or internal messages to the client. The `Exception.class` handler logs the full `ex` server-side but returns a generic message to the client.
- External-API services swallow `RestClientException` (timeouts, off-season endpoints, structure changes) and return empty lists / null. Users see "No data available" instead of a 500.

### Config injection

- Use `@Value("${path.to.key}")` constructor injection — **never field injection**.
- All external-API config lives under a single `external-api.*` namespace in `application.yml`.
- **Documented for IDE auto-complete:** every key in `application.yml` has a matching entry in `src/main/resources/META-INF/additional-spring-configuration-metadata.json` with a description. New config keys must add an entry there too.
- Secrets (API keys, JWT secret) NEVER live in `application.yml` — overridden in `application-local.yml` (gitignored, `local` profile) or via env vars in `application-docker.yml`.
- JWT secret is **Base64-encoded** in config (jjwt 0.12.x requirement).

### External-API response records

Every external API service has a `// ── API Response Records ─────` section containing nested public records that mirror the upstream JSON shape:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record EspnTeamsResponse(List<EspnSport> sports) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record EspnSport(List<EspnLeague> leagues) {}
```

**Rules:**
- Every record annotated `@JsonIgnoreProperties(ignoreUnknown = true)` so added upstream fields don't crash deserialisation
- Records are `public` (not `private`) so test classes in the same package can construct them as fixtures
- Name prefix matches the provider: `Espn*` (NBA/NFL), `Api*` (football-data.org), `Bdl*` (balldontlie). API-Football uses `Api*` inside `ApiFootballService`.

### Controllers

- `@RestController` + `@RequestMapping("/api/<resource>")` — never `@Controller` returning views.
- Constructor injection, manual constructor (no `@RequiredArgsConstructor`).
- Method per endpoint. Return `ResponseEntity<T>` even on 200 — `return ResponseEntity.ok(service.getX(id));`
- Path variables: `@PathVariable`. Query params: `@RequestParam(required = false)` when optional.
- Dates parsed via `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date` — never plain `String` + manual parsing.
- Request bodies: `@Valid @RequestBody RegisterRequest req` so Jakarta validation fires automatically.

### `pom.xml` annotation-processor order — LOAD-BEARING

In `maven-compiler-plugin`'s `annotationProcessorPaths`:

```
1. Lombok
2. lombok-mapstruct-binding (the bridge)
3. MapStruct
```

Reverse this and MapStruct cannot see Lombok-generated getters. The order is documented in the pom but easy to miss.

---

## Frontend (TypeScript / React)

### File naming

| Item | Location | Pattern |
|---|---|---|
| Page component | `frontend/src/pages/` | `PascalCase` + `Page.tsx` (`LivePage.tsx`, `TeamDetailPage.tsx`) |
| Reusable component | `frontend/src/components/` | `PascalCase.tsx` (`MatchCard.tsx`, `StandingsTable.tsx`) |
| Layout component | `frontend/src/layout/` | `PascalCase.tsx` (`Sidebar.tsx`, `BottomNav.tsx`) |
| Hook | `frontend/src/hooks/` | `useCamelCase.ts` (`useLiveScores.ts`) |
| API client | `frontend/src/api/` | `lowercase.ts` (`matches.ts`, `client.ts`) |
| Context | `frontend/src/context/` | `PascalCase` + `Context.tsx` (`AuthContext.tsx`) |
| Types | `frontend/src/types/index.ts` | single barrel file |

### Types mirror backend records

Every backend Java record has a matching TypeScript `interface` with the **same name** in `frontend/src/types/index.ts`. The frontend's contract IS the backend's contract.

```ts
export interface PlayerDto {
  id: number
  name: string
  position: string | null
  // ...
  teamId: number
}
```

Rules:
- Preserve the `Dto` suffix (`PlayerDto`, not `Player`)
- Optional / null backend fields type as `string | null`, not `string | undefined`. Backend sends literal `null` in JSON
- A new backend record requires a matching frontend interface in the same PR

### React Query — standardised `staleTime`

`staleTime` matches how often the data actually changes upstream:

| Data type | `staleTime` | Example queryKey |
|---|---|---|
| Live scores | `30_000` (30s) | `['matches', 'live']` |
| Match list for a date | `30_000` | `['matches', leagueId, date]` |
| Standings | `5 * 60_000` (5m) | `['standings', leagueId]` |
| Team / league list | `5 * 60_000` | `['leagues', sportSlug]` |
| User favourites | `2 * 60_000` | `['favorites', 'teams']` |
| Sports (rarely change) | `10 * 60_000` (10m) | `['sports']` |
| Search results | `30_000` | `['search', trimmed]` |
| Player bio / career stats | `24 * 60 * 60_000` (24h) | `['player-bio', id]`, `['player-stats', id]` |

**Query key convention:** array of `[resource, ...identifiers]`. First element is the noun (`'matches'`, `'standings'`, `'favorites'`); subsequent elements narrow it down.

**Conditional queries** use `enabled` for queries that depend on user input:
```ts
useQuery({
  queryKey: ['search', trimmed],
  queryFn: () => searchAll(trimmed),
  enabled: trimmed.length >= 2,
  staleTime: 30_000,
})
```

**Cache invalidation:** mutations call `queryClient.invalidateQueries({ queryKey: ['favorites', 'teams'] })`. WebSocket pushes use `queryClient.setQueryData(['matches', 'live'], updatedMatches)` to re-render without a refetch.

### API client pattern

`frontend/src/api/client.ts` exports one shared `axios` instance:
- `baseURL: '/api'` (Vite proxy forwards to backend)
- Request interceptor reads `localStorage.getItem('token')` and adds `Authorization: Bearer ${token}` to every request

API modules (`api/matches.ts`, `api/teams.ts`, etc.) export typed functions — **never raw axios calls in components**:

```ts
export const fetchLiveMatches = (): Promise<MatchDto[]> =>
  client.get('/matches/live').then((r) => r.data)
```

For endpoints that can return 204 (e.g. `/players/{id}/bio`, `/players/{id}/career-stats`), use `validateStatus`:
```ts
const response = await client.get(`/players/${id}/bio`, {
  validateStatus: (s) => s === 200 || s === 204,
})
return response.status === 204 ? null : (response.data as PlayerBioDto)
```

### Tailwind — literal class strings only

Tailwind classes are written as **literal strings** — never dynamically composed. The Tailwind JIT compiler must see the complete class name at build time.

**Acceptable** (conditional, each branch is a literal):
```tsx
className={`text-lg ${state === 'live'
  ? 'text-green-600 dark:text-green-400'
  : state === 'scheduled'
    ? 'text-stone-500 dark:text-zinc-400'
    : 'text-stone-900 dark:text-zinc-100'}`}
```

**Forbidden** (dynamic composition):
```tsx
className={`text-${color}-${shade}`}  // JIT cannot see "text-green-600" — purged
```

**Theme conventions:**
- Light mode default + `dark:` variant on every color class. No standalone `dark:` rules without a light counterpart.
- Palette: `stone` (light bg/text) + `zinc` (dark bg/text); `amber` is the brand accent.
- Status colours: `green` (live), `amber` (halftime / brand), `red` (errors).
- Use `tabular-nums` on all numeric scores so digits don't shift width when they change.

### Component structure

Standard React component file layout:

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
  // 2. Derived values
  // 3. Return JSX
}
```

Rules:
- One default export per file (the main component); helpers/sub-components stay unexported
- Functional components only — no class components anywhere
- No `'use client'` directives (this is a Vite SPA, not Next.js)
- Props interface named `Props` for the local component; exported types live in `types/index.ts`

### Navigation patterns

- Routing via `react-router-dom` v6. `<Link to="..." state={data}>` passes already-loaded data (e.g. a `MatchDto`) so the detail page renders without an extra fetch.
- Detail pages still call `useQuery` as a fallback for when the user navigates directly via URL (no router state available).
- Two parallel nav components: `Sidebar.tsx` (lg+ screens, vertical) and `BottomNav.tsx` (mobile, horizontal). Both consume the same `items` array structure: `{ to, icon, label }`. **New routes must be added to both.**

### Auth context

`useAuth()` (from `frontend/src/context/AuthContext.tsx`) is the single source of truth for the JWT:
- Token in `localStorage` (`token` + `username` keys)
- The axios interceptor reads from `localStorage` directly (not from React context) so it works during the initial request before any component renders
- `useAuth()` throws if used outside `<AuthProvider>` — catches missing-provider bugs at runtime

---

## Tooling

| Tool | How |
|---|---|
| Backend run (local) | `mvn spring-boot:run -Dspring-boot.run.profiles=local` (port 8081) |
| Frontend run (dev) | `cd frontend && npm run dev` (port 3000, proxies `/api` + `/ws` to 8081) |
| Tests | `mvn test` (H2 in-memory; Redis disabled via `application-test.yml`) |
| Full stack via Docker | `docker-compose up --build` |
| Swagger UI | `http://localhost:8081/swagger-ui/index.html` |
| Production build (frontend) | `cd frontend && npm run build` (output to `frontend/dist/`) |

---

## Design system primitives (the "sport field" redesign)

Added in the Claude Design handoff redesign. Reuse these on any new screen rather than re-styling inline.

- **`SectionLabel`** (`components/SectionLabel.tsx`) — the uppercase tracked section heading (`text-[11px] font-bold uppercase tracking-[0.13em]`). Accepts a leading icon/bullet child and an optional `className` to recolor (e.g. a league accent).
- **`RowCard`** (`components/RowCard.tsx`, exports `ROW_DIVIDER`) — the rounded bordered list surface. Rows inside use the `ROW_DIVIDER` class for consistent separators.
- **`SportFieldBackdrop`** (`components/SportFieldBackdrop.tsx`, exports `fieldVariantForSport`) — animated portrait field; variant `bowl`/`court`/`gridiron`; themed via a Tailwind text-color class passed as `colorClass`; add `className="hidden md:block"` to hide on phones.
- **`.glass-card`** (index.css) — translucent + blur surface to layer over a field so it reads through. **Glassmorphism is the intended house style for field-backed surfaces** — any older "no glassmorphism" rule is superseded.

**Motion + a11y conventions:**
- New looping animations must be gated: run custom field animation only under `prefers-reduced-motion: no-preference`; ensure `prefers-reduced-motion: reduce` neutralises it (Tailwind's built-in `animate-*` are handled globally in index.css).
- Tailwind classes stay **literal strings** (JIT can't see `text-${x}`); decorative SVG is `aria-hidden`; keep the global `:focus-visible` ring intact.
