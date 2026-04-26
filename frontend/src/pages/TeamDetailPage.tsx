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

// Position groups — covers both football and basketball.
// Any position that doesn't match one of these falls into the 'Other' bucket.
const POSITION_ORDER = [
  // Football positions
  'Goalkeeper', 'Defender', 'Midfielder', 'Forward',
  // Basketball positions (mapped from balldontlie abbreviations in NbaDataLoader)
  'Guard', 'Center', 'Guard-Forward', 'Forward-Center',
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

function calculateAge(dob: string | null): string {
  if (!dob) return '—'
  const birth = new Date(dob)
  const age = Math.floor((Date.now() - birth.getTime()) / (365.25 * 24 * 60 * 60 * 1000))
  return `${age}`
}

export default function TeamDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const { isAuthenticated } = useAuth()

  // When the Leagues page links to a team, it passes the active sport + league as router state.
  // e.g. { fromLeagues: true, sportSlug: 'basketball', leagueId: 7 }
  // We read that here so the Back button can return to exactly the right sport and tab.
  const fromState = location.state as { fromLeagues?: boolean; sportSlug?: string; leagueId?: number } | null

  const handleBack = () => {
    if (fromState?.fromLeagues && fromState.sportSlug) {
      // The user came from the Leagues → Teams tab.
      // Build the URL manually so we land back on the correct sport, league, AND tab=teams.
      // Without this, the Leagues page would remount with its default state (football, standings).
      const params = new URLSearchParams({ tab: 'teams', sport: fromState.sportSlug })
      if (fromState.leagueId) params.set('league', String(fromState.leagueId))
      navigate(`/leagues?${params.toString()}`)
    } else if ((window.history.state?.idx ?? 0) > 0) {
      // window.history.state.idx is React Router's internal page counter.
      // If it's greater than 0, the user navigated here from somewhere — go back normally.
      navigate(-1)
    } else {
      // idx === 0 means this was the first page loaded (e.g. direct URL / bookmark).
      // There's nothing to go back to, so send them to Leagues as a safe landing page.
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

  // Favourite teams — only fetch when signed in
  const { data: favTeams = [] } = useQuery({
    queryKey: ['favorites', 'teams'],
    queryFn: getFavoriteTeams,
    enabled: isAuthenticated,
    staleTime: 2 * 60_000,
  })

  // Favourite players — only fetch when signed in
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
      if (isTeamFav) {
        await removeFavoriteTeam(teamId)
      } else {
        await addFavoriteTeam(teamId)
      }
      // Refresh the favourites list so the heart updates to reflect the new state
      queryClient.invalidateQueries({ queryKey: ['favorites', 'teams'] })
    } catch (err) {
      // If the request fails (e.g. expired token, server error), log it so it's
      // visible in the browser console instead of silently doing nothing
      console.error('[TeamDetailPage] toggleTeamFav failed:', err)
    }
  }

  const togglePlayerFav = async (playerId: number) => {
    if (!isAuthenticated) { navigate('/auth'); return }
    try {
      if (favPlayerIds.has(playerId)) {
        await removeFavoritePlayer(playerId)
      } else {
        await addFavoritePlayer(playerId)
      }
      // Refresh the favourites list so the heart updates to reflect the new state
      queryClient.invalidateQueries({ queryKey: ['favorites', 'players'] })
    } catch (err) {
      console.error('[TeamDetailPage] togglePlayerFav failed:', err)
    }
  }

  const grouped = groupByPosition(players)

  return (
    <div className="space-y-4">
      {/* Back */}
      <button
        onClick={handleBack}
        className="flex items-center gap-1 text-sm text-slate-400 hover:text-white"
      >
        <ChevronLeft size={16} /> Back
      </button>

      {/* Team header */}
      {loadingTeam ? (
        <LoadingSpinner />
      ) : team ? (
        <div className="flex items-center gap-4 rounded-2xl bg-slate-800 px-4 py-5">
          {team.crestUrl
            ? <img src={team.crestUrl} alt={team.name} className="h-16 w-16 object-contain" />
            : <div className="flex h-16 w-16 items-center justify-center rounded-full bg-slate-600 text-lg font-bold">{team.shortName.slice(0, 3)}</div>
          }
          <div className="flex-1 space-y-1">
            <h1 className="text-xl font-bold">{team.name}</h1>
            {team.stadium && (
              <p className="flex items-center gap-1 text-xs text-slate-400">
                <MapPin size={12} /> {team.stadium}
              </p>
            )}
            {team.country && (
              <p className="flex items-center gap-1 text-xs text-slate-400">
                <Globe size={12} /> {team.country}
              </p>
            )}
          </div>
          {/* Favourite toggle for team */}
          <button
            onClick={toggleTeamFav}
            className="shrink-0 rounded-full p-2 transition hover:bg-slate-700 active:scale-90"
            aria-label={isTeamFav ? 'Remove from favourites' : 'Add to favourites'}
          >
            <Heart
              size={24}
              className={isTeamFav ? 'fill-red-500 text-red-500' : 'text-slate-400'}
            />
          </button>
        </div>
      ) : null}

      {/* Squad heading */}
      <div className="space-y-1">
        <h2 className="px-1 text-sm font-semibold text-slate-300">
          Squad {players.length > 0 && <span className="text-slate-500">({players.length})</span>}
        </h2>
      </div>

      {loadingPlayers ? (
        <LoadingSpinner />
      ) : players.length === 0 ? (
        <p className="py-8 text-center text-slate-400 text-sm">No squad data available</p>
      ) : (
        POSITION_ORDER
          .filter((pos) => grouped[pos]?.length > 0)
          .map((pos) => (
            <section key={pos} className="space-y-1">
              <h3 className="px-1 text-xs font-semibold uppercase tracking-wider text-slate-500">
                {pos}s
              </h3>
              {/* Responsive grid: 1 col on mobile, 2 on sm+ */}
              <div className="grid grid-cols-1 gap-1 sm:grid-cols-2">
                {grouped[pos].map((player) => {
                  const playerFav = favPlayerIds.has(player.id)
                  return (
                    <div
                      key={player.id}
                      className="flex items-center gap-3 rounded-lg bg-slate-800 px-3 py-2.5"
                    >
                      {/* Name + nationality */}
                      <div className="flex-1 overflow-hidden">
                        <Link
                          to={`/players/${player.id}`}
                          state={player}
                          className="truncate text-sm font-medium hover:text-blue-400 transition-colors"
                        >
                          {player.name}
                        </Link>
                        {player.nationality && (
                          <p className="truncate text-xs text-slate-400">{player.nationality}</p>
                        )}
                      </div>

                      {/* Jersey number */}
                      <span className="shrink-0 text-xs font-bold text-slate-400">
                        {player.jerseyNumber != null ? `#${player.jerseyNumber}` : '—'}
                      </span>

                      {/* Favourite toggle for player */}
                      <button
                        onClick={() => togglePlayerFav(player.id)}
                        className="shrink-0 rounded-full p-1.5 transition hover:bg-slate-700 active:scale-90"
                        aria-label={playerFav ? 'Remove from favourites' : 'Add to favourites'}
                      >
                        <Heart
                          size={14}
                          className={playerFav ? 'fill-red-500 text-red-500' : 'text-slate-600'}
                        />
                      </button>
                    </div>
                  )
                })}
              </div>
            </section>
          ))
      )}
    </div>
  )
}
