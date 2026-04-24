import client from './client'
import type { LeagueDto, StandingsEntryDto, TeamDto } from '../types'

export const fetchLeague = (id: number): Promise<LeagueDto> =>
  client.get(`/leagues/${id}`).then((r) => r.data)

export const fetchStandings = (leagueId: number): Promise<StandingsEntryDto[]> =>
  client.get(`/leagues/${leagueId}/standings`).then((r) => r.data)

export const fetchTeamsByLeague = (leagueId: number): Promise<TeamDto[]> =>
  client.get(`/leagues/${leagueId}/teams`).then((r) => r.data)
