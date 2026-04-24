import client from './client'
import type { TeamDto, PlayerDto } from '../types'

export const fetchTeam = (id: number): Promise<TeamDto> =>
  client.get(`/teams/${id}`).then((r) => r.data)

export const fetchTeamPlayers = (id: number): Promise<PlayerDto[]> =>
  client.get(`/teams/${id}/players`).then((r) => r.data)
