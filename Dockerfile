# ─────────────────────────────────────────────────────────────────────────────
# OneStopSports — Multi-stage Dockerfile
#
# Stage 1 (frontend): Builds the React/Vite app into static files.
# Stage 2 (builder):  Embeds those static files into the backend, then compiles
#                     the Spring Boot app into a fat JAR using Maven.
# Stage 3 (runtime):  Copies only the JAR into a minimal JRE image.
#
# Embedding the built frontend into Spring Boot's static resources means the
# single JAR serves BOTH the API and the React app from one origin — so the
# frontend's relative /api and /ws paths "just work" with no CORS in production.
#
# Multi-stage keeps the final image small — no Maven, no Node, no source code.
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build the frontend ───────────────────────────────────────────────
FROM node:20-alpine AS frontend

WORKDIR /frontend

# Install dependencies first (cached separately from source). npm ci needs the
# lockfile and installs the exact pinned versions.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

# Copy the rest of the frontend and produce the production bundle in /frontend/dist.
COPY frontend/ ./
RUN npm run build

# ── Stage 2: Build the backend (with the frontend embedded) ───────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy the POM first and download all dependencies into the local Maven cache.
# Docker caches this layer separately — if pom.xml hasn't changed, the next
# build skips the download entirely, making iterative builds much faster.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy the full source tree.
COPY src ./src

# Embed the built SPA into Spring Boot's static resources. Spring Boot
# automatically serves anything under classpath:/static/ — so index.html is
# served at "/" and the hashed asset bundles at "/assets/*".
COPY --from=frontend /frontend/dist/ ./src/main/resources/static/

# Build the fat JAR (the embedded static files get packaged inside it).
# -DskipTests: tests are run in CI, not during the Docker image build.
RUN mvn package -DskipTests -q

# ── Stage 3: Runtime ──────────────────────────────────────────────────────────
# eclipse-temurin:21-jre-alpine is a minimal JRE — no JDK tools, ~80MB smaller
# than the full JDK image. Alpine base keeps the overall image lean.
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Copy only the compiled JAR from the builder stage.
# The wildcard handles the version suffix (e.g. onestopsports-0.0.1-SNAPSHOT.jar).
COPY --from=builder /build/target/*.jar app.jar

# Run as a non-root user — best practice for container security.
# The app doesn't need write access to the filesystem at runtime.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Cap the JVM heap relative to the container memory limit. On small hosts (e.g.
# Render's free 512MB instance) the JVM otherwise assumes it can use the whole
# host and gets OOM-killed. 75% leaves headroom for non-heap (metaspace, threads).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# Document the port. The app binds to $PORT at runtime (set by the host, e.g.
# Render) and falls back to 8081 locally — see application-prod.yml server.port.
EXPOSE 8081

# Start the Spring Boot app. The active profile is set via SPRING_PROFILES_ACTIVE.
ENTRYPOINT ["java", "-jar", "app.jar"]
