// ── Outbound DTOs (mirror the Java records from the backend) ──────────────────

export interface SportDto {
  id: number
  name: string
  slug: string
  iconUrl: string | null
}

export interface LeagueDto {
  id: number
  name: string
  country: string
  logoUrl: string | null
  season: string
  sportId: number
  externalId: number | null
}

export interface TeamDto {
  id: number
  name: string
  shortName: string
  crestUrl: string | null
  stadium: string | null
  country: string | null
  leagueId: number | null
}

export interface PlayerDto {
  id: number
  name: string
  position: string | null
  nationality: string | null
  dateOfBirth: string | null
  jerseyNumber: number | null
  photoUrl: string | null
  teamId: number
}

// Biographical enrichment from balldontlie.io — NBA players only.
// All fields are optional; undrafted players have null draft fields.
export interface PlayerBioDto {
  height: string | null         // e.g. "6-8" (feet-inches)
  weightPounds: number | null   // e.g. 210
  college: string | null        // e.g. "Duke" — null for international players
  draftYear: number | null      // e.g. 2017 — null if undrafted
  draftRound: number | null     // e.g. 1 — null if undrafted
  draftNumber: number | null    // e.g. 3 (overall pick) — null if undrafted
}

// Career stats payload — sport-agnostic shape that fits NBA, NFL, and football.
// One PlayerCareerStatsDto contains 1-3 categories; each category renders as its
// own table on the frontend (column headers + season rows + optional career row).
//
// NBA  → categories: "averages", "totals", "miscellaneous"
// NFL  → categories: position-driven, e.g. "passing", "rushing", "receiving", "defensive"
// Football → categories: typically a single "season" block (current season only on free tier)
export interface PlayerCareerStatsDto {
  sport: string                        // "basketball" | "american-football" | "football"
  categories: StatCategoryDto[]
}

export interface StatCategoryDto {
  name: string                         // machine name — e.g. "averages"
  displayName: string                  // human header — e.g. "Per Game"
  labels: string[]                     // column headers, aligned with each SeasonRow.values
  seasons: SeasonRowDto[]              // per-season rows, oldest first
  career: SeasonRowDto | null          // career aggregate (null when upstream doesn't provide it)
}

export interface SeasonRowDto {
  season: string | null                // e.g. "2024-25" or null for the career row
  team: string | null                  // abbreviation or slug, null for the career row
  values: string[]                     // aligned with the parent category's labels[]
}

export interface MatchDto {
  id: number
  homeTeam: TeamDto
  awayTeam: TeamDto
  homeScore: number | null
  awayScore: number | null
  status: string        // SCHEDULED | TIMED | IN_PLAY | PAUSED | FINISHED | HALFTIME | etc.
  startTime: string | null  // NBA/NFL: already converted to ET. Football: UTC.
  leagueId: number | null
  timezone: string | null   // "ET" for NBA/NFL — shown as a label next to the time. null for football.
}

export interface StandingsEntryDto {
  position: number
  team: TeamDto
  played: number
  won: number
  drawn: number
  lost: number
  goalsFor: number
  goalsAgainst: number
  points: number
  // NFL only — null for football/NBA. When non-null the frontend renders
  // a conference → division grouped table instead of a flat league table.
  conference: string | null
  division: string | null
}

export interface UserDto {
  id: number
  username: string
  email: string
  avatarUrl: string | null
  createdAt: string
}

// ── Inbound / Auth ─────────────────────────────────────────────────────────────

export interface AuthResponse {
  token: string
  username: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface MatchEventDto {
  type: string            // "GOAL" | "OWN_GOAL" | "PENALTY" | "YELLOW_CARD" | "RED_CARD" | "YELLOW_RED_CARD" | "SUBSTITUTION"
  minute: number | null
  injuryMinute: number | null
  playerName: string | null
  assistName: string | null
  teamName: string | null
}

// ── Match status helpers ───────────────────────────────────────────────────────

export type MatchDisplayState = 'scheduled' | 'live' | 'halftime' | 'finished' | 'other'

export function getMatchState(status: string): MatchDisplayState {
  switch (status) {
    case 'IN_PLAY':   return 'live'
    case 'LIVE':      return 'live'     // NBA status — NbaApiService.mapStatus() returns "LIVE"
    case 'PAUSED':    return 'halftime'
    case 'FINISHED':  return 'finished'
    case 'AWARDED':   return 'finished'
    case 'SCHEDULED':
    case 'TIMED':     return 'scheduled'
    default:          return 'other'
  }
}
