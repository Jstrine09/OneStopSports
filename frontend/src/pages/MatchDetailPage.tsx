import { useLocation, useNavigate } from 'react-router-dom'
import { ChevronLeft } from 'lucide-react'
import { getMatchState, type MatchDto } from '../types'

function formatKickoff(utc: string): string {
  return new Date(utc).toLocaleString([], {
    weekday: 'short', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function TeamCrest({ url, name }: { url: string | null; name: string }) {
  if (url) return <img src={url} alt={name} className="h-16 w-16 object-contain sm:h-20 sm:w-20" />
  return (
    <div className="flex h-16 w-16 items-center justify-center rounded-full bg-slate-600 text-lg font-bold sm:h-20 sm:w-20">
      {name.slice(0, 3).toUpperCase()}
    </div>
  )
}

export default function MatchDetailPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const match = location.state as MatchDto | null

  if (!match) {
    return (
      <div className="py-16 text-center text-slate-400">
        <p>Match not found.</p>
        <button onClick={() => navigate(-1)} className="mt-4 text-blue-400 underline">Go back</button>
      </div>
    )
  }

  const state = getMatchState(match.status)
  const hasScore = state !== 'scheduled' && state !== 'other'

  return (
    <div className="space-y-4">
      {/* Back button */}
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-1 text-sm text-slate-400 hover:text-white"
      >
        <ChevronLeft size={16} /> Back
      </button>

      {/* Score hero */}
      <div className="rounded-2xl bg-slate-800 px-4 py-6">
        <div className="flex items-center justify-between gap-2">
          {/* Home team */}
          <div className="flex flex-1 flex-col items-center gap-2">
            <TeamCrest url={match.homeTeam.crestUrl} name={match.homeTeam.shortName} />
            <span className="text-center text-sm font-semibold leading-tight">
              {match.homeTeam.name}
            </span>
          </div>

          {/* Score / time */}
          <div className="flex flex-col items-center gap-1 px-2">
            {hasScore ? (
              <span className={`text-4xl font-extrabold tabular-nums sm:text-5xl ${state === 'live' ? 'text-green-400' : 'text-white'}`}>
                {match.homeScore ?? 0} – {match.awayScore ?? 0}
              </span>
            ) : (
              <span className="text-3xl font-bold text-white">
                {match.startTime ? formatKickoff(match.startTime) : 'TBD'}
              </span>
            )}

            {/* Status badge */}
            {state === 'live'     && <span className="animate-pulse rounded bg-green-500 px-2 py-0.5 text-xs font-bold text-white">LIVE</span>}
            {state === 'halftime' && <span className="rounded bg-amber-500 px-2 py-0.5 text-xs font-bold text-white">HALF TIME</span>}
            {state === 'finished' && <span className="text-xs font-semibold text-slate-400">FULL TIME</span>}
            {state === 'scheduled' && match.startTime && (
              <span className="text-xs text-slate-400">{formatKickoff(match.startTime)}</span>
            )}
          </div>

          {/* Away team */}
          <div className="flex flex-1 flex-col items-center gap-2">
            <TeamCrest url={match.awayTeam.crestUrl} name={match.awayTeam.shortName} />
            <span className="text-center text-sm font-semibold leading-tight">
              {match.awayTeam.name}
            </span>
          </div>
        </div>
      </div>

      {/* Stats placeholder — backend stubs not yet implemented */}
      <div className="rounded-2xl bg-slate-800 px-4 py-5">
        <h2 className="mb-3 text-sm font-semibold text-slate-300">Match Stats</h2>
        <div className="flex flex-col items-center gap-1 py-4 text-slate-500">
          <span className="text-2xl">📊</span>
          <p className="text-xs">Stats coming soon</p>
        </div>
      </div>

      {/* Lineups placeholder */}
      <div className="rounded-2xl bg-slate-800 px-4 py-5">
        <h2 className="mb-3 text-sm font-semibold text-slate-300">Lineups</h2>
        <div className="flex flex-col items-center gap-1 py-4 text-slate-500">
          <span className="text-2xl">📋</span>
          <p className="text-xs">Lineups coming soon</p>
        </div>
      </div>
    </div>
  )
}
