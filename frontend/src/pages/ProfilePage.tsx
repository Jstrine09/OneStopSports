import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext'
import {
  getFavoriteTeams, removeFavoriteTeam,
  getFavoritePlayers, removeFavoritePlayer,
} from '../api/auth'
import LoadingSpinner from '../components/LoadingSpinner'
import SectionLabel from '../components/SectionLabel'
import RowCard, { ROW_DIVIDER } from '../components/RowCard'
import { LogOut, User, Heart, Star } from 'lucide-react'

export default function ProfilePage() {
  const { isAuthenticated, username, logout } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: favTeams = [], isLoading: loadingTeams } = useQuery({
    queryKey: ['favorites', 'teams'],
    queryFn: getFavoriteTeams,
    enabled: isAuthenticated,
    staleTime: 2 * 60_000,
  })

  const { data: favPlayers = [], isLoading: loadingPlayers } = useQuery({
    queryKey: ['favorites', 'players'],
    queryFn: getFavoritePlayers,
    enabled: isAuthenticated,
    staleTime: 2 * 60_000,
  })

  const handleRemoveTeam = async (teamId: number) => {
    await removeFavoriteTeam(teamId)
    queryClient.invalidateQueries({ queryKey: ['favorites', 'teams'] })
  }

  const handleRemovePlayer = async (playerId: number) => {
    await removeFavoritePlayer(playerId)
    queryClient.invalidateQueries({ queryKey: ['favorites', 'players'] })
  }

  // Signed-out state — invite the user to sign in.
  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center gap-4 py-16">
        <User size={48} className="text-stone-400 dark:text-zinc-600" />
        <p className="text-sm text-stone-500 dark:text-zinc-400">Sign in to save your favourite teams and players</p>
        <button
          onClick={() => navigate('/auth')}
          className="rounded-lg bg-amber-500 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-amber-600 active:scale-[0.98]"
        >
          Sign in
        </button>
      </div>
    )
  }

  // A small empty-state line styled like the other cards.
  const empty = (text: string) => (
    <p className="rounded-2xl border border-stone-200 bg-white py-6 text-center text-sm text-stone-500 dark:border-zinc-900 dark:bg-zinc-900/60 dark:text-zinc-400">
      {text}
    </p>
  )

  return (
    <div className="space-y-5">
      {/* Identity header — avatar (username initial) + name + a quick follow count. */}
      <div className="flex items-center gap-4">
        <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-amber-100 text-xl font-black text-amber-700 dark:bg-amber-500/15 dark:text-amber-400">
          {(username ?? '?').slice(0, 1).toUpperCase()}
        </div>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-2xl font-extrabold tracking-tight">{username}</h1>
          <p className="text-xs text-stone-500 dark:text-zinc-500">
            {favTeams.length} {favTeams.length === 1 ? 'team' : 'teams'} · {favPlayers.length}{' '}
            {favPlayers.length === 1 ? 'player' : 'players'} followed
          </p>
        </div>
        <button
          onClick={() => { logout(); navigate('/') }}
          aria-label="Sign out"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-stone-400 transition hover:bg-stone-100 hover:text-red-500 dark:text-zinc-500 dark:hover:bg-zinc-800"
        >
          <LogOut size={18} />
        </button>
      </div>

      {/* Favourite Teams */}
      <section className="space-y-2">
        <SectionLabel>
          <Star size={14} className="text-amber-500" />
          Favourite Teams
        </SectionLabel>

        {loadingTeams ? (
          <LoadingSpinner />
        ) : favTeams.length === 0 ? (
          empty('No favourite teams yet — browse Leagues to add some')
        ) : (
          <RowCard>
            {favTeams.map((team) => (
              <div
                key={team.id}
                className={`flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-stone-50 dark:hover:bg-zinc-800/40 ${ROW_DIVIDER}`}
              >
                <Link to={`/teams/${team.id}`} className="flex flex-1 items-center gap-3 overflow-hidden">
                  {team.crestUrl ? (
                    <img src={team.crestUrl} alt={team.name} className="h-8 w-8 shrink-0 object-contain" />
                  ) : (
                    <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-stone-200 text-[10px] font-bold text-stone-600 dark:bg-zinc-700 dark:text-zinc-300">
                      {team.shortName.slice(0, 3)}
                    </div>
                  )}
                  <span className="truncate text-sm font-semibold">{team.name}</span>
                </Link>
                <button
                  onClick={() => handleRemoveTeam(team.id)}
                  aria-label="Remove from favourites"
                  className="shrink-0 rounded-full p-1.5 transition active:scale-90 hover:bg-stone-100 dark:hover:bg-zinc-800"
                >
                  <Heart size={16} className="fill-red-500 text-red-500" />
                </button>
              </div>
            ))}
          </RowCard>
        )}
      </section>

      {/* Favourite Players */}
      <section className="space-y-2">
        <SectionLabel>
          <Star size={14} className="text-amber-500" />
          Favourite Players
        </SectionLabel>

        {loadingPlayers ? (
          <LoadingSpinner />
        ) : favPlayers.length === 0 ? (
          empty('No favourite players yet — tap the heart on any squad page')
        ) : (
          <RowCard>
            {favPlayers.map((player) => (
              <div
                key={player.id!}
                className={`flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-stone-50 dark:hover:bg-zinc-800/40 ${ROW_DIVIDER}`}
              >
                {/* Jersey number badge */}
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-stone-100 text-xs font-extrabold tabular-nums text-stone-600 dark:bg-zinc-800 dark:text-zinc-300">
                  {player.jerseyNumber ?? '—'}
                </span>

                {/* Favourite players always come from the DB so id is never null here. */}
                <Link to={`/players/${player.id!}`} state={player} className="flex-1 overflow-hidden">
                  <p className="truncate text-sm font-semibold transition-colors hover:text-amber-600 dark:hover:text-amber-400">
                    {player.name}
                  </p>
                  <p className="truncate text-xs text-stone-500 dark:text-zinc-500">
                    {[player.position, player.nationality].filter(Boolean).join(' · ')}
                  </p>
                </Link>

                <button
                  onClick={() => handleRemovePlayer(player.id!)}
                  aria-label="Remove from favourites"
                  className="shrink-0 rounded-full p-1.5 transition active:scale-90 hover:bg-stone-100 dark:hover:bg-zinc-800"
                >
                  <Heart size={16} className="fill-red-500 text-red-500" />
                </button>
              </div>
            ))}
          </RowCard>
        )}
      </section>
    </div>
  )
}
