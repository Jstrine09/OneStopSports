---
phase: 2
slug: postgres-migration-integration-tests
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-13
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + **Testcontainers** (`postgres:16-alpine`) + Flyway 10.20.1 + `JdbcTemplate` — plain JUnit, **no `@SpringBootTest`** (avoids booting `DataLoader`/`NbaDataLoader`/`NflDataLoader` `ApplicationRunner`s that make live API calls) |
| **Config file** | none — a new Maven `integration` profile declares the (already plugin-managed) `maven-failsafe-plugin`; Testcontainers supplies the JDBC URL, so no `application-it.yml` is required |
| **Quick run command** | `mvn -q verify -Pintegration -Dit.test=PostgresMigrationIT` (single IT class via Failsafe) |
| **Full suite command (this phase's IT)** | `mvn verify -Pintegration` |
| **Default suite (must stay green + Docker-free)** | `mvn test` — the existing 120-test H2 suite; Failsafe's `**/*IT.java` names are never matched by Surefire's default includes, so the IT does NOT run here |
| **Estimated runtime** | container start ~measure on first run (image pull + Flyway V1→V9); assertions are sub-second |

---

## Sampling Rate

- **After every task commit:** Run `mvn -q verify -Pintegration -Dit.test=PostgresMigrationIT` (the IT class touched) — requires Docker.
- **Docker-free guard:** every `mvn test` (default profile) must remain green and NOT require Docker throughout the phase (baseline 120 tests not regressed).
- **After every plan wave:** Run `mvn verify -Pintegration` (IT) **and** `mvn test` (H2 baseline).
- **Before `/gsd-verify-work`:** Both `mvn test` (H2, green, Docker-free) and `mvn verify -Pintegration` (IT, green) pass.
- **Max feedback latency:** dominated by container startup; assertions themselves are sub-second.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| {N}-01-01 | 01 | 1 | HARD-02 | — | N/A (test-only; real DB is intentional) | integration | `mvn -q verify -Pintegration -Dit.test=<ClassName>` | ❌ W0 | ⬜ pending |

*Planner fills concrete rows per task. Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Build prerequisite (RESEARCH.md): add two `<dependency>` blocks to `pom.xml` — `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter` (versions managed transitively by `spring-boot-starter-parent:3.4.4` → testcontainers 1.20.6; no pins), plus a Maven `integration` profile declaring the already-plugin-managed `maven-failsafe-plugin` (3.5.2, `integration-test`+`verify` goals).
- [ ] No new test framework install — JUnit 5 + Failsafe already available via the parent POM.

*The Testcontainers deps + `integration` Maven profile are the Wave-0 build prereq (analogous to Phase 1's test-constructor prereq): the IT class cannot compile/run until they exist.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| — | — | — | — |

*All phase behaviors have automated verification (`mvn verify -Pintegration`). Note: the IT requires a running Docker daemon; in a Docker-less environment it is skipped, not failed.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (Testcontainers deps + Failsafe `integration` profile)
- [ ] No watch-mode flags
- [ ] Feedback latency acceptable (container startup only)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending (plan-checker verification)
