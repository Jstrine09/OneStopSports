import { Link } from 'react-router-dom'
import { getMatchState, type MatchDto } from '../types'

interface Props {
  match: MatchDto
}

// Formats a time string to HH:MM, with an optional timezone label appended.
// For NBA/NFL the backend sends ET wall-clock time + timezone="ET", so we just display
// the digits as-is and append the label — e.g. "7:30 PM ET".
function formatKickoff(utc: string, timezone?: string | null): string {
  const time = new Date(utc).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  return timezone ? `${time} ${timezone}` : time
}

function TeamCrest({ url, name }: { url: string | null; name: string }) {
  if (url) {
    return <img src={url} alt={name} className="h-8 w-8 object-contain" />
  }
  // Fallback: initials badge
  return (
    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-zinc-700 text-xs font-bold text-zinc-300">
      {name.slice(0, 3).toUpperCase()}
    </div>
  )
}

export default function MatchCard({ match }: Props) {
  const state = getMatchState(match.status)

  const scoreline =
    state === 'scheduled'
      ? match.startTime ? formatKickoff(match.startTime, match.timezone) : '--:--'
      : `${match.homeScore ?? 0} – ${match.awayScore ?? 0}`

  // Live badge pulses green. Half-time is amber. FT is muted — it happened, it's done.
  const badge = (() => {
    if (state === 'live')     return <span className="animate-pulse rounded bg-green-500 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-white">Live</span>
    if (state === 'halftime') return <span className="rounded bg-amber-500 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-white">HT</span>
    if (state === 'finished') return <span className="text-[10px] font-semibold uppercase tracking-wide text-zinc-500">FT</span>
    return null
  })()

  return (
    <Link
      to={`/match/${match.id}`}
      state={match}
      // Flat row — the parent container provides visual grouping.
      // Live matches get a faint green tint so they're instantly identifiable
      // at a glance, without the badge being the only signal.
      className={`flex items-center gap-3 px-4 py-3 transition-colors active:scale-[0.98]
        hover:bg-slate-100 dark:hover:bg-zinc-800/60
        border-b border-slate-100 dark:border-zinc-800/70 last:border-0
        ${state === 'live' ? 'bg-green-500/[0.04] dark:bg-green-500/[0.06]' : ''}`}
    >
      {/* Home team */}
      <div className="flex flex-1 items-center gap-2.5 overflow-hidden">
        <TeamCrest url={match.homeTeam.crestUrl} name={match.homeTeam.shortName} />
        <span className="truncate text-sm font-semibold">{match.homeTeam.shortName}</span>
      </div>

      {/* Score / kickoff time — this is the information, everything else is context */}
      <div className="flex w-24 flex-col items-center gap-0.5 text-center">
        <span className={`text-lg font-extrabold tabular-nums tracking-tight
          ${state === 'live'
            ? 'text-green-400'
            : state === 'scheduled'
              ? 'text-zinc-500 dark:text-zinc-400'
              : 'text-slate-900 dark:text-zinc-100'}`}
        >
          {scoreline}
        </span>
        {badge}
      </div>

      {/* Away team */}
      <div className="flex flex-1 items-center justify-end gap-2.5 overflow-hidden">
        <span className="truncate text-right text-sm font-semibold">{match.awayTeam.shortName}</span>
        <TeamCrest url={match.awayTeam.crestUrl} name={match.awayTeam.shortName} />
      </div>
    </Link>
  )
}
