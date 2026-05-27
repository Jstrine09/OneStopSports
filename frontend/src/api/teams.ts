import client from './client'
import type { TeamDto, PlayerDto } from '../types'

export const fetchTeam = (id: number): Promise<TeamDto> =>
  client.get(`/teams/${id}`).then((r) => r.data)

// Without season: returns the current squad from the DB.
// With season: fetches the historical roster from ESPN (NBA/NFL only).
// season = start year of the season — e.g. 2022 for "2022-23". ESPN convention.
export const fetchTeamPlayers = (id: number, season?: number): Promise<PlayerDto[]> =>
  client.get(`/teams/${id}/players`, season != null ? { params: { season } } : {}).then((r) => r.data)
