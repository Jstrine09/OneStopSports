import { useState } from 'react'
import { useQuery, useQueries } from '@tanstack/react-query'
import { fetchSports, fetchLeaguesBySport } from '../api/sports'
import { fetchMatchesByLeagueAndDate } from '../api/matches'
import MatchCard from '../components/MatchCard'
import DateNav from '../components/DateNav'
import LoadingSpinner from '../components/LoadingSpinner'
import SportFieldBackdrop, { fieldVariantForSport } from '../components/SportFieldBackdrop'
import { getLeagueTheme } from '../lib/leagueTheme'
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

  // Map a league back to its sport slug so each group can render the right field
  // (pitch / court / gridiron). Leagues only carry sportId, so we resolve via the
  // sports list we already fetched.
  const sportSlugById = new Map(sports.map((s) => [s.id, s.slug]))

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
          OneStop<span className="text-amber-600 dark:text-amber-400">Sports</span>
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
                ? 'bg-amber-500 text-white'
                : 'bg-stone-100 text-stone-500 hover:bg-stone-200 hover:text-stone-900 dark:bg-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-700 dark:hover:text-white'
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
                  ? 'bg-amber-500 text-white'
                  : 'bg-stone-100 text-stone-500 hover:bg-stone-200 hover:text-stone-900 dark:bg-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-700 dark:hover:text-white'
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
        <div className="flex flex-col items-center gap-2 py-16 text-stone-500 dark:text-zinc-400">
          <span className="text-4xl">🏅</span>
          <p className="text-sm">No matches on this date</p>
        </div>
      ) : (
        sections.map(({ league, matches }) => {
          const slug = sportSlugById.get(league.sportId)
          const theme = getLeagueTheme(league.name, slug)
          const variant = fieldVariantForSport(slug)
          return (
            <section key={league.id} className="space-y-2">
              <div className="flex items-center gap-2 px-1">
                {league.logoUrl ? (
                  <img src={league.logoUrl} alt={league.name} className="h-4 w-4 object-contain" />
                ) : (
                  // Colour bullet keyed to the league when there's no crest
                  <span className={`inline-block h-2.5 w-2.5 rounded-sm ${theme.bg}`} />
                )}
                <h2 className={`text-xs font-bold uppercase tracking-wider ${theme.text}`}>
                  {league.country} · {league.name}
                </h2>
              </div>

              {/* Match panel — the sport's field sits behind, themed to the league
                  colour; the rows sit on a glass surface so the field reads through.
                  The field is hidden on phones (handled inside SportFieldBackdrop). */}
              <div className="relative overflow-hidden rounded-2xl border border-stone-200 dark:border-zinc-800">
                <SportFieldBackdrop
                  colorClass={theme.text}
                  variant={variant}
                  intensity="strong"
                  className="hidden md:block"
                />
                <div className="relative glass-card">
                  {matches.map((match) => (
                    <MatchCard key={match.id} match={match} />
                  ))}
                </div>
              </div>
            </section>
          )
        })
      )}
    </div>
  )
}
