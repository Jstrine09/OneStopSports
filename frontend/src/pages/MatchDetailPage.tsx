import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft } from 'lucide-react'
import {
  getMatchState,
  type BoxScoreDto,
  type MatchDto,
  type MatchEventDto,
  type PlayerStatGroupDto,
  type TeamBoxScoreDto,
} from '../types'
import { fetchBoxScore, fetchMatchEvents } from '../api/matches'
import LoadingSpinner from '../components/LoadingSpinner'
import SportFieldBackdrop, { fieldVariantForSport } from '../components/SportFieldBackdrop'
import { getLeagueTheme } from '../lib/leagueTheme'

// ── Helpers ────────────────────────────────────────────────────────────────────

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
    <div className="flex h-20 w-20 items-center justify-center rounded-full bg-stone-200 text-lg font-bold dark:bg-zinc-800 dark:text-zinc-300 sm:h-24 sm:w-24">
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

// ── Box Score sub-components ───────────────────────────────────────────────────

// Team stats comparison table — classic ESPN / Fotmob style:
//   home value | STAT LABEL | away value
// Each row shows one stat for both teams side-by-side.
function TeamStatsTable({ home, away }: { home: TeamBoxScoreDto; away: TeamBoxScoreDto }) {
  // Both teams should have the same set of stats in the same order.
  // We zip them by index since the backend guarantees parallel arrays.
  const rows = home.stats.map((stat, i) => ({
    label: stat.label,
    homeValue: stat.value,
    awayValue: away.stats[i]?.value ?? '—',
  }))

  if (rows.length === 0) return null

  return (
    <div className="divide-y divide-stone-100 dark:divide-zinc-800">
      {rows.map(({ label, homeValue, awayValue }) => (
        <div key={label} className="grid grid-cols-3 items-center py-2 px-4 text-sm">
          {/* Home value — right-aligned so it reads toward the label */}
          <span className="text-right font-semibold tabular-nums text-stone-900 dark:text-zinc-100">
            {homeValue}
          </span>
          {/* Stat label — centred and subdued */}
          <span className="text-center text-[11px] font-medium uppercase tracking-wide text-stone-400 dark:text-zinc-500">
            {label}
          </span>
          {/* Away value — left-aligned so it reads toward the label */}
          <span className="text-left font-semibold tabular-nums text-stone-900 dark:text-zinc-100">
            {awayValue}
          </span>
        </div>
      ))}
    </div>
  )
}

// Player stats table for one team — scrollable columns, sticky player name column.
// 'columns' comes from the backend (ESPN) so they match whatever sport is being shown.
function PlayerStatsTable({ group }: { group: PlayerStatGroupDto }) {
  if (group.players.length === 0) {
    return (
      <p className="py-4 text-center text-xs text-stone-400 dark:text-zinc-600">
        No player data available
      </p>
    )
  }

  return (
    // Horizontal scroll wrapper — on mobile, the player table can have many columns.
    // overflow-x-auto lets it scroll without blowing the page layout.
    <div className="overflow-x-auto">
      <table className="w-full min-w-max text-sm">
        <thead>
          <tr className="border-b border-stone-100 dark:border-zinc-800">
            {/* Player name — wider, left-aligned */}
            <th className="py-2 pl-4 pr-3 text-left text-[11px] font-bold uppercase tracking-wide text-stone-400 dark:text-zinc-500">
              Player
            </th>
            {/* Stat columns — right-aligned, narrow */}
            {group.columns.map((col) => (
              <th
                key={col}
                className="py-2 px-2 text-right text-[11px] font-bold uppercase tracking-wide text-stone-400 dark:text-zinc-500"
              >
                {col}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-stone-100 dark:divide-zinc-800/60">
          {group.players.map((player, i) => (
            <tr
              key={i}
              className="transition-colors hover:bg-stone-50 dark:hover:bg-zinc-800/40"
            >
              {/* Player name — bold for starters, normal weight for bench */}
              <td className="py-2 pl-4 pr-3 text-left">
                <span className={player.starter ? 'font-semibold text-stone-900 dark:text-zinc-100' : 'text-stone-600 dark:text-zinc-400'}>
                  {player.playerName}
                </span>
                {player.starter && (
                  <span className="ml-1.5 text-[10px] font-bold uppercase tracking-wide text-amber-500">
                    S
                  </span>
                )}
              </td>
              {/* Stat values — tabular numbers so they line up in columns */}
              {player.stats.map((val, j) => (
                <td key={j} className="py-2 px-2 text-right tabular-nums text-stone-700 dark:text-zinc-300">
                  {val}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// Full box score panel — team stats comparison at the top, then tabbed player stats.
// Tabs let you switch between home and away player tables without doubling the height.
function BoxScorePanel({ boxScore, match }: { boxScore: BoxScoreDto; match: MatchDto }) {
  // activeTeam 0 = home, 1 = away — mirrors how the backend orders both arrays
  const [activeTeam, setActiveTeam] = useState<0 | 1>(0)

  const home = boxScore.teams.find((t) => t.isHome)
  const away = boxScore.teams.find((t) => !t.isHome)
  const homePlayerGroup = boxScore.playerStats.find((g) => g.isHome)
  const awayPlayerGroup = boxScore.playerStats.find((g) => !g.isHome)
  const activePlayerGroup = activeTeam === 0 ? homePlayerGroup : awayPlayerGroup

  // Section title differs by sport so the header is informative, not generic
  const playerSectionTitle =
    boxScore.sport === 'football' ? 'Match Events by Player' : 'Player Stats'

  return (
    <div className="space-y-0">
      {/* ── Team Stats ──────────────────────────────────────────────────────── */}
      <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/60">
        <header className="border-b border-stone-100 px-4 py-3 dark:border-zinc-900">
          {/* Team name headers flank the title so the stat values have context */}
          <div className="grid grid-cols-3 items-center">
            <span className="text-right text-xs font-bold text-stone-700 dark:text-zinc-300">
              {home?.abbreviation ?? match.homeTeam.shortName}
            </span>
            <h2 className="text-center text-[11px] font-bold uppercase tracking-[0.12em] text-stone-500 dark:text-zinc-400">
              Team Stats
            </h2>
            <span className="text-left text-xs font-bold text-stone-700 dark:text-zinc-300">
              {away?.abbreviation ?? match.awayTeam.shortName}
            </span>
          </div>
        </header>

        {home && away ? (
          <TeamStatsTable home={home} away={away} />
        ) : (
          <p className="py-6 text-center text-xs text-stone-400 dark:text-zinc-600">
            Team stats not available
          </p>
        )}
      </section>

      {/* ── Player Stats ────────────────────────────────────────────────────── */}
      {boxScore.playerStats.length > 0 && (
        <section className="mt-3 overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/60">
          <header className="border-b border-stone-100 px-4 py-3 dark:border-zinc-900">
            <h2 className="mb-2 text-[11px] font-bold uppercase tracking-[0.12em] text-stone-500 dark:text-zinc-400">
              {playerSectionTitle}
            </h2>
            {/* Home / Away tab switcher */}
            <div className="flex gap-1">
              <button
                onClick={() => setActiveTeam(0)}
                className={`rounded-lg px-3 py-1 text-xs font-semibold transition-colors
                  ${activeTeam === 0
                    ? 'bg-stone-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                    : 'text-stone-500 hover:text-stone-900 dark:text-zinc-400 dark:hover:text-zinc-100'}`}
              >
                {homePlayerGroup?.teamName ?? match.homeTeam.shortName}
              </button>
              <button
                onClick={() => setActiveTeam(1)}
                className={`rounded-lg px-3 py-1 text-xs font-semibold transition-colors
                  ${activeTeam === 1
                    ? 'bg-stone-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                    : 'text-stone-500 hover:text-stone-900 dark:text-zinc-400 dark:hover:text-zinc-100'}`}
              >
                {awayPlayerGroup?.teamName ?? match.awayTeam.shortName}
              </button>
            </div>
          </header>

          {activePlayerGroup ? (
            <PlayerStatsTable group={activePlayerGroup} />
          ) : (
            <p className="py-6 text-center text-xs text-stone-400 dark:text-zinc-600">
              No player data for this team
            </p>
          )}
        </section>
      )}
    </div>
  )
}

// ── Main page component ────────────────────────────────────────────────────────

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

  // Box score is only meaningful for finished or live games.
  // For scheduled games we skip the fetch entirely — the API would return 204.
  // leagueId comes from MatchDto — always present for NBA/NFL; present for football too.
  const state = match ? getMatchState(match.status) : 'other'
  const boxScoreEnabled = !!match && !!match.leagueId && (state === 'finished' || state === 'live')

  const { data: boxScore, isLoading: loadingBoxScore } = useQuery({
    queryKey: ['box-score', match?.id, match?.leagueId],
    queryFn: () => fetchBoxScore(match!.id, match!.leagueId!),
    enabled: boxScoreEnabled,
    // Box scores for finished games never change — cache them for the whole session.
    // For live games the scores are updating so use a shorter TTL.
    staleTime: state === 'live' ? 30_000 : Infinity,
  })

  const sortedEvents = [...events].reverse() // latest event first

  if (!match) {
    return (
      <div className="py-16 text-center text-stone-500 dark:text-zinc-500">
        <p>Match not found.</p>
        <button onClick={handleBack} className="mt-4 text-amber-600 underline dark:text-amber-400">Go back</button>
      </div>
    )
  }

  const hasScore = state !== 'scheduled' && state !== 'other'

  // Sport-level theme — without league metadata on the match we infer sport
  // from the timezone field (ET = American sports, null = football).
  const sportSlug = match.timezone === 'ET' ? 'basketball' : 'football'
  const theme = getLeagueTheme(null, sportSlug)

  return (
    <div className="space-y-5">
      {/* Back button */}
      <button
        onClick={handleBack}
        className="flex min-h-[44px] items-center gap-1 py-2 text-sm text-stone-500 transition-colors hover:text-stone-900 dark:text-zinc-500 dark:hover:text-zinc-100"
      >
        <ChevronLeft size={16} /> Back
      </button>

      {/* Scoreline hero — the score IS the page.
          Live matches get a green glow behind the scoreline so the live state
          isn't just a badge but a property of the whole hero section. The
          stadium backdrop sits underneath, themed to the sport. */}
      <section
        className={`relative overflow-hidden rounded-3xl border border-stone-200 bg-white px-4 py-8 dark:border-zinc-900 dark:bg-zinc-900/60 sm:py-10
          ${state === 'live' ? 'bg-gradient-to-b from-green-50 to-white dark:bg-gradient-to-b dark:from-green-500/[0.08] dark:to-zinc-900/40' : ''}`}
      >
        {/* Sport-themed field — strong in non-live matches, subtle during live so
            the green live treatment can dominate the section. Hidden on phones. */}
        <SportFieldBackdrop
          colorClass={theme.text}
          variant={fieldVariantForSport(sportSlug)}
          intensity={state === 'live' ? 'subtle' : 'strong'}
          className="hidden md:block"
        />

        {/* Top accent — sport color (or green when live) */}
        <div className={`absolute inset-x-0 top-0 h-0.5 opacity-80 ${state === 'live' ? 'bg-green-500' : theme.bg}`} />

        {state === 'live' && (
          <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-green-400/60 to-transparent" />
        )}

        <div className="relative flex items-start justify-between gap-2 sm:gap-6">
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
                ${state === 'live' ? 'text-green-600 dark:text-green-400' : 'text-stone-900 dark:text-zinc-100'}`}
              >
                {match.homeScore ?? 0}<span className="px-2 font-light opacity-40">–</span>{match.awayScore ?? 0}
              </span>
            ) : (
              <span className="text-center text-2xl font-extrabold leading-tight text-stone-900 dark:text-zinc-100 sm:text-3xl">
                {match.startTime ? formatKickoff(match.startTime, match.timezone) : 'TBD'}
              </span>
            )}

            <div className="mt-1">
              {state === 'live'      && <span className="inline-flex items-center gap-1.5 rounded-full bg-green-500 px-3 py-1 text-[11px] font-extrabold uppercase tracking-wide text-white">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-white" /> Live
              </span>}
              {state === 'halftime'  && <span className="rounded-full bg-amber-500 px-3 py-1 text-[11px] font-extrabold uppercase tracking-wide text-white">Half Time</span>}
              {state === 'finished'  && <span className="text-[11px] font-bold uppercase tracking-[0.12em] text-stone-500 dark:text-zinc-500">Full Time</span>}
              {state === 'scheduled' && match.startTime && (
                <span className="text-[11px] font-medium text-stone-500 dark:text-zinc-500">{formatKickoff(match.startTime, match.timezone)}</span>
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

      {/* Match Events / Timeline — football only (NBA/NFL don't expose per-event timelines) */}
      <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/60">
        <header className="border-b border-stone-100 px-4 py-3 dark:border-zinc-900">
          <h2 className="text-[11px] font-bold uppercase tracking-[0.12em] text-stone-500 dark:text-zinc-400">Match Events</h2>
        </header>

        {loadingEvents ? (
          <div className="py-6"><LoadingSpinner /></div>
        ) : sortedEvents.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-8 text-stone-400 dark:text-zinc-600">
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
                className="flex items-center gap-3 border-t border-stone-100 px-4 py-2.5 first:border-0 transition-colors hover:bg-stone-50 dark:border-zinc-900 dark:hover:bg-zinc-800/40"
              >
                {/* Minute */}
                <span className="w-10 shrink-0 text-right text-xs font-bold tabular-nums text-stone-500 dark:text-zinc-500">
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
                    <p className="truncate text-xs text-stone-500 dark:text-zinc-500">Assist: {event.assistName}</p>
                  )}
                </div>

                {event.teamName && (
                  <span className="shrink-0 max-w-[100px] truncate text-right text-xs text-stone-400 dark:text-zinc-600">
                    {event.teamName}
                  </span>
                )}
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Box Score — shown for finished and live games.
          Replaces the old "coming soon" Stats / Lineups placeholder cards.
          For scheduled games we show a minimal "not available yet" placeholder. */}
      {state === 'scheduled' || state === 'other' ? (
        // Pre-match: no stats to show — keep it low-key
        <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/40">
          <header className="border-b border-stone-100 px-4 py-3 dark:border-zinc-900">
            <h2 className="text-[11px] font-bold uppercase tracking-[0.12em] text-stone-500 dark:text-zinc-400">Box Score</h2>
          </header>
          <div className="flex flex-col items-center gap-1 py-6 text-stone-400 dark:text-zinc-600">
            <span className="text-2xl">📊</span>
            <p className="text-xs">Available after kick-off</p>
          </div>
        </section>
      ) : loadingBoxScore ? (
        <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/40">
          <div className="py-8"><LoadingSpinner /></div>
        </section>
      ) : boxScore ? (
        // Real data — render the full box score panel
        <BoxScorePanel boxScore={boxScore} match={match} />
      ) : (
        // API returned 204 — game played but no data (ESPN sometimes lags)
        <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/40">
          <header className="border-b border-stone-100 px-4 py-3 dark:border-zinc-900">
            <h2 className="text-[11px] font-bold uppercase tracking-[0.12em] text-stone-500 dark:text-zinc-400">Box Score</h2>
          </header>
          <div className="flex flex-col items-center gap-1 py-6 text-stone-400 dark:text-zinc-600">
            <span className="text-2xl">📊</span>
            <p className="text-xs">Box score not available for this match</p>
          </div>
        </section>
      )}
    </div>
  )
}
