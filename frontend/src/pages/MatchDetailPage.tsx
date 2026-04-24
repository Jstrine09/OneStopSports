import { useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft } from 'lucide-react'
import { getMatchState, type MatchDto, type MatchEventDto } from '../types'
import { fetchMatchEvents } from '../api/matches'
import LoadingSpinner from '../components/LoadingSpinner'

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

function eventIcon(type: string): string {
  switch (type) {
    case 'GOAL':            return '⚽'
    case 'OWN_GOAL':        return '⚽'
    case 'PENALTY':         return '⚽'
    case 'YELLOW_CARD':     return '🟡'
    case 'RED_CARD':        return '🟥'
    case 'YELLOW_RED_CARD': return '🟥'
    case 'SUBSTITUTION':    return '🔄'
    default:                return '•'
  }
}

function eventLabel(event: MatchEventDto): string {
  switch (event.type) {
    case 'OWN_GOAL':   return `${event.playerName ?? '?'} (OG)`
    case 'PENALTY':    return `${event.playerName ?? '?'} (pen)`
    case 'SUBSTITUTION':
      return event.assistName
        ? `↑ ${event.assistName}  ↓ ${event.playerName ?? '?'}`
        : event.playerName ?? '?'
    default:
      return event.playerName ?? '?'
  }
}

export default function MatchDetailPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const match = location.state as MatchDto | null

  const handleBack = () => {
    if ((window.history.state?.idx ?? 0) > 0) {
      navigate(-1)
    } else {
      navigate('/', { replace: true })
    }
  }

  const { data: events = [], isLoading: loadingEvents } = useQuery({
    queryKey: ['match-events', match?.id],
    queryFn: () => fetchMatchEvents(match!.id),
    enabled: !!match,
    staleTime: 60_000,
  })

  // Latest events first
  const sortedEvents = [...events].reverse()

  if (!match) {
    return (
      <div className="py-16 text-center text-slate-400">
        <p>Match not found.</p>
        <button onClick={handleBack} className="mt-4 text-blue-400 underline">Go back</button>
      </div>
    )
  }

  const state = getMatchState(match.status)
  const hasScore = state !== 'scheduled' && state !== 'other'

  return (
    <div className="space-y-4">
      {/* Back button */}
      <button
        onClick={handleBack}
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

            {state === 'live'      && <span className="animate-pulse rounded bg-green-500 px-2 py-0.5 text-xs font-bold text-white">LIVE</span>}
            {state === 'halftime'  && <span className="rounded bg-amber-500 px-2 py-0.5 text-xs font-bold text-white">HALF TIME</span>}
            {state === 'finished'  && <span className="text-xs font-semibold text-slate-400">FULL TIME</span>}
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

      {/* Match Events / Timeline */}
      <div className="rounded-2xl bg-slate-800 px-4 py-5">
        <h2 className="mb-3 text-sm font-semibold text-slate-300">Match Events</h2>

        {loadingEvents ? (
          <LoadingSpinner />
        ) : sortedEvents.length === 0 ? (
          <div className="flex flex-col items-center gap-1 py-4 text-slate-500">
            <span className="text-2xl">📋</span>
            <p className="text-xs">
              {state === 'scheduled' ? 'Match not started yet' : 'No events available'}
            </p>
          </div>
        ) : (
          <div className="space-y-0.5">
            {sortedEvents.map((event, i) => (
              <div
                key={i}
                className="flex items-center gap-3 rounded-lg px-2 py-2 hover:bg-slate-700/50"
              >
                {/* Minute */}
                <span className="w-10 shrink-0 text-right text-xs font-bold tabular-nums text-slate-400">
                  {event.minute != null
                    ? event.injuryMinute != null
                      ? `${event.minute}+${event.injuryMinute}'`
                      : `${event.minute}'`
                    : '—'}
                </span>

                {/* Icon */}
                <span className="text-base leading-none">{eventIcon(event.type)}</span>

                {/* Description */}
                <div className="flex-1 overflow-hidden">
                  <p className="truncate text-sm font-medium">{eventLabel(event)}</p>
                  {event.type === 'GOAL' && event.assistName && (
                    <p className="truncate text-xs text-slate-400">Assist: {event.assistName}</p>
                  )}
                </div>

                {/* Team name */}
                {event.teamName && (
                  <span className="shrink-0 max-w-[100px] truncate text-right text-xs text-slate-500">
                    {event.teamName}
                  </span>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Stats placeholder */}
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
