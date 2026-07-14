import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { setAccessToken } from '../api/client'
import { refresh as apiRefresh, logout as apiLogout } from '../api/auth'

interface AuthContextValue {
  username: string | null
  login: (token: string, username: string) => void
  logout: () => void
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

// Auth state, redesigned for the token-security hardening:
//   • The access token is NEVER stored here or in localStorage — it lives in memory inside
//     api/client.ts (set via setAccessToken). This component just tracks the username (which
//     is not sensitive) for display and to decide whether to show signed-in UI.
//   • On first load we try a SILENT REFRESH: the browser still has the httpOnly refresh
//     cookie from a previous session, so /auth/refresh can hand us a new access token
//     without the user re-entering their password.
export function AuthProvider({ children }: { children: ReactNode }) {
  // Username is kept in localStorage purely so the signed-in UI shows instantly on reload
  // (before the silent refresh resolves). It's not a credential, so storing it is fine.
  const [username, setUsername] = useState<string | null>(() => localStorage.getItem('username'))

  // On mount, attempt to restore the session from the refresh cookie. If it works we have a
  // fresh in-memory access token; if it fails (no/expired cookie) we clear any stale username.
  useEffect(() => {
    let active = true
    apiRefresh()
      .then((res) => {
        if (!active) return
        setAccessToken(res.token)
        setUsername(res.username)
        localStorage.setItem('username', res.username)
      })
      .catch(() => {
        if (!active) return
        setAccessToken(null)
        setUsername(null)
        localStorage.removeItem('username')
      })
    return () => { active = false }
  }, [])

  // Called after a successful login/register (the API also set the refresh cookie).
  const login = (token: string, name: string) => {
    setAccessToken(token)
    setUsername(name)
    localStorage.setItem('username', name)
  }

  // Clear local state immediately, then tell the server to revoke the refresh token (best
  // effort — we don't block the UI on it, and a failure doesn't un-sign-out the user locally).
  const logout = () => {
    setAccessToken(null)
    setUsername(null)
    localStorage.removeItem('username')
    apiLogout().catch(() => { /* already cleared locally; ignore network errors */ })
  }

  return (
    <AuthContext.Provider value={{ username, login, logout, isAuthenticated: !!username }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
