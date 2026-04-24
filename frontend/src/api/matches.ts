import client from './client'
import type { MatchDto, MatchEventDto } from '../types'

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
