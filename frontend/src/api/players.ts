import client from './client'
import type { PlayerDto } from '../types'

export const fetchPlayer = (id: number): Promise<PlayerDto> =>
  client.get(`/players/${id}`).then((r) => r.data)
