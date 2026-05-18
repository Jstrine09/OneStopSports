import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchLiveMatches } from '../api/matches'
import { useLiveScores } from '../hooks/useLiveScores'
import MatchCard from '../components/MatchCard'
import LoadingSpinner from '../components/LoadingSpinner'
import StadiumBackdrop from '../components/StadiumBackdrop'
import { getLeagueTheme, type LeagueTheme } from '../lib/leagueTheme'
import { Radio } from 'lucide-react'
import type { MatchDto } from '../types'

// Group live matches by sport using the timezone field as a heuristic:
// - timezone === "ET"  →  American sports (NBA/NFL) — backend converts ET wall-clock time
// - timezone === null  →  Football (soccer) — UTC times left as-is
// This avoids an extra round-trip for league/sport metadata on the live feed.
//
// Each group carries a theme so the section header, top-accent strip, and
// stadium backdrop all use the sport's brand color.
type SportGroup = {
  label: string
  emoji: string
  theme: LeagueTheme
  matches: MatchDto[]
}

function groupBySport(matches: MatchDto[]): SportGroup[] {
  const football: MatchDto[] = []
  const american: MatchDto[] = []
  for (const m of matches) {
    if (m.timezone === 'ET') american.push(m)
    else football.push(m)
  }
  const groups: SportGroup[] = []
  // Football fallback theme is the default amber — once we group per-league
  // we can swap this to specific league themes.
  if (football.length) groups.push({
    label: 'Football',
    emoji: '⚽',
    theme: getLeagueTheme(null, 'football'),
    matches: football,
  })
  // American sports are mixed NBA + NFL on the live feed; we use NBA's theme
  // since the orange reads strongly and most live games at any given time
  // tend to be NBA. (When we have per-league grouping this gets resolved.)
  if (american.length) groups.push({
    label: 'American Sports',
    emoji: '🏀',
    theme: getLeagueTheme(null, 'basketball'),
    matches: american,
  })
  return groups
}

export default function LivePage() {
  const queryClient = useQueryClient()

  // Initial load + 60s fallback polling. WebSocket is the primary update path —
  // polling is a safety net in case the socket drops and auto-reconnect hasn't fired yet.
  const { data: matches = [], isLoading } = useQuery({
    queryKey: ['matches', 'live'],
    queryFn: fetchLiveMatches,
    refetchInterval: 60_000,
    staleTime: 30_000,
  })

  // Live WebSocket push — writes straight into React Query's cache so cards
  // re-render the moment a score changes, without waiting for the next poll.
  useLiveScores((updatedMatches: MatchDto[]) => {
    queryClient.setQueryData(['matches', 'live'], updatedMatches)
  })

  const groups = groupBySport(matches)

  return (
    <div className="space-y-6">
      {/* Page header — earned committed color treatment.
          Pulsing green ring around the icon. The whole header reads as the
          live signal: this is the one page where the brand says "something
          is happening right now". */}
      <div className="flex items-center gap-3">
        <div className="relative flex h-10 w-10 items-center justify-center rounded-full bg-green-100 dark:bg-green-500/10">
          {matches.length > 0 && (
            <span className="absolute inset-0 animate-ping rounded-full bg-green-500/40" />
          )}
          <Radio size={20} className="relative text-green-600 dark:text-green-400" strokeWidth={2.25} />
        </div>
        <div className="flex-1">
          <h1 className="text-2xl font-extrabold tracking-tight">Live Now</h1>
          <p className="text-xs text-stone-500 dark:text-zinc-500">
            {matches.length === 0 ? 'Nothing live at the moment' : `${matches.length} game${matches.length === 1 ? '' : 's'} in progress`}
          </p>
        </div>
        {matches.length > 0 && (
          <span className="rounded-full bg-green-500 px-3 py-1 text-xs font-extrabold tabular-nums text-white shadow-[0_0_20px_rgba(34,197,94,0.4)]">
            {matches.length}
          </span>
        )}
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : matches.length === 0 ? (
        // Empty state — teaches the surface instead of "nothing here".
        <div className="flex flex-col items-center gap-3 rounded-2xl border border-stone-200 bg-white py-16 dark:border-zinc-900 dark:bg-zinc-900/40">
          <Radio size={36} className="text-stone-300 dark:text-zinc-700" strokeWidth={1.5} />
          <p className="text-sm font-medium text-stone-600 dark:text-zinc-400">No live matches right now</p>
          <p className="max-w-xs text-center text-xs text-stone-400 dark:text-zinc-500">
            Check the Leagues tab to see today's fixtures, or come back when games kick off.
          </p>
        </div>
      ) : (
        // Sport groups — each is its own surface container with the sport's
        // brand color: top accent bar, themed section header, and a faint
        // stadium backdrop inside the section. This gives every match in the
        // group an immediate visual association with its sport.
        <div className="space-y-5">
          {groups.map((group) => (
            <section key={group.label}>
              <div className="mb-2 flex items-center justify-between px-1">
                <h2 className={`flex items-center gap-2 text-xs font-bold uppercase tracking-[0.12em] ${group.theme.text}`}>
                  <span className="text-base leading-none">{group.emoji}</span>
                  {group.label}
                </h2>
                <span className="text-xs tabular-nums text-stone-400 dark:text-zinc-600">
                  {group.matches.length}
                </span>
              </div>
              <div className="relative overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/60">
                {/* Top accent — thin colored bar identifies the sport */}
                <div className={`absolute inset-x-0 top-0 h-0.5 ${group.theme.bg} opacity-80 z-10`} />
                {/* Stadium backdrop sits behind the matches — kept subtle so
                    score legibility is preserved */}
                <StadiumBackdrop colorClass={group.theme.text} intensity="subtle" />
                <div className="relative">
                  {group.matches.map((match) => (
                    <MatchCard key={match.id} match={match} />
                  ))}
                </div>
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}
