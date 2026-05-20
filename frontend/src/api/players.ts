import client from './client'
import type { PlayerBioDto, PlayerDto } from '../types'

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
