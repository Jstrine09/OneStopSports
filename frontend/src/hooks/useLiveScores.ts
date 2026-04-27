import { useEffect } from 'react'
import { Client } from '@stomp/stompjs'
import type { MatchDto } from '../types'

// useLiveScores connects to the backend's WebSocket endpoint and subscribes to
// /topic/matches/live. Whenever the server pushes a score update, the provided
// callback is called with the full updated match list.
//
// The connection is opened when the component mounts and cleanly closed when it
// unmounts (e.g. the user navigates away from the Live Scores page).
//
// How it works end-to-end:
// 1. Backend scheduler (ExternalApiService.refreshLiveMatchCache) runs every 30s
// 2. It fetches live matches and compares them to the previous snapshot
// 3. If any score or status changed, it calls messagingTemplate.convertAndSend(...)
// 4. Spring's STOMP broker broadcasts the message to all subscribers
// 5. This hook receives it and calls the onUpdate callback
// 6. LivePage calls queryClient.setQueryData(...) to update React Query's cache
// 7. React re-renders the score cards instantly — no polling needed
export function useLiveScores(onUpdate: (matches: MatchDto[]) => void) {
  useEffect(() => {
    // Derive the WebSocket URL from the current browser location so this works
    // in both development (localhost:3000 → proxied by Vite to localhost:8080)
    // and production (same host, just different protocol: https → wss).
    // We use a plain ws:// URL — no SockJS needed, all modern browsers support
    // native WebSocket and @stomp/stompjs works with it out of the box.
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const brokerURL = `${wsProtocol}//${window.location.host}/ws`

    // Create the STOMP client with a native WebSocket URL.
    // brokerURL tells @stomp/stompjs to connect directly via WebSocket,
    // without any SockJS handshake overhead.
    const client = new Client({
      brokerURL,

      // Reconnect automatically if the connection drops (network blip, server restart, etc.)
      // reconnectDelay is in milliseconds — 5 s is a sensible default
      reconnectDelay: 5_000,

      onConnect: () => {
        // Subscribe to the live scores topic.
        // The server sends the full list of live matches as a JSON array every time
        // a score or status changes, so we replace the whole list rather than merging.
        client.subscribe('/topic/matches/live', (message) => {
          try {
            // message.body is a JSON string — parse it back into an array of MatchDtos
            const matches: MatchDto[] = JSON.parse(message.body)
            onUpdate(matches)
          } catch {
            // Malformed message — log and wait for the next tick
            console.warn('[useLiveScores] Could not parse WebSocket message:', message.body)
          }
        })
      },

      onStompError: (frame) => {
        // Log STOMP-level errors so they're visible in the browser console during debugging
        console.error('[useLiveScores] STOMP error:', frame.headers['message'])
      },
    })

    // Open the connection — the onConnect callback fires once the handshake completes
    client.activate()

    // Cleanup: deactivate the STOMP client when the component using this hook unmounts.
    // This closes the WebSocket cleanly and prevents memory leaks.
    return () => {
      client.deactivate()
    }
  }, []) // Empty deps — connect once on mount, disconnect on unmount
         // onUpdate is intentionally excluded: it's a new function reference on every render
         // and including it would reconnect the WebSocket on every render cycle
}
