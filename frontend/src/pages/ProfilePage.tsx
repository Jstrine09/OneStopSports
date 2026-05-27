import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext'
import {
  getFavoriteTeams, removeFavoriteTeam,
  getFavoritePlayers, removeFavoritePlayer,
} from '../api/auth'
import LoadingSpinner from '../components/LoadingSpinner'
import { LogOut, User, Heart, X, MapPin } from 'lucide-react'

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

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center gap-4 py-16">
        <User size={48} className="text-stone-600" />
        <p className="text-stone-500 dark:text-zinc-400">Sign in to save your favourite teams and players</p>
        <button
          onClick={() => navigate('/auth')}
          className="rounded-lg bg-amber-500 px-6 py-2.5 text-sm font-semibold text-white hover:bg-amber-600"
        >
          Sign in
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold">My Profile</h1>

      {/* User card */}
      <div className="flex items-center gap-3 rounded-xl bg-white px-4 py-4 dark:bg-zinc-800">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-400">
          <User size={24} />
        </div>
        <div>
          <p className="font-semibold">{username}</p>
          <p className="text-xs text-stone-500 dark:text-zinc-400">OneStopSports member</p>
        </div>
      </div>

      {/* Favourite Teams */}
      <section className="space-y-2">
        <div className="flex items-center gap-2 px-1">
          <Heart size={16} className="fill-red-500 text-red-500" />
          <h2 className="text-sm font-semibold text-stone-600 dark:text-zinc-300">Favourite Teams</h2>
          {favTeams.length > 0 && (
            <span className="text-xs text-stone-400 dark:text-zinc-500">({favTeams.length})</span>
          )}
        </div>

        {loadingTeams ? (
          <LoadingSpinner />
        ) : favTeams.length === 0 ? (
          <p className="rounded-xl bg-white py-6 text-center text-sm text-stone-500 dark:bg-zinc-800 dark:text-zinc-400">
            No favourite teams yet —{' '}
            <Link to="/leagues" className="text-amber-600 underline dark:text-amber-400">
              browse Leagues
            </Link>{' '}
            to add some
          </p>
        ) : (
          <div className="grid grid-cols-2 gap-2 md:grid-cols-3 lg:grid-cols-4">
            {favTeams.map((team) => (
              <div key={team.id} className="relative">
                <Link
                  to={`/teams/${team.id}`}
                  className="flex flex-col items-center gap-2 rounded-xl bg-white p-4 transition hover:bg-stone-50 active:scale-[0.97] dark:bg-zinc-800 dark:hover:bg-zinc-700"
                >
                  {team.crestUrl
                    ? <img src={team.crestUrl} alt={team.name} className="h-12 w-12 object-contain" />
                    : <div className="flex h-12 w-12 items-center justify-center rounded-full bg-stone-200 text-sm font-bold dark:bg-zinc-600">{team.shortName.slice(0, 3)}</div>
                  }
                  <p className="text-center text-xs font-semibold leading-tight">{team.name}</p>
                  {team.stadium && (
                    <p className="flex items-center gap-1 text-center text-[10px] text-stone-400 dark:text-zinc-500">
                      <MapPin size={10} />{team.stadium}
                    </p>
                  )}
                </Link>
                {/* Remove button */}
                <button
                  onClick={() => handleRemoveTeam(team.id)}
                  className="absolute right-1 top-1 flex h-[30px] w-[30px] items-center justify-center rounded-full bg-stone-100 text-stone-500 transition hover:bg-red-500/20 hover:text-red-400 dark:bg-zinc-700 dark:text-zinc-400"
                  aria-label="Remove from favourites"
                >
                  <X size={12} />
                </button>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Favourite Players */}
      <section className="space-y-2">
        <div className="flex items-center gap-2 px-1">
          <Heart size={16} className="fill-red-500 text-red-500" />
          <h2 className="text-sm font-semibold text-stone-600 dark:text-zinc-300">Favourite Players</h2>
          {favPlayers.length > 0 && (
            <span className="text-xs text-stone-400 dark:text-zinc-500">({favPlayers.length})</span>
          )}
        </div>

        {loadingPlayers ? (
          <LoadingSpinner />
        ) : favPlayers.length === 0 ? (
          <p className="rounded-xl bg-white py-6 text-center text-sm text-stone-500 dark:bg-zinc-800 dark:text-zinc-400">
            No favourite players yet — tap the{' '}
            <Heart size={12} className="inline fill-red-500 text-red-500" />{' '}
            on any squad page to add one
          </p>
        ) : (
          <div className="space-y-1">
            {favPlayers.map((player) => (
              <div
                key={player.id!}
                className="flex items-center gap-3 rounded-lg bg-white px-3 py-2.5 dark:bg-zinc-800"
              >
                {/* Jersey number placeholder */}
                <span className="w-6 text-right text-xs font-bold text-stone-400 dark:text-zinc-500">
                  {player.jerseyNumber ?? '—'}
                </span>

                {/* Name + nationality.
                    Favourite players always come from the DB so id is never null here.
                    The ! assertion is safe: only historical ESPN players have null id. */}
                <div className="flex-1 overflow-hidden">
                  <Link
                    to={`/players/${player.id!}`}
                    state={player}
                    className="truncate text-sm font-medium hover:text-amber-600 transition-colors dark:hover:text-amber-400"
                  >
                    {player.name}
                  </Link>
                  <p className="truncate text-xs text-stone-500 dark:text-zinc-400">
                    {[player.position, player.nationality].filter(Boolean).join(' · ')}
                  </p>
                </div>

                {/* Remove */}
                <button
                  onClick={() => handleRemovePlayer(player.id!)}
                  className="flex h-[44px] w-[44px] items-center justify-center rounded-full text-stone-400 transition hover:bg-red-500/20 hover:text-red-400 dark:text-zinc-500"
                  aria-label="Remove from favourites"
                >
                  <X size={14} />
                </button>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Sign out */}
      <button
        onClick={() => { logout(); navigate('/') }}
        className="flex w-full items-center gap-2 rounded-xl bg-white px-4 py-3 text-sm text-red-400 transition hover:bg-stone-50 dark:bg-zinc-800 dark:hover:bg-zinc-700"
      >
        <LogOut size={16} />
        Sign out
      </button>
    </div>
  )
}
