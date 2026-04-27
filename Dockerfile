# ─────────────────────────────────────────────────────────────────────────────
# OneStopSports — Multi-stage Dockerfile
#
# Stage 1 (builder): Compiles the Spring Boot app into a fat JAR using Maven.
# Stage 2 (runtime): Copies only the JAR into a minimal JRE image.
#
# Multi-stage keeps the final image small — no Maven, no source code, no JDK.
# Dependency layer is cached separately from source so rebuilds are fast when
# only application code changes (the mvn dependency:go-offline step is skipped).
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy the POM first and download all dependencies into the local Maven cache.
# Docker caches this layer separately — if pom.xml hasn't changed, the next
# build skips the download entirely, making iterative builds much faster.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy the full source tree and build the fat JAR.
# -DskipTests: tests are run in CI, not during the Docker image build.
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
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

# Document the port the app listens on (doesn't actually publish it — that's
# handled in docker-compose.yml with the ports: mapping).
EXPOSE 8080

# Start the Spring Boot app.
# The active profile is set via SPRING_PROFILES_ACTIVE in docker-compose.yml.
ENTRYPOINT ["java", "-jar", "app.jar"]
