import { useQuery } from '@tanstack/react-query'
import { fetchLiveMatches } from '../api/matches'
import MatchCard from '../components/MatchCard'
import LoadingSpinner from '../components/LoadingSpinner'
import { Radio } from 'lucide-react'

export default function LivePage() {
  const { data: matches = [], isLoading } = useQuery({
    queryKey: ['matches', 'live'],
    queryFn: fetchLiveMatches,
    // Refetch every 30 s to stay current
    refetchInterval: 30_000,
    staleTime: 15_000,
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
