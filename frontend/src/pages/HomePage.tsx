import { useState } from 'react'
import { useQuery, useQueries } from '@tanstack/react-query'
import { fetchSports, fetchLeaguesBySport } from '../api/sports'
import { fetchMatchesByLeagueAndDate } from '../api/matches'
import MatchCard from '../components/MatchCard'
import DateNav from '../components/DateNav'
import LoadingSpinner from '../components/LoadingSpinner'
import type { LeagueDto, MatchDto } from '../types'

function todayStr() {
  return new Date().toISOString().slice(0, 10)
}

export default function HomePage() {
  const [date, setDate] = useState(todayStr)
  const [activeLeague, setActiveLeague] = useState<number | 'all'>('all')

  // Fetch all sports dynamically — no hardcoded slug
  const { data: sports = [] } = useQuery({
    queryKey: ['sports'],
    queryFn: fetchSports,
    staleTime: 10 * 60_000,
  })

  // Fetch leagues for every sport in parallel
  const leagueQueries = useQueries({
    queries: sports.map((sport) => ({
      queryKey: ['leagues', sport.slug],
      queryFn: () => fetchLeaguesBySport(sport.slug),
      staleTime: 5 * 60_000,
    })),
  })

  // Flatten all leagues across all sports
  const allLeagues: LeagueDto[] = leagueQueries.flatMap((q) => q.data ?? [])

  // Fetch matches for every league in parallel for the selected date
  const matchQueries = useQueries({
    queries: allLeagues.map((league) => ({
      queryKey: ['matches', league.id, date],
      queryFn: () => fetchMatchesByLeagueAndDate(league.id, date),
      staleTime: 30_000,
    })),
  })

  const isLoading = leagueQueries.some((q) => q.isLoading) || matchQueries.some((q) => q.isLoading)

  // Build filtered sections
  const sections = allLeagues
    .map((league, i) => ({
      league,
      matches: (matchQueries[i]?.data ?? []) as MatchDto[],
    }))
    .filter(({ league, matches }) => {
      if (matches.length === 0) return false
      if (activeLeague === 'all') return true
      return league.id === activeLeague
    })

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-extrabold tracking-tight">
          OneStop<span className="text-blue-400">Sports</span>
        </h1>
      </div>

      {/* Date navigation */}
      <DateNav date={date} onChange={setDate} />

      {/* League filter pills */}
      {allLeagues.length > 0 && (
        <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1">
          <button
            onClick={() => setActiveLeague('all')}
            className={`shrink-0 rounded-full px-4 py-1.5 text-xs font-semibold transition
              ${activeLeague === 'all'
                ? 'bg-blue-500 text-white'
                : 'bg-slate-800 text-slate-400 hover:bg-slate-700 hover:text-white'
              }`}
          >
            All
          </button>
          {allLeagues.map((l) => (
            <button
              key={l.id}
              onClick={() => setActiveLeague(l.id)}
              className={`shrink-0 rounded-full px-4 py-1.5 text-xs font-semibold transition
                ${activeLeague === l.id
                  ? 'bg-blue-500 text-white'
                  : 'bg-slate-800 text-slate-400 hover:bg-slate-700 hover:text-white'
                }`}
            >
              {l.name}
            </button>
          ))}
        </div>
      )}

      {/* Match sections */}
      {isLoading ? (
        <LoadingSpinner />
      ) : sections.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-16 text-slate-400">
          <span className="text-4xl">🏅</span>
          <p className="text-sm">No matches on this date</p>
        </div>
      ) : (
        sections.map(({ league, matches }) => (
          <section key={league.id} className="space-y-2">
            <div className="flex items-center gap-2 px-1">
              {league.logoUrl && (
                <img src={league.logoUrl} alt={league.name} className="h-4 w-4 object-contain" />
              )}
              <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                {league.country} · {league.name}
              </h2>
            </div>
            <div className="space-y-1.5">
              {matches.map((match) => (
                <MatchCard key={match.id} match={match} />
              ))}
            </div>
          </section>
        ))
      )}
    </div>
  )
}
