import { useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft } from 'lucide-react'
import { getMatchState, type MatchDto, type MatchEventDto } from '../types'
import { fetchMatchEvents } from '../api/matches'
import LoadingSpinner from '../components/LoadingSpinner'

// Date-time label with optional timezone suffix — e.g. "Sun, Apr 27, 7:30 PM ET"
function formatKickoff(utc: string, timezone?: string | null): string {
  const formatted = new Date(utc).toLocaleString([], {
    weekday: 'short', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
  return timezone ? `${formatted} ${timezone}` : formatted
}

function TeamCrest({ url, name }: { url: string | null; name: string }) {
  if (url) return <img src={url} alt={name} className="h-20 w-20 object-contain sm:h-24 sm:w-24" />
  return (
    <div className="flex h-20 w-20 items-center justify-center rounded-full bg-slate-200 text-lg font-bold dark:bg-zinc-800 dark:text-zinc-300 sm:h-24 sm:w-24">
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
    if ((window.history.state?.idx ?? 0) > 0) navigate(-1)
    else navigate('/', { replace: true })
  }

  const { data: events = [], isLoading: loadingEvents } = useQuery({
    queryKey: ['match-events', match?.id],
    queryFn: () => fetchMatchEvents(match!.id),
    enabled: !!match,
    staleTime: 60_000,
  })

  const sortedEvents = [...events].reverse() // latest first

  if (!match) {
    return (
      <div className="py-16 text-center text-slate-500 dark:text-zinc-500">
        <p>Match not found.</p>
        <button onClick={handleBack} className="mt-4 text-amber-400 underline">Go back</button>
      </div>
    )
  }

  const state = getMatchState(match.status)
  const hasScore = state !== 'scheduled' && state !== 'other'

  return (
    <div className="space-y-5">
      {/* Back button */}
      <button
        onClick={handleBack}
        className="flex min-h-[44px] items-center gap-1 py-2 text-sm text-slate-500 transition-colors hover:text-slate-900 dark:text-zinc-500 dark:hover:text-zinc-100"
      >
        <ChevronLeft size={16} /> Back
      </button>

      {/* Scoreline hero — the score IS the page.
          Live matches get a green glow behind the scoreline so the live state
          isn't just a badge but a property of the whole hero section. Crests
          are bigger, names sit under them, the score dominates. */}
      <section
        className={`relative overflow-hidden rounded-3xl border border-slate-200 bg-white px-4 py-8 dark:border-zinc-900 dark:bg-zinc-900/60 sm:py-10
          ${state === 'live' ? 'dark:bg-gradient-to-b dark:from-green-500/[0.08] dark:to-zinc-900/40' : ''}`}
      >
        {state === 'live' && (
          <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-green-400/60 to-transparent" />
        )}

        <div className="flex items-start justify-between gap-2 sm:gap-6">
          {/* Home team */}
          <div className="flex flex-1 flex-col items-center gap-3">
            <TeamCrest url={match.homeTeam.crestUrl} name={match.homeTeam.shortName} />
            <span className="text-center text-sm font-bold leading-tight sm:text-base">
              {match.homeTeam.name}
            </span>
          </div>

          {/* Score — the dominant element */}
          <div className="flex flex-col items-center gap-2 px-1 pt-2">
            {hasScore ? (
              <span className={`text-5xl font-black tabular-nums leading-none tracking-tight sm:text-6xl
                ${state === 'live' ? 'text-green-400' : 'text-slate-900 dark:text-zinc-100'}`}
              >
                {match.homeScore ?? 0}<span className="px-2 font-light opacity-40">–</span>{match.awayScore ?? 0}
              </span>
            ) : (
              <span className="text-center text-2xl font-extrabold leading-tight text-slate-900 dark:text-zinc-100 sm:text-3xl">
                {match.startTime ? formatKickoff(match.startTime, match.timezone) : 'TBD'}
              </span>
            )}

            <div className="mt-1">
              {state === 'live'      && <span className="inline-flex items-center gap-1.5 rounded-full bg-green-500 px-3 py-1 text-[11px] font-extrabold uppercase tracking-wide text-white">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-white" /> Live
              </span>}
              {state === 'halftime'  && <span className="rounded-full bg-amber-500 px-3 py-1 text-[11px] font-extrabold uppercase tracking-wide text-white">Half Time</span>}
              {state === 'finished'  && <span className="text-[11px] font-bold uppercase tracking-[0.12em] text-slate-500 dark:text-zinc-500">Full Time</span>}
              {state === 'scheduled' && match.startTime && (
                <span className="text-[11px] font-medium text-slate-500 dark:text-zinc-500">{formatKickoff(match.startTime, match.timezone)}</span>
              )}
            </div>
          </div>

          {/* Away team */}
          <div className="flex flex-1 flex-col items-center gap-3">
            <TeamCrest url={match.awayTeam.crestUrl} name={match.awayTeam.shortName} />
            <span className="text-center text-sm font-bold leading-tight sm:text-base">
              {match.awayTeam.name}
            </span>
          </div>
        </div>
      </section>

      {/* Match Events / Timeline */}
      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/60">
        <header className="border-b border-slate-100 px-4 py-3 dark:border-zinc-900">
          <h2 className="text-[11px] font-bold uppercase tracking-[0.12em] text-slate-500 dark:text-zinc-400">Match Events</h2>
        </header>

        {loadingEvents ? (
          <div className="py-6"><LoadingSpinner /></div>
        ) : sortedEvents.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-8 text-slate-400 dark:text-zinc-600">
            <span className="text-2xl">📋</span>
            <p className="text-xs">
              {state === 'scheduled' ? 'Match not started yet' : 'No events available'}
            </p>
          </div>
        ) : (
          <div>
            {sortedEvents.map((event, i) => (
              <div
                key={i}
                className="flex items-center gap-3 border-t border-slate-100 px-4 py-2.5 first:border-0 transition-colors hover:bg-slate-50 dark:border-zinc-900 dark:hover:bg-zinc-800/40"
              >
                {/* Minute */}
                <span className="w-10 shrink-0 text-right text-xs font-bold tabular-nums text-slate-500 dark:text-zinc-500">
                  {event.minute != null
                    ? event.injuryMinute != null
                      ? `${event.minute}+${event.injuryMinute}'`
                      : `${event.minute}'`
                    : '—'}
                </span>

                <span className="text-base leading-none">{eventIcon(event.type)}</span>

                <div className="flex-1 overflow-hidden">
                  <p className="truncate text-sm font-medium">{eventLabel(event)}</p>
                  {event.type === 'GOAL' && event.assistName && (
                    <p className="truncate text-xs text-slate-500 dark:text-zinc-500">Assist: {event.assistName}</p>
                  )}
                </div>

                {event.teamName && (
                  <span className="shrink-0 max-w-[100px] truncate text-right text-xs text-slate-400 dark:text-zinc-600">
                    {event.teamName}
                  </span>
                )}
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Stats / Lineups placeholders — kept low-key so they don't compete
          with real content. Empty states teach (free-tier limitation), they
          don't shout. */}
      <div className="grid gap-3 sm:grid-cols-2">
        {[
          { title: 'Match Stats', emoji: '📊' },
          { title: 'Lineups',     emoji: '📋' },
        ].map(({ title, emoji }) => (
          <section
            key={title}
            className="overflow-hidden rounded-2xl border border-slate-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/40"
          >
            <header className="border-b border-slate-100 px-4 py-3 dark:border-zinc-900">
              <h2 className="text-[11px] font-bold uppercase tracking-[0.12em] text-slate-500 dark:text-zinc-400">{title}</h2>
            </header>
            <div className="flex flex-col items-center gap-1 py-6 text-slate-400 dark:text-zinc-600">
              <span className="text-2xl">{emoji}</span>
              <p className="text-xs">Coming soon</p>
            </div>
          </section>
        ))}
      </div>
    </div>
  )
}
