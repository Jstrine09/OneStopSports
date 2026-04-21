import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchSports, fetchLeaguesBySport } from '../api/sports'
import { fetchStandings, fetchTeamsByLeague } from '../api/leagues'
import StandingsTable from '../components/StandingsTable'
import LoadingSpinner from '../components/LoadingSpinner'
import { Trophy, MapPin } from 'lucide-react'

type Tab = 'standings' | 'teams'

export default function LeaguesPage() {
  // Fetch all sports dynamically
  const { data: sports = [], isLoading: loadingSports } = useQuery({
    queryKey: ['sports'],
    queryFn: fetchSports,
    staleTime: 10 * 60_000,
  })

  const [activeSportSlug, setActiveSportSlug] = useState<string | null>(null)
  const [activeLeagueId, setActiveLeagueId] = useState<number | null>(null)
  const [activeTab, setActiveTab] = useState<Tab>('standings')

  const sportSlug = activeSportSlug ?? sports[0]?.slug ?? null

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

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Trophy size={20} className="text-blue-400" />
        <h1 className="text-xl font-bold">Leagues</h1>
      </div>

      {/* Sport selector — only shown when multiple sports exist */}
      {!loadingSports && sports.length > 1 && (
        <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1">
          {sports.map((s) => (
            <button
              key={s.slug}
              onClick={() => { setActiveSportSlug(s.slug); setActiveLeagueId(null) }}
              className={`shrink-0 rounded-full px-4 py-1.5 text-xs font-semibold transition
                ${(activeSportSlug ?? sports[0]?.slug) === s.slug
                  ? 'bg-blue-500 text-white'
                  : 'bg-slate-800 text-slate-400 hover:bg-slate-700 hover:text-white'
                }`}
            >
              {s.name}
            </button>
          ))}
        </div>
      )}

      {/* League selector pills */}
      {!loadingLeagues && leagues.length > 0 && (
        <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1">
          {leagues.map((l) => (
            <button
              key={l.id}
              onClick={() => { setActiveLeagueId(l.id); setActiveTab('standings') }}
              className={`shrink-0 rounded-full px-4 py-1.5 text-xs font-semibold transition
                ${(activeLeagueId ?? leagues[0]?.id) === l.id
                  ? 'bg-blue-500 text-white'
                  : 'bg-slate-800 text-slate-400 hover:bg-slate-700 hover:text-white'
                }`}
            >
              {l.name}
            </button>
          ))}
        </div>
      )}

      {/* League info bar */}
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
            onClick={() => setActiveTab(tab)}
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
        loadingStandings ? <LoadingSpinner /> : <StandingsTable entries={standings} />
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
