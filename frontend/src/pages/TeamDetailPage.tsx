import { useState } from 'react'
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchTeam, fetchTeamPlayers } from '../api/teams'
import {
  getFavoriteTeams, addFavoriteTeam, removeFavoriteTeam,
  getFavoritePlayers, addFavoritePlayer, removeFavoritePlayer,
} from '../api/auth'
import { useAuth } from '../context/AuthContext'
import LoadingSpinner from '../components/LoadingSpinner'
import SectionLabel from '../components/SectionLabel'
import SportFieldBackdrop, { fieldVariantForSport } from '../components/SportFieldBackdrop'
import { getLeagueTheme } from '../lib/leagueTheme'
import { ChevronLeft, MapPin, Globe, Heart } from 'lucide-react'
import type { PlayerDto } from '../types'

// Position groups — covers football (soccer), basketball, and American football (NFL).
// football-data.org returns granular positions (Centre-Back, Attacking Midfield, etc.)
// rather than generic buckets, so we list them all explicitly.
// Anything that doesn't match falls into the 'Other' bucket at the bottom.
const POSITION_ORDER = [
  // Football (soccer) — generic buckets used by some leagues / youth squads
  'Goalkeeper',
  // Defenders — broad bucket first, then specific roles
  'Defender', 'Centre-Back', 'Right-Back', 'Left-Back', 'Defence',
  // Midfielders
  'Midfielder', 'Defensive Midfield', 'Central Midfield', 'Attacking Midfield', 'Midfield',
  // Wingers sit between midfield and attack
  'Right Winger', 'Left Winger',
  // Forwards
  'Forward', 'Centre-Forward', 'Offence',
  // Basketball
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

// Section header labels — simple "{pos}s" doesn't work for hyphenated positions,
// compound words ending in a consonant cluster, or generic youth categories.
const POSITION_LABEL: Record<string, string> = {
  'Goalkeeper':         'Goalkeepers',
  'Defender':           'Defenders',
  'Centre-Back':        'Centre-Backs',
  'Right-Back':         'Right-Backs',
  'Left-Back':          'Left-Backs',
  'Defence':            'Defenders',            // generic youth bucket from football-data.org
  'Midfielder':         'Midfielders',
  'Defensive Midfield': 'Defensive Midfielders',
  'Central Midfield':   'Central Midfielders',
  'Attacking Midfield': 'Attacking Midfielders',
  'Midfield':           'Midfielders',          // generic youth bucket
  'Right Winger':       'Right Wingers',
  'Left Winger':        'Left Wingers',
  'Forward':            'Forwards',
  'Centre-Forward':     'Centre-Forwards',
  'Offence':            'Forwards',             // generic youth bucket
  'Guard':              'Guards',
  'Guard-Forward':      'Guard-Forwards',
  'Forward-Center':     'Forward-Centers',
  'Center':             'Centers',
  'Quarterback':        'Quarterbacks',
  'Running Back':       'Running Backs',
  'Fullback':           'Fullbacks',
  'Wide Receiver':      'Wide Receivers',
  'Tight End':          'Tight Ends',
  'Offensive Tackle':   'Offensive Tackles',
  'Offensive Guard':    'Offensive Guards',
  'Defensive End':      'Defensive Ends',
  'Defensive Tackle':   'Defensive Tackles',
  'Linebacker':         'Linebackers',
  'Outside Linebacker': 'Outside Linebackers',
  'Inside Linebacker':  'Inside Linebackers',
  'Middle Linebacker':  'Middle Linebackers',
  'Cornerback':         'Cornerbacks',
  'Safety':             'Safeties',
  'Free Safety':        'Free Safeties',
  'Strong Safety':      'Strong Safeties',
  'Kicker':             'Kickers',
  'Punter':             'Punters',
  'Long Snapper':       'Long Snappers',
  'Other':              'Other',
}

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

  // selectedSeason: null = current roster from DB; number = historical season from ESPN.
  // Season = start year of the season — e.g. 2022 for "2022-23" (ESPN convention for NBA).
  // For NFL the season is a single year (e.g. 2024).
  const [selectedSeason, setSelectedSeason] = useState<number | null>(null)

  // Build the year list once: current year down to 2000.
  // Only the current year through 2000 — ESPN roster data doesn't reliably go further back.
  const currentYear = new Date().getFullYear()
  const seasonYears = Array.from({ length: currentYear - 1999 }, (_, i) => currentYear - i)

  const { data: team, isLoading: loadingTeam } = useQuery({
    queryKey: ['team', teamId],
    queryFn: () => fetchTeam(teamId),
    enabled: !!teamId,
  })

  const { data: players = [], isLoading: loadingPlayers } = useQuery({
    // Include selectedSeason in the query key so changing the season fetches fresh data
    // and each season's result is cached independently (staleTime: 24h for historical).
    queryKey: ['team-players', teamId, selectedSeason],
    queryFn: () => fetchTeamPlayers(teamId, selectedSeason ?? undefined),
    enabled: !!teamId,
    // Historical rosters never change — cache for 24h. Current roster refreshes every 5m.
    staleTime: selectedSeason != null ? 24 * 60 * 60_000 : 5 * 60_000,
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

  // Sport field for the header. We only reliably know the sport when the user
  // arrived from the Leagues page (sportSlug is passed in router state); on a
  // direct URL or search hit we fall back to the soccer pitch. Theme colour comes
  // from the same slug (NBA orange / NFL indigo / default amber).
  const fieldTheme = getLeagueTheme(undefined, fromState?.sportSlug)
  const fieldVariant = fieldVariantForSport(fromState?.sportSlug)

  return (
    <div className="space-y-5">
      {/* Back */}
      <button
        onClick={handleBack}
        className="flex min-h-[44px] items-center gap-1 py-2 text-sm text-stone-500 transition-colors hover:text-stone-900 dark:text-zinc-500 dark:hover:text-zinc-100"
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
        <header className="relative overflow-hidden rounded-3xl border border-stone-200 bg-white px-6 py-7 dark:border-zinc-900 dark:bg-zinc-900/60">
          {/* Sport field behind the crest, themed to the sport. Subtle so the team
              identity stays dominant; hidden on phones. */}
          <SportFieldBackdrop
            colorClass={fieldTheme.text}
            variant={fieldVariant}
            intensity="subtle"
            className="hidden md:block"
          />

          <div className="relative flex items-center gap-5">
            {team.crestUrl ? (
              <img src={team.crestUrl} alt={team.name} className="h-20 w-20 shrink-0 object-contain sm:h-24 sm:w-24" />
            ) : (
              <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-2xl bg-stone-200 text-2xl font-extrabold dark:bg-zinc-800 dark:text-zinc-300 sm:h-24 sm:w-24">
                {team.shortName.slice(0, 3)}
              </div>
            )}

            <div className="min-w-0 flex-1 space-y-2">
              <h1 className="truncate text-2xl font-extrabold tracking-tight sm:text-3xl">{team.name}</h1>
              <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-stone-500 dark:text-zinc-500">
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
              className="absolute right-0 top-0 flex min-h-[44px] min-w-[44px] items-center justify-center rounded-full transition active:scale-90 hover:bg-stone-100 dark:hover:bg-zinc-800"
              aria-label={isTeamFav ? 'Remove from favourites' : 'Add to favourites'}
            >
              <Heart
                size={22}
                className={isTeamFav ? 'fill-red-500 text-red-500' : 'text-stone-400 dark:text-zinc-600'}
              />
            </button>
          </div>
        </header>
      ) : null}

      {/* Squad section — like a printed team sheet rather than a card collection.
          Position groups stack as sections with tight uppercase labels, players
          sit as flat rows inside a single rounded surface per group. */}
      <section>
        <div className="mb-3 flex items-center justify-between gap-3 px-1">
          <div className="flex items-baseline gap-3">
            <SectionLabel>Squad</SectionLabel>
            {players.length > 0 && (
              <span className="text-xs tabular-nums text-stone-400 dark:text-zinc-600">{players.length} players</span>
            )}
          </div>

          {/* Season picker — lets users browse historical rosters via ESPN's API.
              Works for NBA and NFL only (football-data.org free tier has no historical roster endpoint).
              Selecting a season triggers a new query with ?season=YYYY on the backend.
              "Current Roster" (null) reverts to the live DB roster. */}
          <select
            value={selectedSeason ?? ''}
            onChange={(e) => setSelectedSeason(e.target.value === '' ? null : Number(e.target.value))}
            className="shrink-0 rounded-lg border border-stone-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-stone-600 transition focus:outline-none focus:ring-2 focus:ring-amber-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-300"
            aria-label="Select season"
          >
            <option value="">Current Roster</option>
            {seasonYears.map((year) => (
              <option key={year} value={year}>
                {year}–{String(year + 1).slice(2)} Season
              </option>
            ))}
          </select>
        </div>

        {loadingPlayers ? (
          <LoadingSpinner />
        ) : players.length === 0 ? (
          <p className="py-8 text-center text-sm text-stone-500 dark:text-zinc-500">
            {selectedSeason != null
              ? 'No roster data available for this season. Try another year, or only NBA and NFL teams have historical roster support.'
              : 'No squad data available'}
          </p>
        ) : (
          <div className="space-y-4">
            {POSITION_ORDER
              .filter((pos) => grouped[pos]?.length > 0)
              .map((pos) => (
                <div key={pos}>
                  <h3 className="mb-1.5 px-1 text-[10px] font-bold uppercase tracking-[0.14em] text-stone-400 dark:text-zinc-600">
                    {POSITION_LABEL[pos] ?? `${pos}s`} <span className="ml-1 opacity-60">· {grouped[pos].length}</span>
                  </h3>
                  <div className="overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/60">
                    {grouped[pos].map((player, idx) => {
                      // Historical players have null id — they can't be favourited or navigated to.
                      // Current-roster players always have an id.
                      const isHistorical = player.id == null
                      const playerFav = !isHistorical && favPlayerIds.has(player.id!)
                      // Use index as fallback key when id is null (historical season).
                      // This is safe because the list is re-fetched fresh on each season change.
                      const rowKey = player.id ?? `hist-${idx}`
                      return (
                        <div
                          key={rowKey}
                          className="flex items-center gap-3 border-t border-stone-100 px-4 py-2.5 first:border-0 transition-colors hover:bg-stone-50 dark:border-zinc-900 dark:hover:bg-zinc-800/40"
                        >
                          {/* Jersey number — small but bold, the player's identifier */}
                          <span className="w-8 shrink-0 text-center text-xs font-extrabold tabular-nums text-stone-400 dark:text-zinc-500">
                            {player.jerseyNumber != null ? player.jerseyNumber : '—'}
                          </span>

                          {/* Headshot avatar — only rendered when a photo URL exists.
                              Sized to match the row height (~36px including padding) so the
                              row chrome stays consistent. NBA/NFL get this for free via ESPN's
                              CDN; football players will get it once their stats page has been
                              visited (lazy capture from API-Football). */}
                          {player.photoUrl && (
                            <img
                              src={player.photoUrl}
                              alt=""
                              aria-hidden="true"
                              className="h-9 w-9 shrink-0 rounded-full bg-stone-100 object-cover object-top dark:bg-zinc-800"
                              // Broken CDN URL → hide the avatar rather than show a broken-image icon
                              onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none' }}
                            />
                          )}

                          {/* Name + nationality.
                              Current-season players: clickable Link → PlayerDetailPage.
                              Historical players (id=null): plain text — no DB record to navigate to. */}
                          <div className="flex-1 overflow-hidden">
                            {isHistorical ? (
                              <p className="truncate text-sm font-semibold">{player.name}</p>
                            ) : (
                              <Link
                                to={`/players/${player.id}`}
                                state={player}
                                className="truncate text-sm font-semibold transition-colors hover:text-amber-600 dark:hover:text-amber-400"
                              >
                                {player.name}
                              </Link>
                            )}
                            {player.nationality && (
                              <p className="truncate text-xs text-stone-500 dark:text-zinc-500">{player.nationality}</p>
                            )}
                          </div>

                          {/* Favourite toggle — hidden for historical players (no DB id to store) */}
                          {!isHistorical && (
                            <button
                              onClick={() => togglePlayerFav(player.id!)}
                              className="shrink-0 rounded-full p-1.5 transition active:scale-90 hover:bg-stone-100 dark:hover:bg-zinc-800"
                              aria-label={playerFav ? 'Remove from favourites' : 'Add to favourites'}
                            >
                              <Heart
                                size={14}
                                className={playerFav ? 'fill-red-500 text-red-500' : 'text-stone-300 dark:text-zinc-700'}
                              />
                            </button>
                          )}
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
