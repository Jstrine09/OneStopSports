import client from './client'
import type { TeamDto, PlayerDto } from '../types'

// Mirrors the Java SearchResultDto record — teams and players whose names match the query
export interface SearchResultDto {
  teams: TeamDto[]
  players: PlayerDto[]
}

// GET /api/search?q={query}
// Backend requires at least 2 characters — returns empty lists for shorter queries.
// Capped at 8 teams and 10 players server-side.
export const searchAll = (q: string): Promise<SearchResultDto> =>
  client.get('/search', { params: { q } }).then((r) => r.data)
