import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchSports, fetchLeaguesBySport } from '../api/sports'
import { fetchStandings, fetchTeamsByLeague } from '../api/leagues'
import StandingsTable from '../components/StandingsTable'
import LoadingSpinner from '../components/LoadingSpinner'
import StadiumBackdrop from '../components/StadiumBackdrop'
import { getLeagueTheme } from '../lib/leagueTheme'
import { MapPin } from 'lucide-react'

type Tab = 'standings' | 'teams'

export default function LeaguesPage() {
  // useSearchParams keeps the active sport/league/tab in the URL so navigation
  // (back button, deep links, shared URLs) restore the correct view.
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
              <div className={`flex h-16 w-16 shrink-0 items-center justify-center rounded-xl ${theme.tint} text-lg font-extrabold ${theme.text}`}>
                {activeLeague.name.slice(0, 2).toUpperCase()}
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

      {/* Standings / Teams toggle — segmented control on a flat surface.
          Stays neutral (zinc-100 fill) — it's a control, not identity. */}
      <div className="inline-flex rounded-lg border border-stone-200 p-0.5 dark:border-zinc-900">
        {(['standings', 'teams'] as Tab[]).map((tab) => (
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
      ) : (
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
      )}
    </div>
  )
}
