import client from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, UserDto, TeamDto, PlayerDto } from '../types'

export const login = (data: LoginRequest): Promise<AuthResponse> =>
  client.post('/auth/login', data).then((r) => r.data)

export const register = (data: RegisterRequest): Promise<AuthResponse> =>
  client.post('/auth/register', data).then((r) => r.data)

// Trades the httpOnly refresh cookie for a fresh access token (and rotates the cookie).
// Used to restore a session on page load and after the in-memory access token expires.
export const refresh = (): Promise<AuthResponse> =>
  client.post('/auth/refresh').then((r) => r.data)

// Revokes the refresh token server-side and clears the cookie. Best-effort — the caller
// clears local state regardless of whether this succeeds.
export const logout = (): Promise<void> =>
  client.post('/auth/logout').then(() => undefined)

export const fetchMe = (): Promise<UserDto> =>
  client.get('/users/me').then((r) => r.data)

export const getFavoriteTeams = (): Promise<TeamDto[]> =>
  client.get('/users/me/favorites/teams').then((r) => r.data)

export const addFavoriteTeam = (teamId: number): Promise<void> =>
  client.post('/users/me/favorites/teams', { teamId })

export const removeFavoriteTeam = (teamId: number): Promise<void> =>
  client.delete(`/users/me/favorites/teams/${teamId}`)

export const getFavoritePlayers = (): Promise<PlayerDto[]> =>
  client.get('/users/me/favorites/players').then((r) => r.data)

export const addFavoritePlayer = (playerId: number): Promise<void> =>
  client.post('/users/me/favorites/players', { playerId })

export const removeFavoritePlayer = (playerId: number): Promise<void> =>
  client.delete(`/users/me/favorites/players/${playerId}`)
