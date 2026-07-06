# Synthesis Summary

Single entry point for `gsd-roadmapper`. Produced by `gsd-doc-synthesizer` from classified
planning docs. Mode: new. Precedence: ADR (0) > SPEC > PRD > DOC.

RE-RUN NOTE: This is a re-run after breaking the prior ARCHITECTURE <-> OVERVIEW cross-reference
cycle (ARCHITECTURE.md was reworded/re-classified; its `cross_refs` is now empty). All 10 docs
now synthesize with no cycle; the prior BLOCKER is cleared. Prior intel files were overwritten.

## Doc counts by type

- Total classified: 10 (all high confidence, no UNKNOWN/low)
- ADR: 1 — DECISIONS.md (precedence override 0)
- SPEC: 2 — ARCHITECTURE.md, INTEGRATIONS.md
- PRD: 2 — PRODUCT.md, cowork/PROJECT.md
- DOC: 5 — CONVENTIONS.md, ROADMAP.md, INSTRUCTIONS.md, OVERVIEW.md, HISTORICAL_DATA_RESEARCH.md

## Synthesis coverage

- Synthesized: 10 of 10 docs. No exclusions — the cross-reference graph is acyclic this run.

## Decisions

- Captured: 24 decisions from the single ADR (DECISIONS.md), precedence 0.
- Locked: 0 (the ADR is a multi-decision log, not Accepted-status ADRs; locked left false per
  classification). No LOCKED-vs-LOCKED evaluation applies.
- File: /Users/james/Projects/OneStopSports/.planning/intel/decisions.md
- IDs: DEC-espn-over-balldontlie-nba, DEC-api-football-and-2024-cap, DEC-espn-cdn-headshots-derived,
  DEC-lazy-external-id-football, DEC-stripaccents-both-sides, DEC-sport-slug-switch-routing,
  DEC-useraccount-entity-name, DEC-secrets-in-gitignored-yml, DEC-java-records-dtos,
  DEC-lombok-quartet-entities, DEC-passwordconfig-cycle-break, DEC-restclient-not-webclient,
  DEC-custom-objectmapper-redis-stomp, DEC-matchdto-timezone-field, DEC-live-status-strings,
  DEC-nfl-division-map-hardcoded, DEC-react-query-staletime-per-type, DEC-tailwind-literal-classes,
  DEC-inline-comments-junior, DEC-security-matcher-order-entrypoint, DEC-500-to-4xx-handlers,
  DEC-accessibility-baseline, DEC-football-stale-season-badge.

## Requirements

- Captured: 14 requirements from 2 PRDs (PRODUCT.md, cowork/PROJECT.md). No competing variants.
- File: /Users/james/Projects/OneStopSports/.planning/intel/requirements.md
- IDs: REQ-multi-sport-consolidation, REQ-live-scores, REQ-standings, REQ-match-detail,
  REQ-team-rosters, REQ-player-profiles, REQ-favourites, REQ-authentication, REQ-search,
  REQ-dark-first-theme, REQ-live-feels-alive, REQ-sport-over-chrome, REQ-accessibility-wcag-aa,
  REQ-production-deploy.

## Constraints

- Captured: 19 constraints from both SPECs (ARCHITECTURE.md + INTEGRATIONS.md).
- Type breakdown: api-contract 7 (football-data.org, ESPN NBA, ESPN NFL, balldontlie, api-football,
  CDN image URLs, error-handling contract), schema 3 (layered architecture, persistence/OSIV/ddl,
  caching design), protocol 8 (multi-sport routing pattern, REST request flow, WebSocket live-push
  flow, authentication flow, anti-patterns, timezone handling, auth/identity, adding-a-new-external-API),
  nfr 1 (observability).
- ARCHITECTURE.md (previously excluded by the cycle) is now synthesized — it contributed the
  layered-architecture, multi-sport-routing, REST/WebSocket flow, persistence, caching, error-handling,
  and anti-patterns constraints.
- File: /Users/james/Projects/OneStopSports/.planning/intel/constraints.md

## Context topics

- Captured: 17 topics from all 5 DOCs (CONVENTIONS.md, ROADMAP.md, INSTRUCTIONS.md, OVERVIEW.md,
  HISTORICAL_DATA_RESEARCH.md). OVERVIEW.md (previously excluded by the cycle) is now synthesized.
- File: /Users/james/Projects/OneStopSports/.planning/intel/context.md
- Topics: orientation/what-the-app-is, REST surface, backend conventions, frontend conventions,
  critical gotchas, tooling, project memory, QA remediation status, known incomplete features,
  testing gaps, external API risks, data model concerns, security concerns, operational concerns,
  priority follow-ups, three-sources-of-truth note, historical data research.

## Conflicts

- BLOCKERS: 0 (prior ARCHITECTURE <-> OVERVIEW cycle resolved — no cycle this run)
- WARNINGS: 0 (no competing acceptance variants)
- INFO: 3 (cycle resolved since prior run; glassmorphism supersession consistent; ADR precedence-0
  uncontested + root-level product docs flagged for alignment)
- Detail: /Users/james/Projects/OneStopSports/.planning/INGEST-CONFLICTS.md

## Status

READY — all 10 docs synthesized, no blockers, no competing variants. Safe to route to
`gsd-roadmapper`. Downstream note: README.md and CLAUDE.md exist at the project root outside this
ingest set and are flagged by ROADMAP.md as additional sources of truth to align.
