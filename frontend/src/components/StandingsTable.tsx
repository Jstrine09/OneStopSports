import type { StandingsEntryDto } from '../types'

interface Props {
  entries: StandingsEntryDto[]
  // Whether to show coloured position indicators (row shading + left border + legend).
  // Set to false for competitions with no promotion/relegation zones — e.g. Champions League,
  // and non-football sports like basketball where the colour key doesn't apply.
  // Defaults to true so existing callers don't need to change.
  showZones?: boolean
}

// Returns a subtle background tint for the whole row based on league position.
// Using very low opacity (/10) so the highlight is noticeable but not distracting.
// This is applied to the <tr> so the entire row gets a soft wash of colour.
function rowBg(position: number, total: number): string {
  if (position <= 4)           return 'bg-blue-500/10'
  if (position === 5)          return 'bg-orange-400/10'
  if (position === 6)          return 'bg-green-500/10'
  if (position > total - 3)    return 'bg-red-500/10'
  return ''
}

// Returns a solid left-border colour for the position-number cell.
// Applied to the first <td> (not <tr>) because browsers don't reliably
// render left borders directly on table row elements.
// The transparent fallback keeps all rows the same width so nothing shifts.
function rowBorder(position: number, total: number): string {
  if (position <= 4)           return 'border-l-4 border-blue-500'
  if (position === 5)          return 'border-l-4 border-orange-400'
  if (position === 6)          return 'border-l-4 border-green-500'
  if (position > total - 3)    return 'border-l-4 border-red-500'
  return 'border-l-4 border-transparent'
}

export default function StandingsTable({ entries, showZones = true }: Props) {
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
              // rowBg gives each row a faint colour wash — blue for CL spots, red for relegation, etc.
              // Only applied when showZones is true (domestic leagues). The Champions League
              // and non-football sports skip this so the table looks clean without a misleading key.
              // hover:bg-slate-700/50 still works on top of the tint (it just darkens the row slightly).
              className={`border-b border-slate-700/50 last:border-0 hover:bg-slate-700/50 ${showZones ? rowBg(e.position, entries.length) : ''}`}
            >
              {/* rowBorder puts the solid 4px stripe on the left edge of the # cell.
                  Keeping it on <td> rather than <tr> ensures it always renders.
                  When showZones is off, we still add a transparent border so all rows
                  stay the same width and the # column doesn't shift. */}
              <td className={`py-2.5 pl-4 text-slate-400 ${showZones ? rowBorder(e.position, entries.length) : 'border-l-4 border-transparent'}`}>{e.position}</td>
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

      {/* Legend — only shown for domestic football leagues that have promotion/relegation zones.
          Hidden for the Champions League and non-football sports (showZones = false). */}
      {showZones && (
        <div className="flex flex-wrap gap-4 border-t border-slate-700 px-4 py-2 text-[11px] text-slate-400">
          <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-sm bg-blue-500" /> Champions League</span>
          <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-sm bg-orange-400" /> Europa League</span>
          <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-sm bg-green-500" /> Conference League</span>
          <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-sm bg-red-500" /> Relegation</span>
        </div>
      )}
    </div>
  )
}
