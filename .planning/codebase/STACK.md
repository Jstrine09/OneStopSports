# Technology Stack

**Analysis Date:** 2026-05-21

OneStopSports is a full-stack Fotmob-style sports app covering football (soccer), NBA, and NFL. Java/Spring Boot backend talking to PostgreSQL + Redis + multiple external sports APIs, with a React/Vite frontend that uses both REST polling and STOMP-over-WebSocket for live score push.

## Languages

**Primary:**
- Java 21 — backend (`pom.xml` `<java.version>21</java.version>`, `<source>21</source>`)
- TypeScript 5.5.3 — frontend (`frontend/tsconfig.json`, `frontend/package.json`)

**Secondary:**
- SQL (PostgreSQL dialect) — Flyway migrations in `src/main/resources/db/migration/V*.sql`
- YAML — Spring config (`src/main/resources/application*.yml`)
- HTML/CSS — minimal; styling driven by Tailwind utility classes

## Runtime

**Backend:**
- JVM: Eclipse Temurin 21 (JRE-only Alpine in prod — see `Dockerfile` stage 2: `eclipse-temurin:21-jre-alpine`)
- Embedded server: Tomcat (default from `spring-boot-starter-web`)
- HTTP port: `8081` (set in `src/main/resources/application.yml`, server.port)

**Frontend:**
- Node.js (version unpinned; no `.nvmrc`)
- Dev server: Vite on port 3000 (`frontend/vite.config.ts`), with `/api` and `/ws` proxied to `http://localhost:8081`
- Production bundle: static assets in `frontend/dist/` after `npm run build`

**Package Managers:**
- Maven 3.9 (builder image `maven:3.9-eclipse-temurin-21-alpine` in `Dockerfile`) — backend
- npm — frontend (lockfile present at `frontend/package-lock.json`)

## Frameworks

### Backend Core

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.4.4 | Parent BOM (`pom.xml` parent) — pins versions for all `spring-boot-starter-*` deps |
| spring-boot-starter-web | (via BOM) | REST controllers, embedded Tomcat, Jackson |
| spring-boot-starter-data-jpa | (via BOM) | Hibernate ORM + Spring Data repositories |
| spring-boot-starter-security | (via BOM) | Spring Security 6 — JWT auth filter chain |
| spring-boot-starter-data-redis | (via BOM) | Lettuce client + `RedisCacheManager` for `@Cacheable` |
| spring-boot-starter-websocket | (via BOM) | STOMP-over-WebSocket for live score push |
| spring-boot-starter-validation | (via BOM) | Jakarta Bean Validation on request DTOs |
| spring-boot-starter-webflux | (via BOM) | Included for `RestClient` (Spring 6 synchronous client) — note: actual usage is synchronous `RestClient`, not reactive `WebClient` |

### Backend Persistence

| Dependency | Version | Purpose |
|------------|---------|---------|
| PostgreSQL JDBC | (BOM-managed) | Driver — runtime scope |
| Flyway Core | (BOM-managed) | DB migrations — six `V*.sql` files in `src/main/resources/db/migration/` |
| flyway-database-postgresql | (BOM-managed) | Flyway 10+ requires a separate Postgres dialect module |
| Hibernate | (BOM-managed, via Spring Data JPA) | ORM; `spring.jpa.hibernate.ddl-auto: validate` — Flyway owns schema, Hibernate only validates |
| H2 | (BOM-managed, `test` scope) | In-memory DB for `mvn test` |

### Backend Auth / Security

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Security | 6.x (Spring Boot 3.4.x BOM) | `SecurityFilterChain` bean in `SecurityConfig.java` |
| jjwt-api | 0.12.6 | JWT API surface (compile scope) |
| jjwt-impl | 0.12.6 | JWT implementation (runtime scope) |
| jjwt-jackson | 0.12.6 | Jackson serialiser for JWT claims (runtime scope) |

**Note:** jjwt 0.12.x has a different parser API than 0.11.x — `Jwts.parser()` + `.verifyWith(key)` + `.parseSignedClaims(token)`.

### Backend Utilities

| Dependency | Version | Purpose |
|------------|---------|---------|
| Lombok | 1.18.32 | `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` on entities; NOT `@Data` (recursion risk on bidirectional relationships) |
| MapStruct | 1.5.5.Final | Compile-time DTO ↔ entity mapping |
| lombok-mapstruct-binding | 0.2.0 | Annotation-processor bridge — Lombok runs FIRST so MapStruct sees generated getters (ordering in `pom.xml` is load-bearing) |
| springdoc-openapi-starter-webmvc-ui | 2.8.5 | Auto-generates OpenAPI 3 spec + Swagger UI from `@RestController` classes |

### Backend Testing

| Dependency | Version | Purpose |
|------------|---------|---------|
| spring-boot-starter-test | (BOM) | JUnit 5, Mockito, AssertJ, MockMvc |
| spring-security-test | (BOM) | `csrf()`, `@WithMockUser`, `SecurityMockMvcRequestPostProcessors` |
| H2 | (BOM) | In-memory DB (no Postgres needed for tests) |

Tests run under `src/test/resources/application-test.yml` which disables Redis (`spring.cache.type: none`) and excludes `RedisAutoConfiguration` / `RedisRepositoriesAutoConfiguration`. `OneStopSportsApplicationTests` additionally `@MockBean`s `RedisConnectionFactory` because `RedisConfig` is a user `@Configuration` (not auto-config) and still wires `RedisCacheManager`.

Current count: **48 tests passing** across `AuthServiceTest` (6), `AuthControllerTest` (7), `MatchServiceTest` (13), `NbaApiServiceTest` (12), `LeagueServiceTest` (9), `OneStopSportsApplicationTests` (1).

### Frontend Runtime

| Dependency | Version | Purpose |
|------------|---------|---------|
| react | ^18.3.1 | UI framework |
| react-dom | ^18.3.1 | DOM renderer |
| react-router-dom | ^6.26.2 | Client-side routing |
| @tanstack/react-query | ^5.56.2 | Server-state cache + request dedup; `queryClient.setQueryData(["matches","live"], ...)` used from WebSocket hook to push live updates |
| @stomp/stompjs | ^7.3.0 | STOMP-over-WebSocket client (`useLiveScores` hook subscribes `/topic/matches/live`) |
| axios | ^1.7.7 | REST client; JWT interceptor configured in `frontend/src/api/` |
| lucide-react | ^0.447.0 | Icon set |
| clsx | ^2.1.1 | Conditional className helper |

### Frontend Dev / Build

| Dependency | Version | Purpose |
|------------|---------|---------|
| vite | ^5.4.8 | Dev server (port 3000) + production bundler |
| @vitejs/plugin-react | ^4.3.1 | React Fast Refresh + JSX transform |
| typescript | ~5.5.3 | Type checker (`tsc && vite build` in npm `build` script) |
| tailwindcss | ^3.4.13 | Utility-first CSS — see `frontend/tailwind.config.js` for custom `surface.*` zinc palette |
| postcss | ^8.4.47 | Tailwind processor pipeline |
| autoprefixer | ^10.4.20 | Vendor-prefix transform |
| @types/react, @types/react-dom | ^18.3.x | Type declarations |

TypeScript config (`frontend/tsconfig.json`): `strict: true`, `noUnusedLocals: true`, `noUnusedParameters: true`, `moduleResolution: bundler`, JSX runtime `react-jsx`.

## Infrastructure

### Containers

**`Dockerfile` (multi-stage):**
- Stage 1: `maven:3.9-eclipse-temurin-21-alpine` — `mvn dependency:go-offline` (cached layer) then `mvn package -DskipTests`
- Stage 2: `eclipse-temurin:21-jre-alpine` — copies only `target/*.jar`, runs as non-root `appuser`
- `EXPOSE 8080` (note: app actually listens on 8081 via `server.port`; docker-compose maps `8081:8081`)
- Entrypoint: `java -jar app.jar`

**`docker-compose.yml`:**
- `postgres` — `postgres:16-alpine`; DB name `onestopsports`; user `postgres`; volume `onestopsports_postgres_data`; healthcheck `pg_isready`
- `redis` — `redis:7-alpine`; healthcheck `redis-cli ping`
- `app` — built from `Dockerfile`; `SPRING_PROFILES_ACTIVE=docker`; `depends_on` both services with `condition: service_healthy`; `restart: on-failure`
- Secrets passed via `.env` at project root (gitignored): `DB_PASSWORD`, `FOOTBALL_DATA_API_KEY`, `JWT_SECRET`

### Database

- **Engine:** PostgreSQL 16 (Alpine in compose; user must provide any 16.x locally)
- **Database name:** `onestopsports`
- **Migration tool:** Flyway 10+ (requires explicit `flyway-database-postgresql` dialect module)
- **Migration files:** `src/main/resources/db/migration/V1`–`V6__*.sql`
  - V1: `sport`, `league`
  - V2: `team`, `player`
  - V3: `user_account`, `favorite_team`, `favorite_player` (note: `user_account` not `user` — reserved word in Postgres)
  - V4: `league.external_id INTEGER` (bridges DB IDs → football-data.org competition IDs)
  - V5: Renames sport name "Football" → "Futbol" (slug unchanged)
  - V6: `player.external_id` (caches API-SPORTS player ID for football career-stats lookups)
- **Hibernate ddl-auto:** `validate` — schema is Flyway-owned

### Cache

- **Engine:** Redis 7 (Alpine in compose)
- **Client:** Lettuce (default from `spring-boot-starter-data-redis`)
- **Config:** `config/RedisConfig.java` — custom `ObjectMapper` with `JavaTimeModule` + `DefaultTyping.EVERYTHING` (the no-arg `GenericJackson2JsonRedisSerializer` cannot handle `LocalDateTime` and crashes on any cached match with a `startTime`)
- **TTL:** 30s default on `matches` cache (set programmatically in `RedisConfig` to override the YAML default when a custom `RedisCacheManager` bean is in play)
- **Manual cache mutation:** `MatchService.refreshLiveMatchCache()` writes via `cacheManager.getCache("matches").put(SimpleKey.EMPTY, current)` — key for no-arg `@Cacheable` method is `SimpleKey.EMPTY`

### WebSocket

- **Endpoint:** `/ws` (SockJS-compatible STOMP); Vite proxy forwards with `ws: true`
- **Topic:** `/topic/matches/live` — full live-match list pushed on any score/status change
- **Config:** `config/WebSocketConfig.java` — overrides `configureMessageConverters` to inject Spring Boot's auto-configured `ObjectMapper` (default STOMP converter uses a bare ObjectMapper that cannot serialise `LocalDateTime`)
- **Scheduler:** `MatchService.refreshLiveMatchCache()` @Scheduled `fixedDelay = 30_000` — diffs against `previousSnapshot` map and only pushes on change

## Configuration

**Spring config files (`src/main/resources/`):**
- `application.yml` — base config; placeholders for secrets (`YOUR_API_KEY_HERE`, default Base64 JWT secret) so the app boots without `application-local.yml`
- `application-local.yml` — **gitignored**; real API keys and JWT secret for `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- `application-docker.yml` — committed; uses Docker Compose service names (`postgres`, `redis`) and `${ENV_VAR}` placeholders fed from `.env`
- `application-test.yml` (in `src/test/resources/`) — disables Redis

**Documented config keys (`META-INF/additional-spring-configuration-metadata.json`):**
- `jwt.secret` (Base64 HMAC key), `jwt.expiration-ms` (default 86400000 = 24h)
- `external-api.football-data.{base-url, api-key}`
- `external-api.nba.{base-url, standings-url, stats-url}`
- `external-api.nfl.{base-url, standings-url, stats-url}`
- `external-api.balldontlie.{base-url, api-key}`
- `external-api.api-football.{base-url, api-key}`

**`.env` keys** (root, gitignored): `DB_PASSWORD`, `FOOTBALL_DATA_API_KEY`, `JWT_SECRET`. The `.env.example` is committed.

## Platform Requirements

**Development (Option A — Maven host + Dockerised infra):**
- Java 21 JDK on host
- Maven 3.9+ on host (or use the bundled `./mvnw` wrapper if added; currently the repo invokes `mvn` directly)
- Docker / Docker Compose for `postgres` + `redis` services
- Node 18+ for the frontend dev server (no `.nvmrc` — version inferred from Vite 5 + React 18 compatibility)

**Development (Option B — full Docker Compose):**
- Docker / Docker Compose only; no local Java or Node needed
- First boot seeds DB via `DataLoader`, `NbaDataLoader`, `NflDataLoader` (~2 min)

**Production target:** Container — the multi-stage `Dockerfile` produces a single self-contained image. No deployment platform is wired in (no CI/CD config in the repo).

## Build Tooling

**Maven plugins (`pom.xml`):**
- `spring-boot-maven-plugin` — produces fat JAR; excludes Lombok from the runtime classpath
- `maven-compiler-plugin` — Java 21 source/target; `annotationProcessorPaths` order is **load-bearing**: Lombok → lombok-mapstruct-binding → MapStruct processor. Reversing this means MapStruct cannot see Lombok-generated getters.

**Frontend npm scripts (`frontend/package.json`):**
- `dev` → `vite` (port 3000, hot reload, proxies `/api` and `/ws`)
- `build` → `tsc && vite build` (type check then bundle to `dist/`)
- `preview` → `vite preview` (serve built bundle)

## Observability & Docs

- **API docs:** Swagger UI at `http://localhost:8081/swagger-ui/index.html`; raw spec at `/v3/api-docs`; configured in `config/OpenApiConfig.java` with JWT Bearer scheme so locked endpoints are testable from the UI
- **Logging:** SLF4J via `spring-boot-starter-*` (Logback under the hood); per-class loggers (`LoggerFactory.getLogger(...)`)
- **Metrics / tracing:** None wired in
- **Error responses:** `GlobalExceptionHandler` (`@RestControllerAdvice`) returns consistent `ErrorResponseDto(status, error, message, timestamp)` for `MethodArgumentNotValidException` (400), `HttpMessageNotReadableException` (400), `ResponseStatusException` (passthrough), `BadCredentialsException` (401), `AccessDeniedException` (403), `DataIntegrityViolationException` (409), and a catch-all `Exception` (500). `ResponseStatusException` must be declared BEFORE the catch-all or it gets intercepted.

---

*Stack analysis: 2026-05-21*
