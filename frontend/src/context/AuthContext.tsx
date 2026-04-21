import { createContext, useContext, useState, type ReactNode } from 'react'

interface AuthState {
  token: string | null
  username: string | null
}

interface AuthContextValue extends AuthState {
  login:  (token: string, username: string) => void
  logout: () => void
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    token:    localStorage.getItem('token'),
    username: localStorage.getItem('username'),
  })

  const login = (token: string, username: string) => {
    localStorage.setItem('token',    token)
    localStorage.setItem('username', username)
    setState({ token, username })
  }

  const logout = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    setState({ token: null, username: null })
  }

  return (
    <AuthContext.Provider value={{ ...state, login, logout, isAuthenticated: !!state.token }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
