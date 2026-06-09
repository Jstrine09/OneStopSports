# OneStopSports — Project description

> Paste this into the **Project description** field of the Claude Cowork project.

---

**OneStopSports** is a Fotmob-style multi-sport web app covering football (soccer), the NBA, and the NFL. It surfaces live scores, league standings, match timelines, lineups, full team rosters, player profiles with career stats and headshots, and lets authenticated users save favourite teams and players. Live scores are pushed to the browser over WebSocket (STOMP) so the home screen updates instantly when a goal is scored.

**Stack:** Java 21 + Spring Boot 3.4.4 backend (PostgreSQL via Flyway-managed schema, Redis cache, JWT auth, Hibernate/JPA, MapStruct, Lombok) talking to five external sports APIs (football-data.org, ESPN unofficial NBA, ESPN unofficial NFL, balldontlie.io, api-sports.io football). React 18 + TypeScript + Vite frontend with Tailwind, React Query, axios, `@stomp/stompjs`. Docker Compose for full local boot.

**Current state:** Working end-to-end personal project. All seven entities + all 5 external APIs are wired; live-score WebSocket push, JWT auth, player career stats (3 sports) + bio + ESPN-CDN headshots, live game clock, and match box score + event timeline all work. The frontend went through a full "sport-field" redesign (animated pitch/court/gridiron backdrops + glass surfaces, shared `SectionLabel`/`RowCard` primitives, light/dark, responsive). Production deploy is set up (Render + Neon, single-origin Docker). A 5-persona QA pass fixed the top blockers (auth bypass, 500→4xx, accessibility focus/reduced-motion, stale-data badge). 57 backend tests pass. Known incomplete (free-tier limits): football stats are single-season capped at 2024, football match stats/lineups stubbed. Top open issues: NBA standings don't group by conference, search isn't accent-insensitive (see ROADMAP.md).

The codebase is treated as a teaching project — every Java file carries plain-English inline comments aimed at a junior developer (see CONVENTIONS.md). When working on this project, preserve that style.
