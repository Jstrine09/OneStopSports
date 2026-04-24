import client from './client'
import type { LeagueDto, SportDto } from '../types'

export const fetchSports = (): Promise<SportDto[]> =>
  client.get('/sports').then((r) => r.data)

export const fetchLeaguesBySport = (slug: string): Promise<LeagueDto[]> =>
  client.get(`/sports/${slug}/leagues`).then((r) => r.data)
