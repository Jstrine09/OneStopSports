import client from './client'
import type { PlayerBioDto, PlayerCareerStatsDto, PlayerDto } from '../types'

export const fetchPlayer = (id: number): Promise<PlayerDto> =>
  client.get(`/players/${id}`).then((r) => r.data)

// Fetches NBA bio enrichment from balldontlie.io via our backend proxy.
// Returns null when the backend responds with 204 (player not in balldontlie — e.g. football/NFL).
// The component treats null the same as "no bio" and simply hides the bio card.
export const fetchPlayerBio = async (id: number): Promise<PlayerBioDto | null> => {
  const response = await client.get(`/players/${id}/bio`, {
    validateStatus: (s) => s === 200 || s === 204,
  })
  return response.status === 204 ? null : (response.data as PlayerBioDto)
}

// Fetches a player's career stats. Routes server-side to ESPN (NBA/NFL) or
// API-Football (soccer). Returns null on 204 (player has no externalId, the
// upstream API doesn't know them, or the sport has no stats integration).
// The component treats null as "no stats" and hides the stats card.
export const fetchPlayerCareerStats = async (id: number): Promise<PlayerCareerStatsDto | null> => {
  const response = await client.get(`/players/${id}/career-stats`, {
    validateStatus: (s) => s === 200 || s === 204,
  })
  return response.status === 204 ? null : (response.data as PlayerCareerStatsDto)
}
