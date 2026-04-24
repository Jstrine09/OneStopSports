import type { StandingsEntryDto } from '../types'

interface Props {
  entries: StandingsEntryDto[]
}

// Premier League: top 4 = Champions League, 5 = Europa, 6 = Conference, 18-20 = Relegation
function rowAccent(position: number, total: number): string {
  if (position <= 4)           return 'border-l-4 border-blue-500'
  if (position === 5)          return 'border-l-4 border-orange-400'
  if (position === 6)          return 'border-l-4 border-green-500'
  if (position > total - 3)    return 'border-l-4 border-red-500'
  return 'border-l-4 border-transparent'
}

export default function StandingsTable({ entries }: Props) {
  if (entries.length === 0) {
    return <p className="py-8 text-center text-slate-400">No standings available</p>
  }

  return (
    <div className="overflow-x-auto rounded-xl bg-slate-800">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-700 text-xs text-slate-400">
            <th className="py-3 pl-4 text-left">#</th>
            <th className="py-3 text-left">Team</th>
            <th className="py-3 text-center">P</th>
            <th className="py-3 text-center">W</th>
            <th className="py-3 text-center">D</th>
            <th className="py-3 text-center">L</th>
            <th className="hidden py-3 text-center sm:table-cell">GF</th>
            <th className="hidden py-3 text-center sm:table-cell">GA</th>
            <th className="py-3 text-center">GD</th>
            <th className="py-3 pr-4 text-center font-bold text-white">Pts</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((e) => (
            <tr
              key={e.team.id}
              className={`border-b border-slate-700/50 last:border-0 hover:bg-slate-700/50 ${rowAccent(e.position, entries.length)}`}
            >
              <td className="py-2.5 pl-4 text-slate-400">{e.position}</td>
              <td className="py-2.5">
                <div className="flex items-center gap-2">
                  {e.team.crestUrl && (
                    <img src={e.team.crestUrl} alt={e.team.name} className="h-5 w-5 object-contain" />
                  )}
                  <span className="font-medium">
                    <span className="hidden sm:inline">{e.team.name}</span>
                    <span className="sm:hidden">{e.team.shortName}</span>
                  </span>
                </div>
              </td>
              <td className="py-2.5 text-center text-slate-300">{e.played}</td>
              <td className="py-2.5 text-center text-slate-300">{e.won}</td>
              <td className="py-2.5 text-center text-slate-300">{e.drawn}</td>
              <td className="py-2.5 text-center text-slate-300">{e.lost}</td>
              <td className="hidden py-2.5 text-center text-slate-300 sm:table-cell">{e.goalsFor}</td>
              <td className="hidden py-2.5 text-center text-slate-300 sm:table-cell">{e.goalsAgainst}</td>
              <td className="py-2.5 text-center text-slate-300">{e.goalsFor - e.goalsAgainst}</td>
              <td className="py-2.5 pr-4 text-center font-bold">{e.points}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Legend */}
      <div className="flex flex-wrap gap-4 border-t border-slate-700 px-4 py-2 text-[11px] text-slate-400">
        <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-sm bg-blue-500" /> Champions League</span>
        <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-sm bg-orange-400" /> Europa League</span>
        <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-sm bg-green-500" /> Conference League</span>
        <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-sm bg-red-500" /> Relegation</span>
      </div>
    </div>
  )
}
