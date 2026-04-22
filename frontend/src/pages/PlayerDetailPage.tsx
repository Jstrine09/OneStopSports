import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchPlayer } from '../api/players'
import { getFavoritePlayers, addFavoritePlayer, removeFavoritePlayer } from '../api/auth'
import { useAuth } from '../context/AuthContext'
import LoadingSpinner from '../components/LoadingSpinner'
import { ChevronLeft, Heart, MapPin, Flag } from 'lucide-react'
import type { PlayerDto } from '../types'

function calculateAge(dob: string | null): { age: string; display: string } {
  if (!dob) return { age: '—', display: '—' }
  const birth = new Date(dob)
  const age = Math.floor((Date.now() - birth.getTime()) / (365.25 * 24 * 60 * 60 * 1000))
  const formatted = birth.toLocaleDateString([], { day: 'numeric', month: 'long', year: 'numeric' })
  return { age: `${age}`, display: `${formatted} (age ${age})` }
}

const POSITION_COLOURS: Record<string, string> = {
  Goalkeeper: 'bg-amber-500/20 text-amber-400',
  Defender:   'bg-blue-500/20 text-blue-400',
  Midfielder: 'bg-green-500/20 text-green-400',
  Forward:    'bg-red-500/20 text-red-400',
}

export default function PlayerDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const { isAuthenticated } = useAuth()
  const queryClient = useQueryClient()
  const playerId = Number(id)

  // Use router state for instant load, fall back to API for direct URL access
  const statePlayer = location.state as PlayerDto | null

  const { data: fetchedPlayer, isLoading } = useQuery({
    queryKey: ['player', playerId],
    queryFn: () => fetchPlayer(playerId),
    enabled: !!playerId && !statePlayer,
  })

  const player = statePlayer ?? fetchedPlayer

  // Favourite players
  const { data: favPlayers = [] } = useQuery({
    queryKey: ['favorites', 'players'],
    queryFn: getFavoritePlayers,
    enabled: isAuthenticated,
    staleTime: 2 * 60_000,
  })

  const isFav = favPlayers.some((p) => p.id === playerId)

  const toggleFav = async () => {
    if (!isAuthenticated) { navigate('/auth'); return }
    if (isFav) {
      await removeFavoritePlayer(playerId)
    } else {
      await addFavoritePlayer(playerId)
    }
    queryClient.invalidateQueries({ queryKey: ['favorites', 'players'] })
  }

  if (isLoading && !statePlayer) return <LoadingSpinner />

  if (!player) {
    return (
      <div className="py-16 text-center text-slate-400">
        <p>Player not found.</p>
        <button onClick={() => navigate(-1)} className="mt-4 text-blue-400 underline">Go back</button>
      </div>
    )
  }

  const { display: dobDisplay } = calculateAge(player.dateOfBirth)
  const posColour = POSITION_COLOURS[player.position ?? ''] ?? 'bg-slate-700 text-slate-300'

  return (
    <div className="space-y-4">
      {/* Back */}
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-1 text-sm text-slate-400 hover:text-white"
      >
        <ChevronLeft size={16} /> Back
      </button>

      {/* Player hero card */}
      <div className="rounded-2xl bg-slate-800 px-5 py-6">
        <div className="flex items-start justify-between gap-4">
          {/* Jersey number circle */}
          <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-full bg-slate-700 text-3xl font-extrabold text-white">
            {player.jerseyNumber ?? '?'}
          </div>

          {/* Name + position + nationality */}
          <div className="flex-1 space-y-2">
            <h1 className="text-2xl font-bold leading-tight">{player.name}</h1>

            {player.position && (
              <span className={`inline-block rounded-full px-3 py-0.5 text-xs font-semibold ${posColour}`}>
                {player.position}
              </span>
            )}

            <div className="space-y-1 pt-1">
              {player.nationality && (
                <p className="flex items-center gap-2 text-sm text-slate-400">
                  <Flag size={14} className="shrink-0" />
                  {player.nationality}
                </p>
              )}
              {player.dateOfBirth && (
                <p className="flex items-center gap-2 text-sm text-slate-400">
                  <MapPin size={14} className="shrink-0 opacity-0" />{/* spacer */}
                  {dobDisplay}
                </p>
              )}
            </div>
          </div>

          {/* Favourite button */}
          <button
            onClick={toggleFav}
            className="shrink-0 rounded-full p-2 transition hover:bg-slate-700 active:scale-90"
            aria-label={isFav ? 'Remove from favourites' : 'Add to favourites'}
          >
            <Heart
              size={26}
              className={isFav ? 'fill-red-500 text-red-500' : 'text-slate-400'}
            />
          </button>
        </div>
      </div>

      {/* Stats placeholder — future expansion */}
      <div className="rounded-2xl bg-slate-800 px-4 py-5">
        <h2 className="mb-3 text-sm font-semibold text-slate-300">Season Stats</h2>
        <div className="flex flex-col items-center gap-1 py-4 text-slate-500">
          <span className="text-2xl">📊</span>
          <p className="text-xs">Stats coming soon</p>
        </div>
      </div>
    </div>
  )
}
