import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'

// ── In-memory access token ───────────────────────────────────────────────────
// The short-lived access token (JWT) is kept ONLY in this module-level variable — never in
// localStorage. That's the whole point of the security hardening: an XSS payload can read
// localStorage, but it can't read a plain variable in another module's scope, and the token
// vanishes when the tab closes. On reload we recover the session from the httpOnly refresh
// cookie (see AuthContext's silent refresh), so nothing durable is exposed to JavaScript.
let accessToken: string | null = null
export const setAccessToken = (token: string | null) => { accessToken = token }
export const getAccessToken = () => accessToken

const client = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  // Send cookies (the httpOnly refresh cookie) with requests. Needed so /auth/refresh and
  // /auth/logout receive the cookie. Harmless for same-origin calls that don't set one.
  withCredentials: true,
})

// Attach the in-memory access token to every request as a Bearer header.
client.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

// ── Silent refresh on 401 ────────────────────────────────────────────────────
// When a request comes back 401 (the access token expired), transparently try ONE refresh
// using the httpOnly cookie, then replay the original request with the new token. If refresh
// fails, we clear the token and let the error propagate so the UI can send the user to login.
//
// `refreshing` de-duplicates concurrent refreshes: if several requests 401 at once, they all
// await the same single /auth/refresh call instead of firing a stampede of them.
let refreshing: Promise<string | null> | null = null

function refreshAccessToken(): Promise<string | null> {
  if (!refreshing) {
    // Use a bare axios call (not `client`) so this request skips the interceptors below and
    // can't recurse. baseURL isn't applied here, so we give the full '/api/...' path.
    refreshing = axios
      .post('/api/auth/refresh', null, { withCredentials: true })
      .then((res) => {
        setAccessToken(res.data.token)
        return res.data.token as string
      })
      .catch(() => {
        setAccessToken(null)
        return null
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    // `_retried` guards against an infinite loop: we only attempt the refresh-and-replay once.
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    const url = original?.url ?? ''

    const isAuthEndpoint = url.includes('/auth/refresh') || url.includes('/auth/login')

    if (error.response?.status === 401 && original && !original._retried && !isAuthEndpoint) {
      original._retried = true
      const token = await refreshAccessToken()
      if (token) {
        original.headers = original.headers ?? {}
        original.headers.Authorization = `Bearer ${token}`
        return client(original) // replay the original request with the fresh token
      }
    }

    return Promise.reject(error)
  },
)

export default client
