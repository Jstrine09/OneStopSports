import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Search } from 'lucide-react'
import { searchAll } from '../api/search'
import LoadingSpinner from '../components/LoadingSpinner'
import SectionLabel from '../components/SectionLabel'

export default function SearchPage() {
  // The current value of the search input — drives the React Query fetch
  const [query, setQuery] = useState('')

  const trimmed = query.trim()

  // Only fire the request once the user has typed at least 2 characters.
  // React Query re-runs automatically whenever `query` changes.
  const { data, isLoading } = useQuery({
    queryKey: ['search', trimmed],
    queryFn: () => searchAll(trimmed),
    enabled: trimmed.length >= 2,  // Don't fetch for empty / single-char queries
    staleTime: 30_000,             // Cache results for 30s — search results don't change that fast
  })

  const hasResults = data && (data.teams.length > 0 || data.players.length > 0)
  const showEmpty  = trimmed.length >= 2 && !isLoading && !hasResults

  return (
    <div className="space-y-4">
      {/* Page header */}
      <div className="flex items-center gap-2">
        <Search size={20} className="text-stone-500 dark:text-zinc-400" />
        <h1 className="text-xl font-bold">Search</h1>
      </div>

      {/* Search input — autofocused so users can type immediately on mobile */}
      <input
        type="search"
        placeholder="Search teams or players…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        autoFocus
        autoCapitalize="none"
        autoCorrect="off"
        spellCheck={false}
        className="w-full rounded-xl bg-white px-4 py-3 text-base text-stone-900
                   placeholder-stone-400 outline-none ring-1 ring-stone-200
                   focus:ring-amber-500 transition
                   dark:bg-zinc-900 dark:text-white dark:placeholder-zinc-500 dark:ring-zinc-800
                   md:text-sm"
      />

      {/* Loading state — shown while the API request is in flight */}
      {isLoading && <LoadingSpinner />}

      {/* Empty state — shown when the query is long enough but there are no matches */}
      {showEmpty && (
        <p className="py-8 text-center text-sm text-stone-500 dark:text-zinc-400">
          No results for "{trimmed}"
        </p>
      )}

      {/* Team results */}
      {data && data.teams.length > 0 && (
        <section className="space-y-2">
          <SectionLabel>Teams</SectionLabel>
          <div className="space-y-1.5">
            {data.teams.map((team) => (
              <Link
                key={team.id}
                to={`/teams/${team.id}`}
                className="flex items-center gap-3 rounded-xl bg-white px-4 py-3
                           transition hover:bg-stone-50 active:scale-[0.98]
                           dark:bg-zinc-900/60 dark:hover:bg-zinc-800"
              >
                {/* Team crest or abbreviation fallback — same logic as MatchCard */}
                {team.crestUrl ? (
                  <img src={team.crestUrl} alt={team.name} className="h-8 w-8 object-contain" />
                ) : (
                  <div className="flex h-8 w-8 items-center justify-center rounded-full
                                  bg-stone-200 text-xs font-bold dark:bg-zinc-600">
                    {(team.shortName ?? team.name).slice(0, 3).toUpperCase()}
                  </div>
                )}
                <div>
                  <p className="text-sm font-medium">{team.name}</p>
                  {/* Country shown as a subtle subtitle */}
                  {team.country && (
                    <p className="text-xs text-stone-500 dark:text-zinc-400">{team.country}</p>
                  )}
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* Player results */}
      {data && data.players.length > 0 && (
        <section className="space-y-2">
          <SectionLabel>Players</SectionLabel>
          <div className="space-y-1.5">
            {data.players.map((player) => (
              <Link
                key={player.id!}
                to={`/players/${player.id!}`}
                className="flex items-center gap-3 rounded-xl bg-white px-4 py-3
                           transition hover:bg-stone-50 active:scale-[0.98]
                           dark:bg-zinc-900/60 dark:hover:bg-zinc-800"
              >
                {/* Jersey number badge, or # if unknown */}
                <div className="flex h-8 w-8 items-center justify-center rounded-full
                                bg-stone-100 text-xs font-bold text-stone-600 shrink-0
                                dark:bg-zinc-700 dark:text-zinc-300">
                  {player.jerseyNumber ?? '#'}
                </div>
                <div className="overflow-hidden">
                  <p className="truncate text-sm font-medium">{player.name}</p>
                  {/* Position and nationality as a single subtitle line */}
                  <p className="text-xs text-stone-500 dark:text-zinc-400">
                    {[player.position, player.nationality].filter(Boolean).join(' · ')}
                  </p>
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* Prompt shown before the user starts typing */}
      {!trimmed && (
        <p className="py-8 text-center text-sm text-stone-500 dark:text-zinc-400">
          Search for any team or player across all sports
        </p>
      )}
    </div>
  )
}
