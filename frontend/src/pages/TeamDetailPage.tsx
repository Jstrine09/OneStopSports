import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchTeam, fetchTeamPlayers } from '../api/teams'
import LoadingSpinner from '../components/LoadingSpinner'
import { ChevronLeft, MapPin, Globe } from 'lucide-react'
import type { PlayerDto } from '../types'

// Position groups — flexible so non-football sports fall into 'Other'
const POSITION_ORDER = ['Goalkeeper', 'Defender', 'Midfielder', 'Forward', 'Other']

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

  const grouped = groupByPosition(players)

  return (
    <div className="space-y-4">
      {/* Back */}
      <button
        onClick={() => navigate(-1)}
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
            : <div className="flex h-16 w-16 items-center justify-center rounded-full bg-slate-600 text-lg font-bold">{team.shortName.slice(0,3)}</div>
          }
          <div className="space-y-1">
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
        </div>
      ) : null}

      {/* Squad */}
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
                {grouped[pos].map((player) => (
                  <div
                    key={player.id}
                    className="flex items-center gap-3 rounded-lg bg-slate-800 px-3 py-2.5"
                  >
                    {/* Jersey number */}
                    <span className="w-6 text-right text-xs font-bold text-slate-500">
                      {player.jerseyNumber ?? '—'}
                    </span>

                    {/* Name + nationality */}
                    <div className="flex-1 overflow-hidden">
                      <p className="truncate text-sm font-medium">{player.name}</p>
                      {player.nationality && (
                        <p className="truncate text-xs text-slate-400">{player.nationality}</p>
                      )}
                    </div>

                    {/* Age */}
                    <span className="text-xs text-slate-500">
                      {calculateAge(player.dateOfBirth)}
                    </span>
                  </div>
                ))}
              </div>
            </section>
          ))
      )}
    </div>
  )
}
