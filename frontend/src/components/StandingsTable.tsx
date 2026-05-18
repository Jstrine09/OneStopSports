import type { StandingsEntryDto } from '../types'

interface Props {
  entries: StandingsEntryDto[]
  // Whether to colour-code position zones (CL/EL/Conf/relegation).
  // false for competitions with no zones (Champions League, NBA, NFL) so the
  // table doesn't show a misleading legend.
  showZones?: boolean
}

type Zone = 'cl' | 'el' | 'conf' | 'releg' | null

function zoneFor(position: number, total: number): Zone {
  if (position <= 4)        return 'cl'
  if (position === 5)       return 'el'
  if (position === 6)       return 'conf'
  if (position > total - 3) return 'releg'
  return null
}

// Background tint for the row. Very low chroma so the wash reads as context,
// not as the dominant signal — the position number badge carries the colour weight.
function rowTint(zone: Zone): string {
  switch (zone) {
    case 'cl':    return 'bg-blue-500/[0.06]'
    case 'el':    return 'bg-orange-400/[0.06]'
    case 'conf':  return 'bg-green-500/[0.06]'
    case 'releg': return 'bg-red-500/[0.06]'
    default:      return ''
  }
}

// Position number badge — replaces the banned border-left side-stripe.
// The number itself carries the zone signal, which is more legible than a thin
// strip of colour off to the side and works at any density.
function positionBadge(position: number, zone: Zone): JSX.Element {
  const base = 'inline-flex h-6 w-6 items-center justify-center rounded-md text-[11px] font-bold tabular-nums'
  switch (zone) {
    case 'cl':    return <span className={`${base} bg-blue-500/20 text-blue-400`}>{position}</span>
    case 'el':    return <span className={`${base} bg-orange-400/20 text-orange-300`}>{position}</span>
    case 'conf':  return <span className={`${base} bg-green-500/20 text-green-400`}>{position}</span>
    case 'releg': return <span className={`${base} bg-red-500/20 text-red-400`}>{position}</span>
    default:      return <span className={`${base} text-slate-500 dark:text-zinc-500`}>{position}</span>
  }
}

export default function StandingsTable({ entries, showZones = true }: Props) {
  if (entries.length === 0) {
    return <p className="py-8 text-center text-sm text-slate-500 dark:text-zinc-500">No standings available</p>
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/60">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-[10px] uppercase tracking-[0.1em] text-slate-400 dark:text-zinc-600">
              <th className="py-3 pl-4 text-left font-semibold">#</th>
              <th className="py-3 text-left font-semibold">Team</th>
              <th className="py-3 text-center font-semibold">P</th>
              <th className="py-3 text-center font-semibold">W</th>
              <th className="py-3 text-center font-semibold">D</th>
              <th className="py-3 text-center font-semibold">L</th>
              <th className="hidden py-3 text-center font-semibold sm:table-cell">GF</th>
              <th className="hidden py-3 text-center font-semibold sm:table-cell">GA</th>
              <th className="py-3 text-center font-semibold">GD</th>
              <th className="py-3 pr-4 text-center font-bold text-slate-700 dark:text-zinc-300">Pts</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((e) => {
              const zone = showZones ? zoneFor(e.position, entries.length) : null
              return (
                <tr
                  key={e.team.id}
                  className={`border-t border-slate-100 transition-colors
                    hover:bg-slate-50 dark:border-zinc-900 dark:hover:bg-zinc-800/40
                    ${rowTint(zone)}`}
                >
                  <td className="py-2.5 pl-4">{positionBadge(e.position, zone)}</td>
                  <td className="py-2.5">
                    <div className="flex items-center gap-2.5">
                      {e.team.crestUrl && (
                        <img src={e.team.crestUrl} alt={e.team.name} className="h-5 w-5 object-contain" />
                      )}
                      <span className="font-medium">
                        <span className="hidden sm:inline">{e.team.name}</span>
                        <span className="sm:hidden">{e.team.shortName}</span>
                      </span>
                    </div>
                  </td>
                  <td className="py-2.5 text-center tabular-nums text-slate-600 dark:text-zinc-400">{e.played}</td>
                  <td className="py-2.5 text-center tabular-nums text-slate-600 dark:text-zinc-400">{e.won}</td>
                  <td className="py-2.5 text-center tabular-nums text-slate-600 dark:text-zinc-400">{e.drawn}</td>
                  <td className="py-2.5 text-center tabular-nums text-slate-600 dark:text-zinc-400">{e.lost}</td>
                  <td className="hidden py-2.5 text-center tabular-nums text-slate-600 dark:text-zinc-400 sm:table-cell">{e.goalsFor}</td>
                  <td className="hidden py-2.5 text-center tabular-nums text-slate-600 dark:text-zinc-400 sm:table-cell">{e.goalsAgainst}</td>
                  <td className="py-2.5 text-center tabular-nums text-slate-600 dark:text-zinc-400">{e.goalsFor - e.goalsAgainst}</td>
                  <td className="py-2.5 pr-4 text-center font-extrabold tabular-nums">{e.points}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* Legend — only shown for domestic football leagues with promotion/relegation zones */}
      {showZones && (
        <div className="flex flex-wrap gap-x-4 gap-y-1.5 border-t border-slate-100 px-4 py-2.5 text-[11px] text-slate-500 dark:border-zinc-900 dark:text-zinc-500">
          <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-sm bg-blue-500" /> Champions League</span>
          <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-sm bg-orange-400" /> Europa League</span>
          <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-sm bg-green-500" /> Conference League</span>
          <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-sm bg-red-500" /> Relegation</span>
        </div>
      )}
    </div>
  )
}
