import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    // Adds a service worker so the app is installable (Android Chrome install prompt) and
    // its shell works offline. We keep the existing hand-written public/manifest.json
    // (manifest: false) and just let Workbox precache the built assets + auto-register the SW.
    VitePWA({
      registerType: 'autoUpdate',
      // 'script-defer' emits an external registerSW.js and references it with a
      // <script src> — instead of injecting an INLINE registration script. That keeps our
      // Content-Security-Policy's script-src at a strict 'self' (an inline script would
      // otherwise be blocked, silently breaking service-worker registration in production).
      injectRegister: 'script-defer',
      manifest: false, // use the existing public/manifest.json + its <link> in index.html
      workbox: {
        // Precache the app shell. Never let the SW intercept API or WebSocket traffic —
        // live data must always hit the network (and /ws is proxied straight to Render).
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//, /^\/ws/],
        globPatterns: ['**/*.{js,css,html,ico,png,svg,webmanifest}'],
      },
    }),
  ],
  server: {
    port: 3000,
    // Proxy all /api calls to the Spring Boot backend — avoids CORS in dev
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      // Proxy WebSocket connections so the frontend can reach /ws without CORS issues.
      // ws: true tells Vite to upgrade the connection to WebSocket when the client requests it.
      '/ws': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        ws: true, // Forward ws:// connections to the backend (plain WebSocket, no SockJS)
      },
    },
  },
})
