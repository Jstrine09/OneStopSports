# OneStopSports — Project description

> Paste this into the **Project description** field of the Claude Cowork project.

---

**OneStopSports** is a Fotmob-style multi-sport web app covering football (soccer), the NBA, and the NFL. It surfaces live scores, league standings, match timelines, lineups, full team rosters, player profiles with career stats and headshots, and lets authenticated users save favourite teams and players. Live scores are pushed to the browser over WebSocket (STOMP) so the home screen updates instantly when a goal is scored.

**Stack:** Java 21 + Spring Boot 3.4.4 backend (PostgreSQL via Flyway-managed schema, Redis cache, JWT auth, Hibernate/JPA, MapStruct, Lombok) talking to five external sports APIs (football-data.org, ESPN unofficial NBA, ESPN unofficial NFL, balldontlie.io, api-sports.io football). React 18 + TypeScript + Vite frontend with Tailwind, React Query, axios, `@stomp/stompjs`. Docker Compose for full local boot.

**Current state:** Working end-to-end personal project. All seven planned entities are wired, all 5 external APIs are integrated, the live-score WebSocket push works, JWT auth works, NBA/NFL player career stats render, football player career stats render (current season, with competition column), NBA/NFL player headshots render via ESPN's CDN. 57 backend unit + slice tests pass. The frontend is responsive (mobile bottom-nav + desktop sidebar) with light/dark theming. Known incomplete: football player headshots, football match stats/lineups (free-tier-blocked), no CI pipeline, no production deployment.

The codebase is treated as a teaching project — every Java file carries plain-English inline comments aimed at a junior developer (see CONVENTIONS.md). When working on this project, preserve that style.
