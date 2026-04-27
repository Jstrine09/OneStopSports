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
  // Fallback: coloured initials badge
  return (
    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-600 text-xs font-bold">
      {name.slice(0, 3).toUpperCase()}
    </div>
  )
}

export default function MatchCard({ match }: Props) {
  const state = getMatchState(match.status)

  const scoreline =
    state === 'scheduled'
      ? match.startTime ? formatKickoff(match.startTime, match.timezone) : '--:--'
      : `${match.homeScore ?? 0} - ${match.awayScore ?? 0}`

  const badge = (() => {
    if (state === 'live')     return <span className="animate-pulse rounded bg-green-500 px-1.5 py-0.5 text-[10px] font-bold uppercase text-white">Live</span>
    if (state === 'halftime') return <span className="rounded bg-amber-500 px-1.5 py-0.5 text-[10px] font-bold uppercase text-white">HT</span>
    if (state === 'finished') return <span className="text-[10px] font-semibold uppercase text-slate-400">FT</span>
    return null
  })()

  return (
    <Link
      to={`/match/${match.id}`}
      state={match}
      className="flex items-center gap-3 rounded-xl bg-slate-800 px-4 py-3 transition hover:bg-slate-700 active:scale-[0.98]"
    >
      {/* Home team */}
      <div className="flex flex-1 items-center gap-2 overflow-hidden">
        <TeamCrest url={match.homeTeam.crestUrl} name={match.homeTeam.shortName} />
        <span className="truncate text-sm font-medium">{match.homeTeam.shortName}</span>
      </div>

      {/* Score / kickoff time */}
      <div className="flex w-20 flex-col items-center gap-0.5 text-center">
        <span className={`text-base font-bold tabular-nums ${state === 'live' ? 'text-green-400' : 'text-white'}`}>
          {scoreline}
        </span>
        {badge}
      </div>

      {/* Away team */}
      <div className="flex flex-1 items-center justify-end gap-2 overflow-hidden">
        <span className="truncate text-right text-sm font-medium">{match.awayTeam.shortName}</span>
        <TeamCrest url={match.awayTeam.crestUrl} name={match.awayTeam.shortName} />
      </div>
    </Link>
  )
}
