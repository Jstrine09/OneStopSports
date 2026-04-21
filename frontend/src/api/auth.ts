import client from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, UserDto } from '../types'

export const login = (data: LoginRequest): Promise<AuthResponse> =>
  client.post('/auth/login', data).then((r) => r.data)

export const register = (data: RegisterRequest): Promise<AuthResponse> =>
  client.post('/auth/register', data).then((r) => r.data)

export const fetchMe = (): Promise<UserDto> =>
  client.get('/users/me').then((r) => r.data)

export const addFavoriteTeam = (teamId: number): Promise<void> =>
  client.post('/users/me/favorites/teams', { teamId })

export const removeFavoriteTeam = (teamId: number): Promise<void> =>
  client.delete(`/users/me/favorites/teams/${teamId}`)
