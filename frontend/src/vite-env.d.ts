/// <reference types="vite/client" />

// Typed access to our Vite env vars (anything prefixed VITE_ is exposed to the client).
interface ImportMetaEnv {
  // Absolute wss:// URL of the backend's STOMP WebSocket endpoint. Set in Vercel for the
  // split deploy; unset locally, where useLiveScores falls back to a same-origin URL.
  readonly VITE_WS_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
