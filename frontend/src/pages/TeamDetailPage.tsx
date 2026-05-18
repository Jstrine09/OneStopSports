import { useParams, useNavigate, useLocation, Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchTeam, fetchTeamPlayers } from '../api/teams'
import {
  getFavoriteTeams, addFavoriteTeam, removeFavoriteTeam,
  getFavoritePlayers, addFavoritePlayer, removeFavoritePlayer,
} from '../api/auth'
import { useAuth } from '../context/AuthContext'
import LoadingSpinner from '../components/LoadingSpinner'
import { ChevronLeft, MapPin, Globe, Heart } from 'lucide-react'
import type { PlayerDto } from '../types'

// Position groups — covers football (soccer), basketball, and American football (NFL).
// Anything that doesn't match falls into the 'Other' bucket.
const POSITION_ORDER = [
  // Football (soccer)
  'Goalkeeper', 'Defender', 'Midfielder', 'Forward',
  // Basketball (mapped from balldontlie abbreviations in NbaDataLoader)
  'Guard', 'Guard-Forward', 'Forward-Center', 'Center',
  // NFL offense
  'Quarterback', 'Running Back', 'Fullback', 'Wide Receiver', 'Tight End',
  'Offensive Tackle', 'Offensive Guard',
  // NFL defense
  'Defensive End', 'Defensive Tackle', 'Linebacker',
  'Outside Linebacker', 'Inside Linebacker', 'Middle Linebacker',
  'Cornerback', 'Safety', 'Free Safety', 'Strong Safety',
  // NFL special teams
  'Kicker', 'Punter', 'Long Snapper',
  // Catch-all for anything else
  'Other',
]

function groupByPosition(players: PlayerDto[]): Record<string, PlayerDto[]> {
  return players.reduce<Record<string, PlayerDto[]>>((acc, player) => {
    const pos = player.position ?? 'Other'
    const bucket = POSITION_ORDER.includes(pos) ? pos : 'Other'
    acc[bucket] = [...(acc[bucket] ?? []), player]
    return acc
  }, {})
}


export default function TeamDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const { isAuthenticated } = useAuth()

  // When the Leagues page links to a team, it passes the active sport + league as router state.
  // We read that here so the Back button can return to exactly the right sport and tab.
  const fromState = location.state as { fromLeagues?: boolean; sportSlug?: string; leagueId?: number } | null

  const handleBack = () => {
    if (fromState?.fromLeagues && fromState.sportSlug) {
      const params = new URLSearchParams({ tab: 'teams', sport: fromState.sportSlug })
      if (fromState.leagueId) params.set('league', String(fromState.leagueId))
      navigate(`/leagues?${params.toString()}`)
    } else if ((window.history.state?.idx ?? 0) > 0) {
      navigate(-1)
    } else {
      navigate('/leagues')
    }
  }
  const queryClient = useQueryClient()
  const teamId = Number(id)

  const { data: team, isLoading: loadingTeam } = useQuery({
    queryKey: ['team', teamId],
    queryFn: () => fetchTeam(teamId),
    enabled: !!teamId,
  })

  const { data: players = [], isLoading: loadingPlayers } = useQuery({
    queryKey: ['team-players', teamId],
    queryFn: () => fetchTeamPlayers(teamId),
    enabled: !!teamId,
    staleTime: 5 * 60_000,
  })

  const { data: favTeams = [] } = useQuery({
    queryKey: ['favorites', 'teams'],
    queryFn: getFavoriteTeams,
    enabled: isAuthenticated,
    staleTime: 2 * 60_000,
  })

  const { data: favPlayers = [] } = useQuery({
    queryKey: ['favorites', 'players'],
    queryFn: getFavoritePlayers,
    enabled: isAuthenticated,
    staleTime: 2 * 60_000,
  })

  const isTeamFav = favTeams.some((t) => t.id === teamId)
  const favPlayerIds = new Set(favPlayers.map((p) => p.id))

  const toggleTeamFav = async () => {
    if (!isAuthenticated) { navigate('/auth'); return }
    try {
      if (isTeamFav) await removeFavoriteTeam(teamId)
      else await addFavoriteTeam(teamId)
      queryClient.invalidateQueries({ queryKey: ['favorites', 'teams'] })
    } catch (err) {
      console.error('[TeamDetailPage] toggleTeamFav failed:', err)
    }
  }

  const togglePlayerFav = async (playerId: number) => {
    if (!isAuthenticated) { navigate('/auth'); return }
    try {
      if (favPlayerIds.has(playerId)) await removeFavoritePlayer(playerId)
      else await addFavoritePlayer(playerId)
      queryClient.invalidateQueries({ queryKey: ['favorites', 'players'] })
    } catch (err) {
      console.error('[TeamDetailPage] togglePlayerFav failed:', err)
    }
  }

  const grouped = groupByPosition(players)

  return (
    <div className="space-y-5">
      {/* Back */}
      <button
        onClick={handleBack}
        className="flex min-h-[44px] items-center gap-1 py-2 text-sm text-slate-500 transition-colors hover:text-slate-900 dark:text-zinc-500 dark:hover:text-zinc-100"
      >
        <ChevronLeft size={16} /> Back
      </button>

      {/* Team identity header — crest is the anchor of the page.
          Larger crest, generous breathing room around the name, metadata sits
          below in a quieter row. The favourite heart is the only call to action
          here and sits at the top right so it doesn't compete with identity. */}
      {loadingTeam ? (
        <LoadingSpinner />
      ) : team ? (
        <header className="relative overflow-hidden rounded-3xl border border-slate-200 bg-white px-6 py-7 dark:border-zinc-900 dark:bg-zinc-900/60">
          {/* Subtle radial wash behind the crest — adds depth without using
              gradient text or glassmorphism (both banned). */}
          <div className="pointer-events-none absolute -left-12 -top-12 h-48 w-48 rounded-full bg-amber-500/[0.04] blur-3xl" />

          <div className="relative flex items-center gap-5">
            {team.crestUrl ? (
              <img src={team.crestUrl} alt={team.name} className="h-20 w-20 shrink-0 object-contain sm:h-24 sm:w-24" />
            ) : (
              <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-2xl bg-slate-200 text-2xl font-extrabold dark:bg-zinc-800 dark:text-zinc-300 sm:h-24 sm:w-24">
                {team.shortName.slice(0, 3)}
              </div>
            )}

            <div className="min-w-0 flex-1 space-y-2">
              <h1 className="truncate text-2xl font-extrabold tracking-tight sm:text-3xl">{team.name}</h1>
              <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500 dark:text-zinc-500">
                {team.country && (
                  <span className="flex items-center gap-1.5">
                    <Globe size={12} className="opacity-60" /> {team.country}
                  </span>
                )}
                {team.stadium && (
                  <span className="flex items-center gap-1.5">
                    <MapPin size={12} className="opacity-60" /> {team.stadium}
                  </span>
                )}
              </div>
            </div>

            <button
              onClick={toggleTeamFav}
              className="absolute right-0 top-0 flex min-h-[44px] min-w-[44px] items-center justify-center rounded-full transition active:scale-90 hover:bg-slate-100 dark:hover:bg-zinc-800"
              aria-label={isTeamFav ? 'Remove from favourites' : 'Add to favourites'}
            >
              <Heart
                size={22}
                className={isTeamFav ? 'fill-red-500 text-red-500' : 'text-slate-400 dark:text-zinc-600'}
              />
            </button>
          </div>
        </header>
      ) : null}

      {/* Squad section — like a printed team sheet rather than a card collection.
          Position groups stack as sections with tight uppercase labels, players
          sit as flat rows inside a single rounded surface per group. */}
      <section>
        <div className="mb-3 flex items-baseline justify-between px-1">
          <h2 className="text-[11px] font-bold uppercase tracking-[0.12em] text-slate-500 dark:text-zinc-400">
            Squad
          </h2>
          {players.length > 0 && (
            <span className="text-xs tabular-nums text-slate-400 dark:text-zinc-600">{players.length} players</span>
          )}
        </div>

        {loadingPlayers ? (
          <LoadingSpinner />
        ) : players.length === 0 ? (
          <p className="py-8 text-center text-sm text-slate-500 dark:text-zinc-500">No squad data available</p>
        ) : (
          <div className="space-y-4">
            {POSITION_ORDER
              .filter((pos) => grouped[pos]?.length > 0)
              .map((pos) => (
                <div key={pos}>
                  <h3 className="mb-1.5 px-1 text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400 dark:text-zinc-600">
                    {pos}s <span className="ml-1 opacity-60">· {grouped[pos].length}</span>
                  </h3>
                  <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/60">
                    {grouped[pos].map((player) => {
                      const playerFav = favPlayerIds.has(player.id)
                      return (
                        <div
                          key={player.id}
                          className="flex items-center gap-3 border-t border-slate-100 px-4 py-2.5 first:border-0 transition-colors hover:bg-slate-50 dark:border-zinc-900 dark:hover:bg-zinc-800/40"
                        >
                          {/* Jersey number — small but bold, the player's identifier */}
                          <span className="w-8 shrink-0 text-center text-xs font-extrabold tabular-nums text-slate-400 dark:text-zinc-500">
                            {player.jerseyNumber != null ? player.jerseyNumber : '—'}
                          </span>

                          {/* Name + nationality */}
                          <div className="flex-1 overflow-hidden">
                            <Link
                              to={`/players/${player.id}`}
                              state={player}
                              className="truncate text-sm font-semibold transition-colors hover:text-amber-400"
                            >
                              {player.name}
                            </Link>
                            {player.nationality && (
                              <p className="truncate text-xs text-slate-500 dark:text-zinc-500">{player.nationality}</p>
                            )}
                          </div>

                          {/* Favourite toggle */}
                          <button
                            onClick={() => togglePlayerFav(player.id)}
                            className="shrink-0 rounded-full p-1.5 transition active:scale-90 hover:bg-slate-100 dark:hover:bg-zinc-800"
                            aria-label={playerFav ? 'Remove from favourites' : 'Add to favourites'}
                          >
                            <Heart
                              size={14}
                              className={playerFav ? 'fill-red-500 text-red-500' : 'text-slate-300 dark:text-zinc-700'}
                            />
                          </button>
                        </div>
                      )
                    })}
                  </div>
                </div>
              ))}
          </div>
        )}
      </section>
    </div>
  )
}
