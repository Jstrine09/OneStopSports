import { useEffect, useState } from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchPlayer, fetchPlayerBio, fetchPlayerCareerStats } from '../api/players'
import { getFavoritePlayers, addFavoritePlayer, removeFavoritePlayer } from '../api/auth'
import { useAuth } from '../context/AuthContext'
import LoadingSpinner from '../components/LoadingSpinner'
import CareerStatsTable from '../components/CareerStatsTable'
import { ChevronLeft, Heart, Flag, Cake, Ruler, Weight, GraduationCap, Award } from 'lucide-react'
import type { PlayerDto } from '../types'

function calculateAge(dob: string | null): { age: string; display: string } {
  if (!dob) return { age: '—', display: '—' }
  const birth = new Date(dob)
  const age = Math.floor((Date.now() - birth.getTime()) / (365.25 * 24 * 60 * 60 * 1000))
  const formatted = birth.toLocaleDateString([], { day: 'numeric', month: 'long', year: 'numeric' })
  return { age: `${age}`, display: `${formatted} (age ${age})` }
}

// Derives a player's initials for the fallback avatar tile: first + last name initial
// (e.g. "Bukayo Saka" → "BS"), or the first two letters for a single-word name. Used
// whenever there's no headshot to show, so the tile reads as *that player*, not a blank.
function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

// Deterministic tint palette for the fallback avatar. We hash the player's name into a
// fixed slot so a given player always gets the same colour across renders and revisits
// (rather than flickering between colours). Each entry ships light + dark variants.
const AVATAR_TINTS = [
  'bg-amber-100  text-amber-800  dark:bg-amber-500/20  dark:text-amber-300',
  'bg-blue-100   text-blue-800   dark:bg-blue-500/20   dark:text-blue-300',
  'bg-green-100  text-green-800  dark:bg-green-500/20  dark:text-green-300',
  'bg-rose-100   text-rose-800   dark:bg-rose-500/20   dark:text-rose-300',
  'bg-violet-100 text-violet-800 dark:bg-violet-500/20 dark:text-violet-300',
  'bg-teal-100   text-teal-800   dark:bg-teal-500/20   dark:text-teal-300',
]
function tintFor(name: string): string {
  // Simple deterministic string hash (same idea as Java's String.hashCode) → palette index.
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) | 0
  return AVATAR_TINTS[Math.abs(hash) % AVATAR_TINTS.length]
}

// Position tint — used on the position chip. Tints are kept low-chroma so the chip
// reads as a label, not a banner. Each chip has both light and dark variants so
// it stays legible regardless of theme.
const POSITION_COLOURS: Record<string, string> = {
  Goalkeeper: 'bg-amber-100 text-amber-700 ring-amber-500/30 dark:bg-amber-500/15 dark:text-amber-400 dark:ring-amber-400/30',
  Defender:   'bg-blue-100  text-blue-700  ring-blue-500/30  dark:bg-blue-500/15  dark:text-blue-400  dark:ring-blue-400/30',
  Midfielder: 'bg-green-100 text-green-700 ring-green-500/30 dark:bg-green-500/15 dark:text-green-400 dark:ring-green-400/30',
  Forward:    'bg-red-100   text-red-700   ring-red-500/30   dark:bg-red-500/15   dark:text-red-400   dark:ring-red-400/30',
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

  // Tracks whether the headshot <img> failed to load (CDN 404, bad derived URL, offline).
  // When true we fall through to the initials tile instead of leaving a blank/broken image.
  // Reset whenever the photo URL changes so navigating between players re-tries cleanly.
  const [photoFailed, setPhotoFailed] = useState(false)
  useEffect(() => { setPhotoFailed(false) }, [player?.photoUrl])

  // Bio enrichment from balldontlie.io — NBA players only.
  // We always fire this query; it returns null for non-NBA players (204 from backend).
  // staleTime is long — bio data almost never changes.
  const { data: bio = null } = useQuery({
    queryKey: ['player-bio', playerId],
    queryFn: () => fetchPlayerBio(playerId),
    enabled: !!playerId,
    staleTime: 24 * 60 * 60_000, // 24 hours — player bio doesn't change often
    retry: false,                 // Don't hammer the API if balldontlie is down
  })

  // Career stats — ESPN for NBA/NFL, API-Football for soccer (Phase 6, not live yet).
  // Returns null for players whose backend route returns 204 (no externalId, no upstream
  // record, or unsupported sport). The component shows a placeholder in that case.
  // 24h staleTime: career stats only update once a day at most.
  const { data: careerStats = null, isLoading: loadingStats } = useQuery({
    queryKey: ['player-career-stats', playerId],
    queryFn: () => fetchPlayerCareerStats(playerId),
    enabled: !!playerId,
    staleTime: 24 * 60 * 60_000,
    retry: false,
  })

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
      <div className="py-16 text-center text-stone-500 dark:text-zinc-500">
        <p>Player not found.</p>
        <button onClick={() => handleBack(undefined)} className="mt-4 text-amber-600 underline dark:text-amber-400">Go back</button>
      </div>
    )
  }

  const { display: dobDisplay } = calculateAge(player.dateOfBirth)
  const posColour = POSITION_COLOURS[player.position ?? ''] ?? 'bg-stone-100 text-stone-600 ring-stone-300 dark:bg-zinc-800 dark:text-zinc-400 dark:ring-zinc-700'

  return (
    <div className="space-y-5">
      {/* Back */}
      <button
        onClick={() => handleBack(player)}
        className="flex min-h-[44px] items-center gap-1 py-2 text-sm text-stone-500 transition-colors hover:text-stone-900 dark:text-zinc-500 dark:hover:text-zinc-100"
      >
        <ChevronLeft size={16} /> Back
      </button>

      {/* Player identity card.
          Jersey number is the dominant typographic element — bigger than the name,
          tabular and extrabold. Reads like a roster card or a stadium scoreboard
          slot. Name + meta sit beside it in a quieter hierarchy. */}
      <section className="relative overflow-hidden rounded-3xl border border-stone-200 bg-white px-5 py-6 dark:border-zinc-900 dark:bg-zinc-900/60 sm:px-7 sm:py-8">
        {/* Soft radial wash for depth without using glassmorphism or gradient text */}
        <div className="pointer-events-none absolute -right-12 -top-16 h-56 w-56 rounded-full bg-amber-500/[0.04] blur-3xl" />

        <div className="relative flex items-center gap-5 sm:gap-7">
          {/* Identity tile — headshot when we have one that loads, an initials tile as the
              graceful fallback otherwise. The jersey number rides along as a small badge
              anchored bottom-right in both cases, so the tile stays the dominant element
              (Fotmob-style). Tile dimensions stay constant (h-24/w-24 → h-28/w-28) so the
              rest of the layout never shifts between the two states.

              showPhoto is false when the player has no photoUrl (many footballers, until
              their api-sports ID is captured) OR when the derived CDN image 404s — either
              way the user sees the player's initials on a coloured tile, never a blank box. */}
          <div className="relative h-24 w-24 shrink-0 sm:h-28 sm:w-28">
            {player.photoUrl && !photoFailed ? (
              <img
                src={player.photoUrl}
                alt={player.name}
                // Headshots are PNGs with transparent backgrounds — the stone/zinc tile
                // underneath provides visual grounding so the head isn't floating.
                // object-cover crops to fill; object-top keeps faces high in the frame.
                className="h-full w-full rounded-2xl bg-stone-100 object-cover object-top dark:bg-zinc-800"
                // On load failure flip to the initials fallback (see photoFailed above)
                // instead of leaving a broken/blank image behind.
                onError={() => setPhotoFailed(true)}
              />
            ) : (
              // Fallback avatar: the player's initials on a deterministic coloured tile.
              <div className={`flex h-full w-full items-center justify-center rounded-2xl ${tintFor(player.name)}`}>
                <span className="text-3xl font-black uppercase leading-none tracking-tight sm:text-4xl">
                  {initialsOf(player.name)}
                </span>
              </div>
            )}
            {player.jerseyNumber != null && (
              <span className="absolute -bottom-1 -right-1 flex h-7 min-w-[28px] items-center justify-center rounded-full bg-stone-900 px-1.5 text-xs font-extrabold tabular-nums text-white ring-2 ring-white dark:bg-zinc-100 dark:text-zinc-900 dark:ring-zinc-900 sm:h-8 sm:min-w-[32px] sm:text-sm">
                {player.jerseyNumber}
              </span>
            )}
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
                <p className="flex items-center gap-2 text-xs text-stone-500 dark:text-zinc-500">
                  <Flag size={12} className="opacity-60" />
                  {player.nationality}
                </p>
              )}
              {player.dateOfBirth && (
                <p className="flex items-center gap-2 text-xs text-stone-500 dark:text-zinc-500">
                  <Cake size={12} className="opacity-60" />
                  {dobDisplay}
                </p>
              )}
            </div>
          </div>

          <button
            onClick={toggleFav}
            className="absolute right-0 top-0 flex min-h-[44px] min-w-[44px] items-center justify-center rounded-full transition active:scale-90 hover:bg-stone-100 dark:hover:bg-zinc-800"
            aria-label={isFav ? 'Remove from favourites' : 'Add to favourites'}
          >
            <Heart
              size={22}
              className={isFav ? 'fill-red-500 text-red-500' : 'text-stone-400 dark:text-zinc-600'}
            />
          </button>
        </div>
      </section>

      {/* Bio card — only rendered for NBA players where balldontlie has data.
          For football / NFL players the backend returns 204, bio stays null,
          and this whole section is skipped cleanly. */}
      {bio && (
        <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/40">
          <header className="border-b border-stone-100 px-4 py-3 dark:border-zinc-900">
            <h2 className="text-[11px] font-bold uppercase tracking-[0.12em] text-stone-500 dark:text-zinc-400">
              Bio
            </h2>
          </header>
          {/* Always render four balanced cells (— for anything missing) so the
              divided grid stays even: 2×2 on mobile, 1×4 on sm+. */}
          <div className="grid grid-cols-2 divide-x divide-y divide-stone-100 dark:divide-zinc-900 sm:grid-cols-4 sm:divide-y-0">
            <div className="flex flex-col items-center gap-1 px-4 py-4">
              <Ruler size={14} className="text-stone-400 dark:text-zinc-500" />
              <span className="text-base font-bold tabular-nums">{bio.height ?? '—'}</span>
              <span className="text-[10px] uppercase tracking-wider text-stone-400 dark:text-zinc-500">Height</span>
            </div>
            <div className="flex flex-col items-center gap-1 px-4 py-4">
              <Weight size={14} className="text-stone-400 dark:text-zinc-500" />
              <span className="text-base font-bold tabular-nums">{bio.weightPounds ? `${bio.weightPounds} lbs` : '—'}</span>
              <span className="text-[10px] uppercase tracking-wider text-stone-400 dark:text-zinc-500">Weight</span>
            </div>
            <div className="flex flex-col items-center gap-1 px-4 py-4">
              <GraduationCap size={14} className="text-stone-400 dark:text-zinc-500" />
              <span className="text-center text-sm font-semibold leading-tight">{bio.college ?? '—'}</span>
              <span className="text-[10px] uppercase tracking-wider text-stone-400 dark:text-zinc-500">College</span>
            </div>
            <div className="flex flex-col items-center gap-1 px-4 py-4">
              <Award size={14} className="text-stone-400 dark:text-zinc-500" />
              <span className="text-base font-bold tabular-nums">{bio.draftYear ?? '—'}</span>
              <span className="text-[10px] uppercase tracking-wider text-stone-400 dark:text-zinc-500">
                {bio.draftYear && bio.draftRound && bio.draftNumber
                  ? `Rd ${bio.draftRound} · Pick ${bio.draftNumber}`
                  : 'Draft'}
              </span>
            </div>
          </div>
        </section>
      )}

      {/* Career stats — three paths:
            1. Stats arrived → render the full table (NBA/NFL via ESPN, football via API-Football)
            2. Still loading → show the spinner inside the same section frame
            3. 204 / null → show a polite empty state so the page still looks intentional
                          (football players currently land here until Phase 6 ships) */}
      {careerStats ? (
        <CareerStatsTable stats={careerStats} />
      ) : loadingStats ? (
        <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white py-8 dark:border-zinc-900 dark:bg-zinc-900/40">
          <LoadingSpinner />
        </section>
      ) : (
        <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/40">
          <header className="border-b border-stone-100 px-4 py-3 dark:border-zinc-900">
            <h2 className="text-[11px] font-bold uppercase tracking-[0.12em] text-stone-500 dark:text-zinc-400">Career Stats</h2>
          </header>
          <div className="flex flex-col items-center gap-1 py-8 text-stone-400 dark:text-zinc-600">
            <span className="text-2xl">📊</span>
            <p className="text-xs">Not available for this player</p>
          </div>
        </section>
      )}
    </div>
  )
}
