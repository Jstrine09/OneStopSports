---
phase: 1
slug: backend-service-test-coverage
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-08
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito + spring-boot-starter-test |
| **Config file** | `src/test/resources/application-test.yml` (H2 in-memory, Flyway off, Redis disabled) |
| **Quick run command** | `mvn -q test -Dtest=<ClassName>` (single test class) |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~measure on first full run (baseline: 66 tests / 9 classes) |

---

## Sampling Rate

- **After every task commit:** Run `mvn -q test -Dtest=<ClassName>` for the class touched
- **After every plan wave:** Run `mvn test` (full suite)
- **Before `/gsd-verify-work`:** Full suite must be green (baseline 66 tests not regressed)
- **Max feedback latency:** single-class run should be well under a minute

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| {N}-01-01 | 01 | 1 | HARD-01 | — | N/A | unit | `mvn -q test -Dtest=<ClassName>` | ❌ W0 | ⬜ pending |

*Planner fills concrete rows per task. Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Existing infrastructure (JUnit 5 + Mockito + `application-test.yml`) covers this phase — no new test framework install needed.
- [ ] Production-code prerequisite (flagged in RESEARCH.md): add package-private test constructors to `NflApiService`, `ExternalApiService`, `ApiFootballService`, `BallDontLieService`; widen `BallDontLieService`'s `BdlPlayersResponse`/`BdlPlayer` records to package-private.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| — | — | — | — |

*All phase behaviors have automated verification (`mvn test`).*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
