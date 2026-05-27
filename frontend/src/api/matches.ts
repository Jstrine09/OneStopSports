import client from './client'
import type { BoxScoreDto, MatchDto, MatchEventDto } from '../types'

export const fetchLiveMatches = (): Promise<MatchDto[]> =>
  client.get('/matches/live').then((r) => r.data)

export const fetchMatch = (id: number): Promise<MatchDto> =>
  client.get(`/matches/${id}`).then((r) => r.data)

export const fetchMatchesByLeagueAndDate = (
  leagueId: number,
  date: string          // YYYY-MM-DD
): Promise<MatchDto[]> =>
  client.get('/matches', { params: { league: leagueId, date } }).then((r) => r.data)

export const fetchMatchEvents = (id: number): Promise<MatchEventDto[]> =>
  client.get(`/matches/${id}/events`).then((r) => r.data)

// Fetches the box score for a match (team stats + per-player stat tables).
// leagueId is required so the backend can route to the correct external API.
// Returns undefined (not an error) when the backend responds with 204 No Content —
// this happens when the game hasn't been played yet or ESPN returned no data.
export const fetchBoxScore = (id: number, leagueId: number): Promise<BoxScoreDto | undefined> =>
  client.get(`/matches/${id}/boxscore`, { params: { leagueId } }).then((r) =>
    r.status === 204 ? undefined : r.data
  )
