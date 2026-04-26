import { useEffect } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
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
// 2. It fetches live matches, compares them to the previous snapshot
// 3. If any score or status changed, it calls messagingTemplate.convertAndSend(...)
// 4. Spring's STOMP broker broadcasts the message to all subscribers
// 5. This hook receives it and calls the onUpdate callback
// 6. LivePage calls queryClient.setQueryData(...) to update React Query's cache
// 7. React re-renders the score cards instantly — no polling needed
export function useLiveScores(onUpdate: (matches: MatchDto[]) => void) {
  useEffect(() => {
    // Create the STOMP client.
    // webSocketFactory uses SockJS so the connection works even if the browser
    // doesn't support native WebSockets (SockJS falls back to long-polling).
    // The URL /ws is proxied by Vite in dev → http://localhost:8080/ws
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'), // SockJS handles the HTTP→WS upgrade

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
            // Malformed message — ignore and wait for the next tick
            console.warn('[useLiveScores] Could not parse WebSocket message:', message.body)
          }
        })
      },

      onStompError: (frame) => {
        // Log STOMP-level errors (e.g. authentication failure on a protected broker)
        // so they're visible in the browser console during debugging
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
