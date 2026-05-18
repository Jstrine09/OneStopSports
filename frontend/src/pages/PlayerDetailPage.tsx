import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchPlayer } from '../api/players'
import { getFavoritePlayers, addFavoritePlayer, removeFavoritePlayer } from '../api/auth'
import { useAuth } from '../context/AuthContext'
import LoadingSpinner from '../components/LoadingSpinner'
import { ChevronLeft, Heart, Flag, Cake } from 'lucide-react'
import type { PlayerDto } from '../types'

function calculateAge(dob: string | null): { age: string; display: string } {
  if (!dob) return { age: '—', display: '—' }
  const birth = new Date(dob)
  const age = Math.floor((Date.now() - birth.getTime()) / (365.25 * 24 * 60 * 60 * 1000))
  const formatted = birth.toLocaleDateString([], { day: 'numeric', month: 'long', year: 'numeric' })
  return { age: `${age}`, display: `${formatted} (age ${age})` }
}

// Position tint — used on the position chip. Tints are kept low-chroma so the chip
// reads as a label, not a banner.
const POSITION_COLOURS: Record<string, string> = {
  Goalkeeper: 'bg-amber-500/15 text-amber-400 ring-amber-400/30',
  Defender:   'bg-blue-500/15 text-blue-400 ring-blue-400/30',
  Midfielder: 'bg-green-500/15 text-green-400 ring-green-400/30',
  Forward:    'bg-red-500/15 text-red-400 ring-red-400/30',
}

export default function PlayerDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const { isAuthenticated } = useAuth()

  // Go back one step if there's history, otherwise navigate to the player's team page.
  const handleBack = (resolvedPlayer: typeof player) => {
    if ((window.history.state?.idx ?? 0) > 0) {
      navigate(-1)
    } else if (resolvedPlayer?.teamId) {
      navigate(`/teams/${resolvedPlayer.teamId}`, { replace: true })
    } else {
      navigate('/leagues', { replace: true })
    }
  }
  const queryClient = useQueryClient()
  const playerId = Number(id)

  // Router state for instant load, fall back to API for direct URL access
  const statePlayer = location.state as PlayerDto | null

  const { data: fetchedPlayer, isLoading } = useQuery({
    queryKey: ['player', playerId],
    queryFn: () => fetchPlayer(playerId),
    enabled: !!playerId && !statePlayer,
  })

  const player = statePlayer ?? fetchedPlayer

  const { data: favPlayers = [] } = useQuery({
    queryKey: ['favorites', 'players'],
    queryFn: getFavoritePlayers,
    enabled: isAuthenticated,
    staleTime: 2 * 60_000,
  })

  const isFav = favPlayers.some((p) => p.id === playerId)

  const toggleFav = async () => {
    if (!isAuthenticated) { navigate('/auth'); return }
    if (isFav) await removeFavoritePlayer(playerId)
    else await addFavoritePlayer(playerId)
    queryClient.invalidateQueries({ queryKey: ['favorites', 'players'] })
  }

  if (isLoading && !statePlayer) return <LoadingSpinner />

  if (!player) {
    return (
      <div className="py-16 text-center text-slate-500 dark:text-zinc-500">
        <p>Player not found.</p>
        <button onClick={() => handleBack(undefined)} className="mt-4 text-amber-400 underline">Go back</button>
      </div>
    )
  }

  const { display: dobDisplay } = calculateAge(player.dateOfBirth)
  const posColour = POSITION_COLOURS[player.position ?? ''] ?? 'bg-slate-100 text-slate-500 ring-slate-300 dark:bg-zinc-800 dark:text-zinc-400 dark:ring-zinc-700'

  return (
    <div className="space-y-5">
      {/* Back */}
      <button
        onClick={() => handleBack(player)}
        className="flex min-h-[44px] items-center gap-1 py-2 text-sm text-slate-500 transition-colors hover:text-slate-900 dark:text-zinc-500 dark:hover:text-zinc-100"
      >
        <ChevronLeft size={16} /> Back
      </button>

      {/* Player identity card.
          Jersey number is the dominant typographic element — bigger than the name,
          tabular and extrabold. Reads like a roster card or a stadium scoreboard
          slot. Name + meta sit beside it in a quieter hierarchy. */}
      <section className="relative overflow-hidden rounded-3xl border border-slate-200 bg-white px-5 py-6 dark:border-zinc-900 dark:bg-zinc-900/60 sm:px-7 sm:py-8">
        {/* Soft radial wash for depth without using glassmorphism or gradient text */}
        <div className="pointer-events-none absolute -right-12 -top-16 h-56 w-56 rounded-full bg-amber-500/[0.04] blur-3xl" />

        <div className="relative flex items-center gap-5 sm:gap-7">
          {/* Jersey number — the player's signature */}
          <div className="flex h-24 w-24 shrink-0 items-center justify-center rounded-2xl bg-slate-100 dark:bg-zinc-800 sm:h-28 sm:w-28">
            <span className="text-5xl font-black tabular-nums leading-none tracking-tight text-slate-900 dark:text-zinc-100 sm:text-6xl">
              {player.jerseyNumber ?? '–'}
            </span>
          </div>

          {/* Name + position + meta */}
          <div className="min-w-0 flex-1 space-y-2.5">
            <h1 className="truncate text-2xl font-extrabold tracking-tight sm:text-3xl">
              {player.name}
            </h1>

            {player.position && (
              <span className={`inline-block rounded-full px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-wider ring-1 ${posColour}`}>
                {player.position}
              </span>
            )}

            <div className="space-y-1 pt-1">
              {player.nationality && (
                <p className="flex items-center gap-2 text-xs text-slate-500 dark:text-zinc-500">
                  <Flag size={12} className="opacity-60" />
                  {player.nationality}
                </p>
              )}
              {player.dateOfBirth && (
                <p className="flex items-center gap-2 text-xs text-slate-500 dark:text-zinc-500">
                  <Cake size={12} className="opacity-60" />
                  {dobDisplay}
                </p>
              )}
            </div>
          </div>

          <button
            onClick={toggleFav}
            className="absolute right-0 top-0 flex min-h-[44px] min-w-[44px] items-center justify-center rounded-full transition active:scale-90 hover:bg-slate-100 dark:hover:bg-zinc-800"
            aria-label={isFav ? 'Remove from favourites' : 'Add to favourites'}
          >
            <Heart
              size={22}
              className={isFav ? 'fill-red-500 text-red-500' : 'text-slate-400 dark:text-zinc-600'}
            />
          </button>
        </div>
      </section>

      {/* Season stats placeholder — kept honest with the empty state */}
      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/40">
        <header className="border-b border-slate-100 px-4 py-3 dark:border-zinc-900">
          <h2 className="text-[11px] font-bold uppercase tracking-[0.12em] text-slate-500 dark:text-zinc-400">Season Stats</h2>
        </header>
        <div className="flex flex-col items-center gap-1 py-8 text-slate-400 dark:text-zinc-600">
          <span className="text-2xl">📊</span>
          <p className="text-xs">Coming soon</p>
        </div>
      </section>
    </div>
  )
}
