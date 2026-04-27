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
      // ws: true tells Vite to upgrade the connection to WebSocket when the client requests it.
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true, // Forward ws:// connections to the backend (plain WebSocket, no SockJS)
      },
    },
  },
})
