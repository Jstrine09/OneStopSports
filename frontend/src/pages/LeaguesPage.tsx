import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchSports, fetchLeaguesBySport } from '../api/sports'
import { fetchStandings, fetchTeamsByLeague } from '../api/leagues'
import StandingsTable from '../components/StandingsTable'
import LoadingSpinner from '../components/LoadingSpinner'
import { Trophy, MapPin } from 'lucide-react'

type Tab = 'standings' | 'teams'

export default function LeaguesPage() {
  // useSearchParams reads and writes the URL query string — e.g. ?sport=basketball&league=7&tab=teams
  // We use this instead of useState so the active sport/league/tab survive navigation.
  // When the user goes to a team and presses Back, the URL is restored and they land
  // exactly where they were — same sport, same league, same tab.
  const [searchParams, setSearchParams] = useSearchParams()

  // Fetch all sports so we can show sport selector pills (Football, Basketball, etc.)
  const { data: sports = [], isLoading: loadingSports } = useQuery({
    queryKey: ['sports'],
    queryFn: fetchSports,
    staleTime: 10 * 60_000,
  })

  // Read the active state from the URL — fall back to sensible defaults when params aren't set
  const sportSlug    = searchParams.get('sport')  ?? sports[0]?.slug ?? null  // e.g. 'basketball'
  const activeLeagueId = searchParams.get('league') ? Number(searchParams.get('league')) : null // e.g. 7
  const activeTab    = (searchParams.get('tab') ?? 'standings') as Tab  // 'standings' or 'teams'

  // Fetch leagues for the selected sport (e.g. all football leagues, or just NBA for basketball)
  const { data: leagues = [], isLoading: loadingLeagues } = useQuery({
    queryKey: ['leagues', sportSlug],
    queryFn: () => fetchLeaguesBySport(sportSlug!),
    enabled: !!sportSlug, // don't fetch until we know which sport is selected
    staleTime: 5 * 60_000,
  })

  // If no league is explicitly selected in the URL, fall back to the first one in the list
  const leagueId = activeLeagueId ?? leagues[0]?.id ?? null

  // Fetch standings for the selected league — only when the standings tab is active
  const { data: standings = [], isLoading: loadingStandings } = useQuery({
    queryKey: ['standings', leagueId],
    queryFn: () => fetchStandings(leagueId!),
    enabled: leagueId !== null && activeTab === 'standings',
    staleTime: 5 * 60_000,
  })

  // Fetch teams for the selected league — only when the teams tab is active
  const { data: teams = [], isLoading: loadingTeams } = useQuery({
    queryKey: ['teams', leagueId],
    queryFn: () => fetchTeamsByLeague(leagueId!),
    enabled: leagueId !== null && activeTab === 'teams',
    staleTime: 5 * 60_000,
  })

  // The full league object for the currently selected league — used to show name/country/season
  const activeLeague = leagues.find((l) => l.id === leagueId) ?? leagues[0]

  // --- URL update helpers ---
  // All three use { replace: true } which means the URL change REPLACES the current history entry
  // instead of pushing a new one. This way, pressing Back from a team page doesn't cycle through
  // every sport/league/tab the user clicked — it just goes straight back to the leagues page.

  // Called when the user clicks a sport pill (e.g. switches from Football to Basketball)
  const setSport = (slug: string) =>
    setSearchParams({ sport: slug }, { replace: true })

  // Called when the user clicks a league pill (e.g. switches from Premier League to NBA)
  // We keep the sport param so it doesn't get wiped when switching leagues
  const setLeague = (id: number) =>
    setSearchParams({ sport: sportSlug ?? '', league: String(id) }, { replace: true })

  // Called when the user clicks the Standings or Teams tab
  // We preserve sport and league so the context doesn't reset
  const setTab = (tab: Tab) => {
    const params: Record<string, string> = { tab }
    if (sportSlug) params.sport  = sportSlug
    if (leagueId)  params.league = String(leagueId)
    setSearchParams(params, { replace: true })
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Trophy size={20} className="text-blue-400" />
        <h1 className="text-xl font-bold">Leagues</h1>
      </div>

      {/* Sport selector pills — only shown when more than one sport exists in the DB */}
      {!loadingSports && sports.length > 1 && (
        <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1">
          {sports.map((s) => (
            <button
              key={s.slug}
              onClick={() => setSport(s.slug)}
              className={`shrink-0 rounded-full px-4 py-1.5 text-xs font-semibold transition
                ${sportSlug === s.slug
                  ? 'bg-blue-500 text-white'
                  : 'bg-slate-800 text-slate-400 hover:bg-slate-700 hover:text-white'
                }`}
            >
              {s.name}
            </button>
          ))}
        </div>
      )}

      {/* League selector pills — e.g. Premier League, La Liga, NBA */}
      {!loadingLeagues && leagues.length > 0 && (
        <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1">
          {leagues.map((l) => (
            <button
              key={l.id}
              onClick={() => setLeague(l.id)}
              className={`shrink-0 rounded-full px-4 py-1.5 text-xs font-semibold transition
                ${leagueId === l.id
                  ? 'bg-blue-500 text-white'
                  : 'bg-slate-800 text-slate-400 hover:bg-slate-700 hover:text-white'
                }`}
            >
              {l.name}
            </button>
          ))}
        </div>
      )}

      {/* League info bar — shows the selected league's country and season */}
      {activeLeague && (
        <div className="rounded-xl bg-slate-800 px-4 py-3">
          <p className="text-xs text-slate-400">{activeLeague.country}</p>
          <p className="font-semibold">
            {activeLeague.name}{' '}
            <span className="text-sm font-normal text-slate-400">· {activeLeague.season}</span>
          </p>
        </div>
      )}

      {/* Standings / Teams tab toggle */}
      <div className="flex rounded-xl bg-slate-800 p-1">
        {(['standings', 'teams'] as Tab[]).map((tab) => (
          <button
            key={tab}
            onClick={() => setTab(tab)}
            className={`flex-1 rounded-lg py-2 text-xs font-semibold capitalize transition
              ${activeTab === tab
                ? 'bg-blue-500 text-white'
                : 'text-slate-400 hover:text-white'
              }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {activeTab === 'standings' ? (
        loadingStandings ? <LoadingSpinner /> : (
          // showZones controls whether row shading + left border + legend appear.
          // We show them only for domestic football leagues — the Champions League has no
          // relegation/promotion zones, and basketball uses a completely different ranking system.
          <StandingsTable
            entries={standings}
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
          <p className="py-8 text-center text-sm text-slate-400">No teams found</p>
        ) : (
          <div className="grid grid-cols-2 gap-2 md:grid-cols-3 lg:grid-cols-4">
            {teams.map((team) => (
              <Link
                key={team.id}
                to={`/teams/${team.id}`}
                // Pass the current sport + league as router state.
                // TeamDetailPage reads this in its Back button so it can navigate
                // back to exactly this sport, league, and tab — not the default view.
                state={{ fromLeagues: true, sportSlug, leagueId }}
                className="flex flex-col items-center gap-2 rounded-xl bg-slate-800 p-4 transition hover:bg-slate-700 active:scale-[0.97]"
              >
                {team.crestUrl
                  ? <img src={team.crestUrl} alt={team.name} className="h-12 w-12 object-contain" />
                  : <div className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-600 text-sm font-bold">{team.shortName.slice(0,3)}</div>
                }
                <p className="text-center text-xs font-semibold leading-tight">{team.name}</p>
                {team.stadium && (
                  <p className="flex items-center gap-1 text-center text-[10px] text-slate-500">
                    <MapPin size={10} />{team.stadium}
                  </p>
                )}
              </Link>
            ))}
          </div>
        )
      )}
    </div>
  )
}
