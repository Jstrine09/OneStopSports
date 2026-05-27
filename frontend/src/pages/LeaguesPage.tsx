import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchSports, fetchLeaguesBySport } from '../api/sports'
import { fetchStandings, fetchTeamsByLeague } from '../api/leagues'
import { fetchMatchesByLeagueAndDate, fetchBoxScore } from '../api/matches'
import StandingsTable from '../components/StandingsTable'
import DateNav from '../components/DateNav'
import LoadingSpinner from '../components/LoadingSpinner'
import StadiumBackdrop from '../components/StadiumBackdrop'
import { getLeagueTheme } from '../lib/leagueTheme'
import type { MatchDto, BoxScoreDto, PlayerStatGroupDto } from '../types'
import { getMatchState } from '../types'
import { MapPin, ChevronDown } from 'lucide-react'

type Tab = 'standings' | 'teams' | 'results'

// Returns yesterday's date as YYYY-MM-DD.
// Results tab defaults to yesterday because today's matches are usually still in progress.
function yesterdayStr() {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  return d.toISOString().slice(0, 10)
}

// Formats a time string as HH:MM with an optional timezone label — e.g. "7:30 PM ET".
// Same pattern used by MatchCard and MatchDetailPage.
function formatKickoff(utc: string, timezone?: string | null): string {
  const time = new Date(utc).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  return timezone ? `${time} ${timezone}` : time
}

// ── Inline box score for expanded result rows ───────────────────────────────
// Shows team stats comparison + starters per team.
// Keeps the UI dense — this is inline, not a full page.

interface InlineBoxScoreProps { boxScore: BoxScoreDto }

function InlineBoxScore({ boxScore }: InlineBoxScoreProps) {
  const home     = boxScore.teams.find((t) => t.isHome)
  const away     = boxScore.teams.find((t) => !t.isHome)
  const homeGrp  = boxScore.playerStats.find((g) => g.isHome)
  const awayGrp  = boxScore.playerStats.find((g) => !g.isHome)

  const starters = (grp: PlayerStatGroupDto | undefined) =>
    grp?.players.filter((p) => p.starter) ?? []

  return (
    <div className="space-y-4">
      {/* ── Team stats comparison — 3-column: home value | label | away value ── */}
      {home && away && home.stats.length > 0 && (
        <div className="space-y-1.5">
          <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-stone-400 dark:text-zinc-600">
            Team Stats
          </p>
          {home.stats.map((stat, i) => {
            const awayStat = away.stats[i]
            return (
              <div key={stat.label} className="flex items-center gap-2 text-xs">
                <span className="w-12 text-right font-bold tabular-nums">{stat.value}</span>
                <span className="flex-1 text-center text-[10px] uppercase tracking-wide text-stone-400 dark:text-zinc-600">
                  {stat.label}
                </span>
                <span className="w-12 text-left font-bold tabular-nums">{awayStat?.value ?? '—'}</span>
              </div>
            )
          })}
        </div>
      )}

      {/* ── Starters — two columns side by side ─────────────────────────────── */}
      {(homeGrp || awayGrp) && (
        <div className="grid grid-cols-2 gap-4">
          {[homeGrp, awayGrp].map((grp, idx) => (
            <div key={idx}>
              <p className="mb-1.5 text-[10px] font-bold uppercase tracking-[0.14em] text-stone-400 dark:text-zinc-600">
                {grp?.teamName ?? (idx === 0 ? 'Home' : 'Away')}
              </p>
              {starters(grp).length === 0 ? (
                <p className="text-xs text-stone-400 dark:text-zinc-600">—</p>
              ) : (
                <ul className="space-y-0.5">
                  {starters(grp).map((p) => (
                    <li key={p.playerName} className="truncate text-xs font-medium text-stone-700 dark:text-zinc-300">
                      {p.playerName}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Expandable result row ────────────────────────────────────────────────────
// Shows a match summary row. FINISHED matches can be expanded to reveal the
// inline box score (starters + team stats). Box score is fetched lazily on
// first expand — staleTime: Infinity so historical data is never re-fetched.
//
// leagueId is passed as a query param to /api/matches/{id}/boxscore so the
// backend can route to the correct sport's ESPN endpoint.

interface ResultRowProps { match: MatchDto; leagueId: number }

function ResultRow({ match, leagueId }: ResultRowProps) {
  const [open, setOpen] = useState(false)
  const state      = getMatchState(match.status)
  const isFinished = state === 'finished'

  // Lazy box score fetch — only fires when the row is expanded AND the match is finished.
  // useQuery is safe inside a component rendered in a list; each row gets its own cache entry.
  const { data: boxScore, isLoading: loadingBox } = useQuery({
    queryKey: ['boxscore', match.id, leagueId],
    queryFn: () => fetchBoxScore(match.id, leagueId),
    enabled: open && isFinished,
    staleTime: Infinity, // historical box scores never change — cache forever
  })

  // Scoreline text: finished shows result, scheduled shows kickoff time
  const scoreline = isFinished || state === 'live'
    ? `${match.homeScore ?? 0} – ${match.awayScore ?? 0}`
    : match.startTime ? formatKickoff(match.startTime, match.timezone) : '—'

  return (
    <div className="border-t border-stone-100 first:border-0 dark:border-zinc-900">
      {/* Match summary row — always visible.
          Finished matches get a pointer cursor + chevron indicating they're expandable. */}
      <button
        onClick={() => { if (isFinished) setOpen((o) => !o) }}
        className={`flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-stone-50 dark:hover:bg-zinc-800/40 ${isFinished ? 'cursor-pointer' : 'cursor-default'}`}
      >
        {/* Home team — right-aligned (score is centre, teams are left and right) */}
        <div className="flex min-w-0 flex-1 items-center justify-end gap-2">
          {match.homeTeam.crestUrl && (
            <img src={match.homeTeam.crestUrl} alt="" className="h-5 w-5 shrink-0 object-contain" />
          )}
          <span className="truncate text-sm font-semibold">
            {match.homeTeam.shortName || match.homeTeam.name}
          </span>
        </div>

        {/* Score / kickoff time — fixed width centre block */}
        <div className="w-20 shrink-0 text-center">
          <span className={`text-sm font-extrabold tabular-nums ${state === 'live' ? 'text-green-500 dark:text-green-400' : 'text-stone-900 dark:text-zinc-100'}`}>
            {scoreline}
          </span>
          {state === 'finished' && (
            <p className="text-[10px] text-stone-400 dark:text-zinc-600">FT</p>
          )}
        </div>

        {/* Away team — left-aligned */}
        <div className="flex min-w-0 flex-1 items-center gap-2">
          {match.awayTeam.crestUrl && (
            <img src={match.awayTeam.crestUrl} alt="" className="h-5 w-5 shrink-0 object-contain" />
          )}
          <span className="truncate text-sm font-semibold">
            {match.awayTeam.shortName || match.awayTeam.name}
          </span>
        </div>

        {/* Expand chevron — only shown on finished matches */}
        {isFinished && (
          <ChevronDown
            size={14}
            className={`shrink-0 text-stone-400 transition-transform duration-200 ${open ? 'rotate-180' : ''}`}
          />
        )}
      </button>

      {/* Inline box score — only rendered when expanded and match is finished */}
      {open && isFinished && (
        <div className="border-t border-stone-100 bg-stone-50 px-4 py-4 dark:border-zinc-900 dark:bg-zinc-800/30">
          {loadingBox ? (
            <LoadingSpinner />
          ) : !boxScore ? (
            <p className="text-center text-xs text-stone-400 dark:text-zinc-600">
              Box score not available for this match
            </p>
          ) : (
            <InlineBoxScore boxScore={boxScore} />
          )}
        </div>
      )}
    </div>
  )
}

// Builds the short text shown in the fallback badge when a league has no logo.
// Two letters of a long name (e.g. "PR" for "Premier League") looks like a typo —
// proper league abbreviations are initials of each word, or for single-word names
// either the whole word (when short) or its first 3 letters.
//   "Premier League"          → "PL"
//   "UEFA Champions League"   → "UCL"
//   "Serie A"                 → "SA"
//   "Ligue 1"                 → "L1"
//   "Bundesliga"              → "BUN"
//   "NBA" / "NFL"             → unchanged
function leagueAbbreviation(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return ''
  if (words.length === 1) {
    // Already-short single words ("NBA", "NFL", "MLB") pass through;
    // long ones ("Bundesliga", "Eredivisie") get truncated to 3 chars.
    return words[0].length <= 4
      ? words[0].toUpperCase()
      : words[0].slice(0, 3).toUpperCase()
  }
  // Multi-word: take the first character of each word.
  return words.map((w) => w[0]).join('').toUpperCase()
}

export default function LeaguesPage() {
  // useSearchParams keeps the active sport/league/tab in the URL so navigation
  // (back button, deep links, shared URLs) restore the correct view.
  const [searchParams, setSearchParams] = useSearchParams()

  // Results tab date state — kept in component state (not URL) since it's a
  // transient navigation choice, not something worth persisting in the URL.
  // Defaults to yesterday so there are actually finished matches to show.
  const [resultsDate, setResultsDate] = useState(yesterdayStr)

  const { data: sports = [], isLoading: loadingSports } = useQuery({
    queryKey: ['sports'],
    queryFn: fetchSports,
    staleTime: 10 * 60_000,
  })

  const sportSlug    = searchParams.get('sport')  ?? sports[0]?.slug ?? null
  const activeLeagueId = searchParams.get('league') ? Number(searchParams.get('league')) : null
  const activeTab    = (searchParams.get('tab') ?? 'standings') as Tab


  const { data: leagues = [], isLoading: loadingLeagues } = useQuery({
    queryKey: ['leagues', sportSlug],
    queryFn: () => fetchLeaguesBySport(sportSlug!),
    enabled: !!sportSlug,
    staleTime: 5 * 60_000,
  })

  const leagueId = activeLeagueId ?? leagues[0]?.id ?? null

  const { data: standings = [], isLoading: loadingStandings } = useQuery({
    queryKey: ['standings', leagueId],
    queryFn: () => fetchStandings(leagueId!),
    enabled: leagueId !== null && activeTab === 'standings',
    staleTime: 5 * 60_000,
  })

  const { data: teams = [], isLoading: loadingTeams } = useQuery({
    queryKey: ['teams', leagueId],
    queryFn: () => fetchTeamsByLeague(leagueId!),
    enabled: leagueId !== null && activeTab === 'teams',
    staleTime: 5 * 60_000,
  })

  // Results tab — fetch matches for the selected league on the selected date.
  // Same endpoint as HomePage: GET /api/matches?league={id}&date={date}.
  // staleTime: 5 minutes — finished results don't change, but we don't need Infinity
  // here since the user may be browsing different dates and we want reasonable freshness.
  const { data: results = [], isLoading: loadingResults } = useQuery({
    queryKey: ['matches', leagueId, resultsDate],
    queryFn: () => fetchMatchesByLeagueAndDate(leagueId!, resultsDate),
    enabled: leagueId !== null && activeTab === 'results',
    staleTime: 5 * 60_000,
  })

  const activeLeague = leagues.find((l) => l.id === leagueId) ?? leagues[0]

  // Resolve the brand theme for the active league. Falls back to the sport-level
  // theme (NBA/NFL) when no league-specific match exists, and to the default
  // amber when neither league nor sport matches.
  const theme = getLeagueTheme(activeLeague?.name, sportSlug)

  // URL update helpers — { replace: true } so back button doesn't cycle through pill clicks
  const setSport  = (slug: string) => setSearchParams({ sport: slug }, { replace: true })
  const setLeague = (id: number) =>
    setSearchParams({ sport: sportSlug ?? '', league: String(id) }, { replace: true })
  const setTab = (tab: Tab) => {
    const params: Record<string, string> = { tab }
    if (sportSlug) params.sport  = sportSlug
    if (leagueId)  params.league = String(leagueId)
    setSearchParams(params, { replace: true })
  }

  return (
    <div className="space-y-5">
      {/* Sport selector — underline tab style, active tab takes the active league's theme color.
          When you switch sports mid-flow the underline color refreshes too, since the theme
          resolves from sport when no league is yet chosen. */}
      {!loadingSports && sports.length > 1 && (
        <div className="flex gap-6 overflow-x-auto no-scrollbar border-b border-stone-200 dark:border-zinc-900">
          {sports.map((s) => {
            const isActive = sportSlug === s.slug
            return (
              <button
                key={s.slug}
                onClick={() => setSport(s.slug)}
                className={`relative shrink-0 pb-3 text-sm font-semibold transition
                  ${isActive
                    ? 'text-stone-900 dark:text-zinc-100'
                    : 'text-stone-400 hover:text-stone-700 dark:text-zinc-500 dark:hover:text-zinc-300'
                  }`}
              >
                {s.name}
                {isActive && (
                  <span className={`absolute inset-x-0 -bottom-px h-0.5 ${theme.bg}`} />
                )}
              </button>
            )
          })}
        </div>
      )}

      {/* League identity header — the league is the subject of this page.
          The StadiumBackdrop component renders floodlight glows + bowl silhouette
          + crowd-dot texture, all themed to the league's brand color. This is the
          "live feels alive" + "sport over chrome" principles working together:
          the chrome IS the sport. */}
      {activeLeague && (
        <header className="relative overflow-hidden rounded-3xl border border-stone-200 bg-white px-6 py-7 dark:border-zinc-900 dark:bg-zinc-900/60">
          <StadiumBackdrop colorClass={theme.text} intensity="strong" />

          {/* Top accent — a thin colored bar across the very top of the header.
              Reinforces league identity without using the banned side-stripe pattern. */}
          <div className={`absolute inset-x-0 top-0 h-0.5 ${theme.bg} opacity-80`} />

          <div className="relative flex items-center gap-5">
            {activeLeague.logoUrl ? (
              <img
                src={activeLeague.logoUrl}
                alt={activeLeague.name}
                className="h-16 w-16 shrink-0 object-contain"
              />
            ) : (
              <div className={`flex h-16 w-16 shrink-0 items-center justify-center rounded-xl ${theme.tint} text-base font-extrabold tracking-tight ${theme.text}`}>
                {leagueAbbreviation(activeLeague.name)}
              </div>
            )}
            <div className="min-w-0 flex-1">
              <h1 className="truncate text-2xl font-extrabold tracking-tight">
                {activeLeague.name}
              </h1>
              <p className={`text-xs font-bold uppercase tracking-[0.12em] ${theme.text}`}>
                {activeLeague.country} <span className="mx-1.5 opacity-50">/</span> {activeLeague.season}
              </p>
            </div>
          </div>
        </header>
      )}

      {/* League selector pills — active league adopts its theme color so the
          active state is immediately recognisable across leagues. The pill ring
          tells you which league is selected; the color tells you which league
          you're in. */}
      {!loadingLeagues && leagues.length > 0 && (
        <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1">
          {leagues.map((l) => {
            const isActive = leagueId === l.id
            const pillTheme = getLeagueTheme(l.name, sportSlug)
            return (
              <button
                key={l.id}
                onClick={() => setLeague(l.id)}
                className={`shrink-0 rounded-lg px-3 py-1.5 text-xs font-bold transition
                  ${isActive
                    ? `${pillTheme.tint} ${pillTheme.text} ring-1 ${pillTheme.ring}`
                    : 'bg-stone-100 text-stone-500 hover:text-stone-900 dark:bg-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100'
                  }`}
              >
                {l.name}
              </button>
            )
          })}
        </div>
      )}

      {/* Standings / Teams / Results toggle — segmented control on a flat surface. */}
      <div className="inline-flex rounded-lg border border-stone-200 p-0.5 dark:border-zinc-900">
        {(['standings', 'teams', 'results'] as Tab[]).map((tab) => (
          <button
            key={tab}
            onClick={() => setTab(tab)}
            className={`rounded-md px-4 py-1.5 text-xs font-semibold capitalize transition
              ${activeTab === tab
                ? 'bg-stone-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                : 'text-stone-500 hover:text-stone-900 dark:text-zinc-500 dark:hover:text-zinc-100'
              }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {activeTab === 'standings' ? (
        loadingStandings ? <LoadingSpinner /> : (
          <StandingsTable
            entries={standings}
            // showZones only for domestic football leagues — Champions League
            // has no relegation, basketball uses a different ranking system.
            showZones={
              sportSlug === 'football' &&
              !activeLeague?.name?.toLowerCase().includes('champions')
            }
          />
        )
      ) : activeTab === 'teams' ? (
        loadingTeams ? (
          <LoadingSpinner />
        ) : teams.length === 0 ? (
          <p className="py-8 text-center text-sm text-stone-500 dark:text-zinc-500">No teams found</p>
        ) : (
          // Squad-wall layout. Hover uses the league's theme color so the brand
          // continuity carries through into the interaction state.
          <div className="grid grid-cols-2 gap-2 md:grid-cols-3 lg:grid-cols-4">
            {teams.map((team) => (
              <Link
                key={team.id}
                to={`/teams/${team.id}`}
                state={{ fromLeagues: true, sportSlug, leagueId }}
                className={`group flex flex-col items-center gap-3 rounded-xl border border-transparent bg-white px-3 py-5 transition
                           ${theme.hoverBorder} ${theme.hoverTint} active:scale-[0.97]
                           dark:bg-zinc-900/60`}
              >
                {team.crestUrl
                  ? <img src={team.crestUrl} alt={team.name} className="h-14 w-14 object-contain transition-transform group-hover:scale-105" />
                  : <div className="flex h-14 w-14 items-center justify-center rounded-full bg-stone-200 text-sm font-extrabold dark:bg-zinc-800 dark:text-zinc-300">{team.shortName.slice(0,3)}</div>
                }
                <div className="space-y-0.5">
                  <p className="text-center text-xs font-semibold leading-tight">{team.name}</p>
                  {team.stadium && (
                    <p className="flex items-center justify-center gap-1 text-[10px] text-stone-400 dark:text-zinc-600">
                      <MapPin size={9} />{team.stadium}
                    </p>
                  )}
                </div>
              </Link>
            ))}
          </div>
        )
      ) : (
        // Results tab — date picker + match cards for the selected league and date.
        // Clicking any match card navigates to MatchDetailPage where the box score is shown.
        // The DateNav here is scoped to this tab — it doesn't affect the HomePage date.
        <div className="space-y-3">
          <DateNav date={resultsDate} onChange={setResultsDate} />

          {loadingResults ? (
            <LoadingSpinner />
          ) : results.length === 0 ? (
            <div className="flex flex-col items-center gap-2 py-12 text-stone-400 dark:text-zinc-600">
              <span className="text-3xl">📅</span>
              <p className="text-sm">No matches on this date</p>
            </div>
          ) : (
            // Expandable result rows — finished matches can be expanded inline
            // to show starters + team stats. Box score is lazy-fetched on expand.
            <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/60">
              {results.map((match) => (
                <ResultRow key={match.id} match={match} leagueId={leagueId!} />
              ))}
            </section>
          )}
        </div>
      )}
    </div>
  )
}
