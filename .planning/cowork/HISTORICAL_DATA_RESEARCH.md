# OneStopSports — Historical Data Research

> **What this doc is:** A full assessment of every viable API option for adding historical matchup and stats tracking to OneStopSports. Covers all three sports. Ranked by cost and data depth. Written against the current stack (see `INTEGRATIONS.md`) so every recommendation is grounded in what's already wired.

---

## What "historical data" means for this app

To build out matchup tracking and stats history, we need the following per sport:

| Feature | Football (Soccer) | NBA | NFL |
|---------|------------------|-----|-----|
| Historical fixture results | ✅ by date/season | ✅ by date/season | ✅ by date/season |
| Head-to-head records | ✅ dedicated endpoint | manual (filter by teams) | manual (filter by teams) |
| Historical league standings | ✅ by season | ✅ by season | ✅ by season |
| Per-season player stats | ✅ (limited free) | ✅ | ✅ |
| Career / multi-season player stats | ✅ (paid) | ✅ | ✅ |
| Game-level box scores | ❌ not exposed by current APIs | needs paid tier | needs paid tier |
| Play-by-play | ❌ | needs paid tier | needs paid tier |

The current stack covers live/current-season well but is blind to anything more than one season old.

---

## Football (Soccer)

### Option 1 — API-Football Pro ($19/month) ⭐ Best value upgrade

**Already in the stack** as `ApiFootballService`. Upgrade the existing free account to unlock:

| | Free (now) | Pro ($19/month) |
|--|------------|-----------------|
| Requests/day | 100 | 7,500 |
| Seasons accessible | 2024 only | All historical seasons |
| Head-to-head endpoint | ✓ (rate-limited) | ✓ (usable) |
| Live data | ❌ | ✅ |
| Stats depth | Limited | Full: xG, shots, possession, RATING |

**Dedicated head-to-head endpoint:**
```
GET https://v3.football.api-sports.io/fixtures/headtohead?h2h={teamId}-{teamId}
```
Supports parameters: `league`, `season`, `date`, `from`, `to`, `status`, `timezone`. Returns all historical meetings between two teams with full match stats.

**Historical fixture endpoint:**
```
GET /fixtures?league={id}&season={year}
```
Returns every match for a competition and season — results, lineups, stats, events, odds.

**What changes in code:** Remove `FREE_TIER_MAX_SEASON = 2024` cap in `ApiFootballService`, increase request budget, add a new `fetchH2H(teamAId, teamBId)` method, add server-side `@Cacheable` with a 24h TTL (covered in ROADMAP as a known gap).

**Coverage:** 1,100+ leagues, 200+ countries, back to roughly 2010 for major leagues.

**Verdict:** This is the single best-value upgrade for football — it fixes the known season-cap bug AND adds head-to-head. At $19/month it fits a personal project budget.

---

### Option 2 — football-data.org paid plans (€29–€99/month)

**Already in the stack** as `ExternalApiService`. Current use is squads + fixtures + standings for the 6 seeded leagues.

| Tier | Price | Leagues | Historical |
|------|-------|---------|------------|
| Free (now) | €0 | 12, delayed | Current season only |
| Free + Three | €29/month | 15, live | Historical included |
| Standard | €49/month | 25, live | Historical included |
| Advanced | €99/month | 50, live | Historical included |

**Historical access** unlocks past seasons of fixtures, standings, and squad data for any covered league. No head-to-head endpoint — you'd need to build that by filtering `/competitions/{id}/matches` by season and team.

**What changes in code:** Remove the sleep-6.2s throttle (10 req/min goes up on paid tiers), start querying `/competitions/{id}/matches?season={year}` for historical seasons.

**Verdict:** Only worthwhile if you want to expand league coverage significantly. The football-data.org data model is clean but lacks the depth of API-Football (no per-match xG, no player-level stats, no head-to-head). For historical matchups specifically, API-Football Pro is superior at a similar or lower price.

---

### Option 3 — TheStatsAPI ($50/month, no free tier)

A newer provider specifically designed as a football stats API. Self-described as the "stats layer" missing from football-data.org.

| Feature | Detail |
|---------|--------|
| Historical depth | 10 years across 80 competitions (up to 1,196 on request) |
| Player stats | 84,000+ players with season stats per plan |
| Match stats | xG, possession, shots, passes, cards (live + final) |
| Head-to-head | ✅ — built in |
| Free tier | ❌ — 7-day trial only |
| Rate limit | 30 calls/min on Starter |
| Price | $50/month (Starter), $150/month (Growth), $500/month (Scale) |

**No feature gating between plans** — every endpoint is available on every tier. You're paying for request volume, not features.

**Verdict:** The most developer-friendly football API for historical stats work. Better xG/advanced metrics than API-Football. But at $50/month (vs $19), it's a harder sell for a side project unless you want xG and advanced match analytics as core features. Good option when/if this becomes a commercial product.

---

### Option 4 — BSD Free Football API (bzzoiro.com) — Free, worth evaluating

A lesser-known but apparently generous free API:

- **63,000+ matches since 2004** across PL, La Liga, Serie A, Bundesliga, Ligue 1, Championship, Liga Portugal, Eredivisie
- **Advanced stats:** xG, xA, per-shot xG, shot maps, heatmaps, goal build-up sequences
- **Multi-bookmaker odds** included
- **Claims no rate limits** on the free tier
- **No API key required** (public)

**Risk:** Completely undocumented and even more unofficial than ESPN's API. No SLA, no changelog, no guarantee of continuity. Should not be used as a primary source — but could be useful for backfilling xG and shot data that doesn't exist anywhere else for free.

**Verdict:** Worth a spike to see what the data actually looks like. Not a foundation to build on.

---

### Option 5 — Sportmonks (€25–€269/month)

Expensive for what a personal project needs. Historical data beyond 3 seasons is a one-time add-on purchase on top of the monthly fee. Enterprise plans include full history.

**Verdict:** Overkill unless you're building a commercial product. Skip for now.

---

## NBA Basketball

### Option 1 — balldontlie paid tier ($9.99/month) ⭐ Best value upgrade

**Already in the stack** as `BallDontLieService` (free tier). Upgrading unlocks the full game and stats history.

| | Free (now) | Paid ($9.99/month) | GOAT ($39.99/month) |
|--|------------|---------------------|----------------------|
| Endpoints | /players, /teams | + /games, /box_scores | + advanced stats, DFS |
| Historical | Bio only | **Games from 1946** | Games + advanced stats |
| Rate limit | 5 req/min | higher | higher |

**Key new endpoints unlocked at $9.99/month:**
```
GET /games?seasons[]=2024&team_ids[]=14       all games for a team/season
GET /box_scores?game_ids[]=...                full box score (player + team stats)
GET /season_averages?season=2024&player_ids[]=175  season-level averages
```

**Data depth at $9.99/month:**
- Every NBA game back to the 1946-47 season
- Per-game box scores (points, rebounds, assists, steals, blocks, turnovers, FG%, 3P%, FT%)
- Season averages per player
- Game results (winner, score, date, arena)

**Head-to-head:** No dedicated endpoint, but you can construct it: `GET /games?team_ids[]={a}&team_ids[]={b}` returns all games featuring both teams. Filter in code.

**What changes in code:** Upgrade the balldontlie API key, create new service methods in `BallDontLieService` for games and box scores. The auth pattern is the same (`Authorization: <key>` without Bearer).

**Note:** balldontlie has expanded to NFL too (see below).

**Verdict:** At $9.99/month, this is the cheapest way to add NBA game history with box scores. The data goes back to 1946, which is deeper than any other option at this price point.

---

### Option 2 — ESPN unofficial API (Free, already in stack)

The ESPN API **already supports historical data** via date-based scoreboard lookups. This is largely untapped in the current code:

```
GET /scoreboard?dates=20250101                   all games on a specific date
GET /teams/{id}/schedule?season=2024&seasontype=2   full season schedule + results
GET /summary?event={eventId}                     full game summary, boxscore, play-by-play
```

The `summary` endpoint is particularly powerful — it returns full box scores (all player stats per game) and play-by-play data at no cost.

**Historical depth:** ESPN stores data going back many seasons (commonly 10+ years) for both NBA and NFL.

**Limitation:** No rate limit documentation; unofficial; no SLA. Structure can change without warning. Already mitigated in the codebase with `@JsonIgnoreProperties(ignoreUnknown = true)`.

**What changes in code:** Add a `fetchGameSummary(eventId)` method to `NbaApiService` that calls `/summary?event={id}`. The `eventId` is already returned in the scoreboard response we call today. Map box score data to a new `BoxScoreDto`.

**Verdict:** The best free option. The data is already being partially fetched (we call scoreboard) — we just don't drill into the `summary` endpoint. This should be the first thing wired for NBA/NFL history.

---

### Option 3 — MySportsFeeds (Free for personal/non-commercial use) ⭐ Hidden gem

MySportsFeeds offers **free access for personal/private/non-commercial use** with historical data included at no extra charge.

| Feature | Detail |
|---------|--------|
| NBA coverage | Schedules, scores, box scores, standings, play-by-play, lineups, injuries, DFS, odds |
| Historical | Free — they explicitly don't charge for historical seasons |
| Format | JSON, XML, CSV |
| Rate limit | Reasonable for personal use |
| Auth | API key via registration |
| Commercial use | Paid plans required |

**Endpoints of interest:**
```
GET /pull/nba/2024-2025/games.json              full season game results
GET /pull/nba/2024-2025/game_boxscore.json?game={id}  box score per game
GET /pull/nba/2024-2025/player_stats_totals.json       season totals
GET /pull/nba/2024-2025/standings.json                 standings by date
```

**Head-to-head:** No dedicated endpoint but game-level data lets you filter.

**Caveat:** "Personal use" is key — if this ever becomes a commercial product, you'd need to upgrade. But for a side project, this is an excellent free option.

**Verdict:** Strongest free option for structured historical NBA data with box scores. The free personal-use tier is more capable than balldontlie's $9.99/month tier, but the ToS restricts commercial use.

---

## NFL American Football

### Option 1 — ESPN unofficial API (Free, already in stack) ⭐ Best free option

Same story as NBA — the existing ESPN integration already has historical reach we're not using.

```
GET /teams/{id}/schedule?season=2024&seasontype=2   full regular season schedule
GET /teams/{id}/schedule?season=2024&seasontype=3   playoff schedule
GET /summary?event={eventId}                        full game summary + box score
GET /scoreboard?dates=20250101                      games on a date
```

The `summary` endpoint returns team stats (passing yards, rushing yards, turnovers, possession time, third-down conversions) and player stats (QBR, passing/rushing/receiving lines) for any historical game.

**Verdict:** Wire this first — zero cost, same pattern as current NFL code, same `@JsonIgnoreProperties` guard.

---

### Option 2 — balldontlie ($9.99/month or ALL-ACCESS $299.99/month)

balldontlie has expanded beyond NBA. NFL data is available at the $9.99/month tier (or via ALL-ACCESS which covers every sport).

NFL coverage at $9.99/month includes:
- Games by season/team
- Box scores
- Player season stats

**Verdict:** If you're already paying $9.99/month for NBA, check whether NFL is included in the same tier or requires a separate subscription. The ALL-ACCESS tier ($299.99/month) covers every sport including soccer, which could consolidate all three sports into one provider.

---

### Option 3 — MySportsFeeds (Free for personal use)

Covers NFL with the same generous personal-use free tier as NBA:

```
GET /pull/nfl/2024-2025/games.json
GET /pull/nfl/2024-2025/game_boxscore.json?game={id}
GET /pull/nfl/2024-2025/player_stats_totals.json
```

Full play-by-play, lineups, injuries, DFS projections included.

**Verdict:** Same trade-off as NBA — excellent free personal-use tier, but ToS becomes an issue if commercialised.

---

## Summary Comparison Table

| Provider | Sport | Cost | Historical Depth | Head-to-Head | Box Scores | Key Risk |
|---------|-------|------|-----------------|--------------|------------|---------|
| **API-Football Pro** | Soccer | $19/month | All seasons (~2010+) | ✅ dedicated endpoint | ✅ | Rate-limited (7,500/day) |
| **TheStatsAPI Starter** | Soccer | $50/month | 10 years | ✅ | ✅ with xG | No free tier |
| **football-data.org Standard** | Soccer | €49/month | Historical included | ❌ build it | ❌ (no stats) | Expensive for what you get |
| **BSD Free Football API** | Soccer | Free | 2004–present | ❌ build it | Partial | Unofficial, undocumented |
| **ESPN (unofficial)** | NBA + NFL | **Free** | 10+ years | ❌ build it | ✅ via /summary | No SLA, can break |
| **balldontlie paid** | NBA (+NFL) | $9.99/month | NBA 1946–present | ❌ build it | ✅ | First-name search quirks |
| **MySportsFeeds** | NBA + NFL | **Free** (personal) | Full historical | ❌ build it | ✅ | ToS: personal use only |
| **SportsDataIO** | NBA + NFL | Paid trial available | Comprehensive | ✅ | ✅ | Expensive at scale |

---

## Recommended Path (ranked by cost/effort)

### Phase 1 — Zero extra cost (do first)

**Wire ESPN's `/summary` endpoint for NBA and NFL.** The event ID is already in the scoreboard response we fetch every 30 seconds. Add:
- `NbaApiService.fetchGameSummary(eventId)` — maps box score + team stats to a new `GameSummaryDto`
- Same for `NflApiService`
- New controller: `GET /api/matches/{id}/boxscore` — returns the full box score
- Store nothing — the summary endpoint is fast enough to call on demand and cache with a 5-minute TTL in Redis

This gives us **historical game detail** for any NBA/NFL game going back many years, at no additional cost, using the same auth model we already have.

### Phase 2 — $19/month for full soccer history

**Upgrade API-Football to Pro.** This one change:
- Removes the `FREE_TIER_MAX_SEASON = 2024` cap (current-season stats work correctly)
- Gives 7,500 requests/day (vs 100 — enables server-side caching without paranoia)
- Unlocks the head-to-head endpoint for any two football teams
- Enables a historical fixture browser for any of the 6 seeded leagues

Code changes are minimal — remove the cap constant, add `fetchH2H()`, add `@Cacheable` on football stats.

### Phase 3 — $9.99/month for NBA game history

**Upgrade balldontlie** if structured NBA historical data (box scores, season averages) is needed independently of the ESPN informal API. The 1946-present depth is unique.

### Phase 4 — MySportsFeeds (if staying personal/non-commercial)

If the ToS allows (strictly personal, never monetised), MySportsFeeds gives free access to full historical NFL + NBA including box scores and play-by-play. New `MysportsfeedsApiService` class following the same adapter pattern.

---

## Data model implications

Adding historical tracking will require new DB tables. Planned migrations would be:

| Table | Columns | Notes |
|-------|---------|-------|
| `match_result` | `id`, `home_team_id`, `away_team_id`, `league_id`, `match_date`, `home_score`, `away_score`, `season`, `source_id` | Persisted game results — doesn't change after final whistle |
| `player_season_stats` | `id`, `player_id`, `season`, `league_id`, `stats JSON`, `fetched_at` | One row per player per season; JSON blob avoids schema per sport |
| `team_h2h_cache` | `team_a_id`, `team_b_id`, `data JSON`, `fetched_at` | Optional materialised cache of head-to-head summaries |

**Key decision:** Live match data stays ephemeral (Redis-only). Historical match results should be persisted — they never change and calling an API every time is wasteful. A background job can backfill historical seasons once per league/season pair.

---

## What NOT to build on

- **Sportradar** — comprehensive but very expensive; designed for commercial products
- **SportsDataIO at full price** — same issue; Discovery Lab free tier is useful for prototyping only
- **BSD Free Football API as primary source** — too undocumented and fragile
- **Adding a Match/Game table for live data** — the ARCHITECTURE.md explicitly calls this an anti-pattern (live data should stay ephemeral in Redis)

---

*Research date: 2026-05-26*
