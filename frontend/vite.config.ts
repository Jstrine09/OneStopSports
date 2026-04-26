import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    // Proxy all /api calls to the Spring Boot backend — avoids CORS in dev
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Proxy WebSocket connections so the frontend can reach /ws without CORS issues.
      // SockJS starts with HTTP then upgrades to WebSocket — both need to be forwarded.
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true, // Enable WebSocket (ws://) proxying in addition to HTTP
      },
    },
  },
})
