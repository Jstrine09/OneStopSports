import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchLiveMatches } from '../api/matches'
import { useLiveScores } from '../hooks/useLiveScores'
import MatchCard from '../components/MatchCard'
import LoadingSpinner from '../components/LoadingSpinner'
import { Radio } from 'lucide-react'
import type { MatchDto } from '../types'

export default function LivePage() {
  const queryClient = useQueryClient()

  // Initial load + fallback polling via REST (every 60 s).
  // The WebSocket push below is the primary update mechanism — polling is a safety net
  // in case the WebSocket connection drops and auto-reconnect hasn't fired yet.
  const { data: matches = [], isLoading } = useQuery({
    queryKey: ['matches', 'live'],
    queryFn: fetchLiveMatches,
    refetchInterval: 60_000, // Reduced from 30 s — WebSocket handles real-time updates
    staleTime: 30_000,
  })

  // Subscribe to WebSocket pushes from the backend.
  // When a score changes, the server sends the full updated match list.
  // We write it directly into React Query's cache so MatchCards re-render instantly
  // without waiting for the next polling cycle.
  useLiveScores((updatedMatches: MatchDto[]) => {
    queryClient.setQueryData(['matches', 'live'], updatedMatches)
  })

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Radio size={20} className="text-green-400" />
        <h1 className="text-xl font-bold">Live Now</h1>
        {matches.length > 0 && (
          <span className="animate-pulse rounded-full bg-green-500 px-2 py-0.5 text-xs font-bold text-white">
            {matches.length}
          </span>
        )}
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : matches.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-16 text-slate-400">
          <Radio size={40} className="opacity-30" />
          <p className="text-sm">No live matches right now</p>
        </div>
      ) : (
        <div className="space-y-1.5">
          {matches.map((match) => (
            <MatchCard key={match.id} match={match} />
          ))}
        </div>
      )}
    </div>
  )
}
