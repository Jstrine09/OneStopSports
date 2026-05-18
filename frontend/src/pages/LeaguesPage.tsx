import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchSports, fetchLeaguesBySport } from '../api/sports'
import { fetchStandings, fetchTeamsByLeague } from '../api/leagues'
import StandingsTable from '../components/StandingsTable'
import LoadingSpinner from '../components/LoadingSpinner'
import { MapPin } from 'lucide-react'

type Tab = 'standings' | 'teams'

export default function LeaguesPage() {
  // useSearchParams reads and writes the URL query string — e.g. ?sport=basketball&league=7&tab=teams
  // This keeps the active sport/league/tab in the URL so navigation (back button, deep links,
  // shared URLs) all restore the correct view.
  const [searchParams, setSearchParams] = useSearchParams()

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

  const activeLeague = leagues.find((l) => l.id === leagueId) ?? leagues[0]

  // URL update helpers — { replace: true } so back button doesn't cycle through every pill click
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
      {/* Sport selector — underline tab style instead of pills.
          A horizontal underline reads as "navigation between sections", which is
          what switching sports really is. Pills here felt like filter chips on
          a SaaS dashboard, which is the anti-reference. */}
      {!loadingSports && sports.length > 1 && (
        <div className="flex gap-6 overflow-x-auto no-scrollbar border-b border-slate-200 dark:border-zinc-900">
          {sports.map((s) => {
            const isActive = sportSlug === s.slug
            return (
              <button
                key={s.slug}
                onClick={() => setSport(s.slug)}
                className={`relative shrink-0 pb-3 text-sm font-semibold transition
                  ${isActive
                    ? 'text-slate-900 dark:text-zinc-100'
                    : 'text-slate-400 hover:text-slate-700 dark:text-zinc-500 dark:hover:text-zinc-300'
                  }`}
              >
                {s.name}
                {isActive && (
                  <span className="absolute inset-x-0 -bottom-px h-0.5 bg-amber-400" />
                )}
              </button>
            )
          })}
        </div>
      )}

      {/* League identity header — the league is the subject of this page.
          Logo + big name + season puts the league front and centre instead of
          burying it inside a small info card. This is the "sport over chrome"
          principle in action. */}
      {activeLeague && (
        <header className="flex items-center gap-4 py-1">
          {activeLeague.logoUrl ? (
            <img
              src={activeLeague.logoUrl}
              alt={activeLeague.name}
              className="h-14 w-14 shrink-0 object-contain"
            />
          ) : (
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-amber-500/10 text-lg font-extrabold text-amber-400">
              {activeLeague.name.slice(0, 2).toUpperCase()}
            </div>
          )}
          <div className="min-w-0 flex-1">
            <h1 className="truncate text-2xl font-extrabold tracking-tight">
              {activeLeague.name}
            </h1>
            <p className="text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-zinc-500">
              {activeLeague.country} <span className="mx-1.5 opacity-40">/</span> {activeLeague.season}
            </p>
          </div>
        </header>
      )}

      {/* League selector — horizontal-scroll pills, kept since there can be
          many leagues per sport. Active league uses amber on a tinted bg
          (not solid blue fill) so the pill feels lit-up rather than stamped. */}
      {!loadingLeagues && leagues.length > 0 && (
        <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1">
          {leagues.map((l) => {
            const isActive = leagueId === l.id
            return (
              <button
                key={l.id}
                onClick={() => setLeague(l.id)}
                className={`shrink-0 rounded-lg px-3 py-1.5 text-xs font-semibold transition
                  ${isActive
                    ? 'bg-amber-500/15 text-amber-400 ring-1 ring-amber-400/40'
                    : 'bg-slate-100 text-slate-500 hover:text-slate-900 dark:bg-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100'
                  }`}
              >
                {l.name}
              </button>
            )
          })}
        </div>
      )}

      {/* Standings / Teams tab toggle — segmented control on a flat surface.
          Padding inside the segments stays consistent; the active pill shifts
          rather than the whole control morphing. */}
      <div className="inline-flex rounded-lg border border-slate-200 p-0.5 dark:border-zinc-900">
        {(['standings', 'teams'] as Tab[]).map((tab) => (
          <button
            key={tab}
            onClick={() => setTab(tab)}
            className={`rounded-md px-4 py-1.5 text-xs font-semibold capitalize transition
              ${activeTab === tab
                ? 'bg-slate-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                : 'text-slate-500 hover:text-slate-900 dark:text-zinc-500 dark:hover:text-zinc-100'
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
      ) : (
        loadingTeams ? (
          <LoadingSpinner />
        ) : teams.length === 0 ? (
          <p className="py-8 text-center text-sm text-slate-500 dark:text-zinc-500">No teams found</p>
        ) : (
          // Squad-wall layout — bigger crests, less uniform padding than the old grid.
          // Reads as a press wall of clubs, not a card collection.
          <div className="grid grid-cols-2 gap-2 md:grid-cols-3 lg:grid-cols-4">
            {teams.map((team) => (
              <Link
                key={team.id}
                to={`/teams/${team.id}`}
                state={{ fromLeagues: true, sportSlug, leagueId }}
                className="group flex flex-col items-center gap-3 rounded-xl border border-transparent bg-white px-3 py-5 transition
                           hover:border-amber-400/30 hover:bg-amber-500/[0.03] active:scale-[0.97]
                           dark:bg-zinc-900/60 dark:hover:border-amber-400/30 dark:hover:bg-amber-500/[0.04]"
              >
                {team.crestUrl
                  ? <img src={team.crestUrl} alt={team.name} className="h-14 w-14 object-contain transition-transform group-hover:scale-105" />
                  : <div className="flex h-14 w-14 items-center justify-center rounded-full bg-slate-200 text-sm font-extrabold dark:bg-zinc-800 dark:text-zinc-300">{team.shortName.slice(0,3)}</div>
                }
                <div className="space-y-0.5">
                  <p className="text-center text-xs font-semibold leading-tight">{team.name}</p>
                  {team.stadium && (
                    <p className="flex items-center justify-center gap-1 text-[10px] text-slate-400 dark:text-zinc-600">
                      <MapPin size={9} />{team.stadium}
                    </p>
                  )}
                </div>
              </Link>
            ))}
          </div>
        )
      )}
    </div>
  )
}
