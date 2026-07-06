# Requirements Intel

Extracted from PRD-type sources. Two PRDs cover complementary scope: PRODUCT.md defines
product vision, users, brand, and design/accessibility principles; PROJECT.md defines the
feature surface, stack, and current deployed state. Where both touch the same feature, they
agree (no divergent acceptance criteria), so no competing variants were produced.

Source PRDs:
- /Users/james/Projects/OneStopSports/PRODUCT.md (product vision / design principles)
- /Users/james/Projects/OneStopSports/.planning/cowork/PROJECT.md (feature + stack overview)

---

## REQ-multi-sport-consolidation
- source: /Users/james/Projects/OneStopSports/PRODUCT.md, /Users/james/Projects/OneStopSports/.planning/cowork/PROJECT.md
- scope: core product purpose
- description: Consolidate football (soccer), NBA, and NFL into one app so a user never needs a separate app per sport. Fotmob-inspired.
- acceptance: A single app surfaces all three sports' live scores, standings, match detail, rosters, and player profiles.

## REQ-live-scores
- source: /Users/james/Projects/OneStopSports/PRODUCT.md, /Users/james/Projects/OneStopSports/.planning/cowork/PROJECT.md
- scope: live match scores
- description: Surface live scores; the primary task on any screen is "what's happening in a match right now". Scores are pushed to the browser over WebSocket (STOMP) so the home screen updates instantly (e.g. when a goal is scored).
- acceptance: Live scores update in real time via WebSocket push; REST polling is the fallback.

## REQ-standings
- source: /Users/james/Projects/OneStopSports/PRODUCT.md, /Users/james/Projects/OneStopSports/.planning/cowork/PROJECT.md
- scope: league standings
- description: Show league standings / how a user's team sits in the table (a primary secondary task).
- acceptance: Standings render per league; NBA groups by conference; NFL by division.

## REQ-match-detail
- source: /Users/james/Projects/OneStopSports/PRODUCT.md, /Users/james/Projects/OneStopSports/.planning/cowork/PROJECT.md
- scope: match detail
- description: Match detail including timelines, box scores, and (where available) lineups. Live game clock shown.
- acceptance: A match page shows detail + box score + event timeline; match stats/lineups are stubbed on football free tier.

## REQ-team-rosters
- source: /Users/james/Projects/OneStopSports/PRODUCT.md, /Users/james/Projects/OneStopSports/.planning/cowork/PROJECT.md
- scope: team rosters
- description: Browse full team rosters (a secondary task).
- acceptance: A team page lists its full roster.

## REQ-player-profiles
- source: /Users/james/Projects/OneStopSports/PRODUCT.md, /Users/james/Projects/OneStopSports/.planning/cowork/PROJECT.md
- scope: player profiles
- description: Player profiles with career stats (3 sports), bio, and headshots.
- acceptance: A player page shows career stats where available, bio (NBA only), and an ESPN-CDN headshot (NBA/NFL); football stats are single-season capped at 2024.

## REQ-favourites
- source: /Users/james/Projects/OneStopSports/PRODUCT.md, /Users/james/Projects/OneStopSports/.planning/cowork/PROJECT.md
- scope: user favourites
- description: Authenticated users can save favourite teams and players.
- acceptance: A logged-in user can add/remove favourite teams and players; guarded by JWT auth.

## REQ-authentication
- source: /Users/james/Projects/OneStopSports/.planning/cowork/PROJECT.md
- scope: auth
- description: Self-hosted username/password registration + login with JWT bearer tokens.
- acceptance: Users register and log in; protected endpoints require a valid Bearer JWT.

## REQ-search
- source: /Users/james/Projects/OneStopSports/.planning/cowork/PROJECT.md
- scope: global search
- description: Global search across teams and players, accent-insensitive.
- acceptance: A query (min length) returns matching teams + players; "Dembele" matches "Dembélé".

## REQ-dark-first-theme
- source: /Users/james/Projects/OneStopSports/PRODUCT.md
- scope: visual theme / design principle
- description: Dark is the primary theme by conviction (users check scores in stadiums, on sofas, at night) — with depth and intention, not just low brightness. Avoid generic SaaS/dashboard, old-school ESPN/Sky clutter, and cookie-cutter inverted-slate dark mode.
- acceptance: The app ships a purpose-built dark-first theme; a light variant exists but dark is primary.

## REQ-live-feels-alive
- source: /Users/james/Projects/OneStopSports/PRODUCT.md
- scope: design principle — motion/energy
- description: The UI communicates immediacy and energy, especially on live-score and match pages; motion earns its place (real-time updates justify animation). Brand: electric, precise, present.
- acceptance: Live/match screens use motion that reinforces real-time updates; motion is not decorative-only.

## REQ-sport-over-chrome
- source: /Users/james/Projects/OneStopSports/PRODUCT.md
- scope: design principle — content hierarchy
- description: Team crests, scores, and stats are the content; navigation/borders/decoration serve them. Density with rhythm — pack information tightly with clear hierarchy and breathing room between groups.
- acceptance: Data is foregrounded; chrome recedes; grouped density with clear hierarchy rather than uniform padding.

## REQ-accessibility-wcag-aa
- source: /Users/james/Projects/OneStopSports/PRODUCT.md
- scope: accessibility (NFR)
- description: WCAG AA minimum. Dark-first requires rigorous contrast checks (low-contrast text on dark surfaces is the common failure mode). Respect `prefers-reduced-motion` for all non-data animations.
- acceptance: Contrast meets WCAG AA; non-data animations honour `prefers-reduced-motion`.

## REQ-production-deploy
- source: /Users/james/Projects/OneStopSports/.planning/cowork/PROJECT.md
- scope: deployment / operational
- description: Publicly deployed as Vercel (frontend) + Render (backend) + Neon (Postgres), with CORS/WS origins locked to real deploy domains, an installable PWA, and free-tier cold starts mitigated by an external UptimeRobot monitor.
- acceptance: App is reachable publicly; CORS/WS restricted to deploy domains; frontend installable as a PWA.
