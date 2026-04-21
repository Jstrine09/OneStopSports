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

export interface MatchDto {
  id: number
  homeTeam: TeamDto
  awayTeam: TeamDto
  homeScore: number | null
  awayScore: number | null
  status: string        // SCHEDULED | TIMED | IN_PLAY | PAUSED | FINISHED | HALFTIME | etc.
  startTime: string | null  // ISO-8601 LocalDateTime from backend
  leagueId: number | null
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

// ── Match status helpers ───────────────────────────────────────────────────────

export type MatchDisplayState = 'scheduled' | 'live' | 'halftime' | 'finished' | 'other'

export function getMatchState(status: string): MatchDisplayState {
  switch (status) {
    case 'IN_PLAY':   return 'live'
    case 'PAUSED':    return 'halftime'
    case 'FINISHED':  return 'finished'
    case 'AWARDED':   return 'finished'
    case 'SCHEDULED':
    case 'TIMED':     return 'scheduled'
    default:          return 'other'
  }
}
