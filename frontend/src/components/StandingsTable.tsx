import type { StandingsEntryDto } from '../types'

interface Props {
  entries: StandingsEntryDto[]
  // Whether to colour-code position zones (CL/EL/Conf/relegation).
  // False for competitions with no zones (Champions League, NBA, NFL).
  showZones?: boolean
  // When true, the table surface is translucent (`.glass-card`) so a
  // SportFieldBackdrop behind it reads through. Default false → solid card.
  glass?: boolean
}

// ── Football / NBA flat-table helpers ─────────────────────────────────────────

type Zone = 'cl' | 'el' | 'conf' | 'releg' | null

function zoneFor(position: number, total: number): Zone {
  if (position <= 4)        return 'cl'
  if (position === 5)       return 'el'
  if (position === 6)       return 'conf'
  if (position > total - 3) return 'releg'
  return null
}

function rowTint(zone: Zone): string {
  switch (zone) {
    case 'cl':    return 'bg-blue-500/[0.06]'
    case 'el':    return 'bg-orange-400/[0.06]'
    case 'conf':  return 'bg-green-500/[0.06]'
    case 'releg': return 'bg-red-500/[0.06]'
    default:      return ''
  }
}

// Position number badge — carries the zone signal as a coloured chip.
function positionBadge(position: number, zone: Zone): JSX.Element {
  const base = 'inline-flex h-6 w-6 items-center justify-center rounded-md text-[11px] font-bold tabular-nums'
  switch (zone) {
    case 'cl':    return <span className={`${base} bg-blue-100   text-blue-700   dark:bg-blue-500/20   dark:text-blue-400`}>{position}</span>
    case 'el':    return <span className={`${base} bg-orange-100 text-orange-700 dark:bg-orange-400/20 dark:text-orange-300`}>{position}</span>
    case 'conf':  return <span className={`${base} bg-green-100  text-green-700  dark:bg-green-500/20  dark:text-green-400`}>{position}</span>
    case 'releg': return <span className={`${base} bg-red-100    text-red-700    dark:bg-red-500/20    dark:text-red-400`}>{position}</span>
    default:      return <span className={`${base} text-stone-500 dark:text-zinc-500`}>{position}</span>
  }
}

// ── NFL grouped-table helpers ──────────────────────────────────────────────────

// Group a flat list of 32 entries by conference → division.
// Preserves the order returned by the backend (AFC East→North→South→West, NFC East→…).
function groupByDivision(entries: StandingsEntryDto[]) {
  const conferences: { name: string; divisions: { name: string; teams: StandingsEntryDto[] }[] }[] = []

  for (const entry of entries) {
    const confName = entry.conference ?? 'Unknown Conference'
    const divName  = entry.division  ?? 'Unknown Division'

    let conf = conferences.find((c) => c.name === confName)
    if (!conf) { conf = { name: confName, divisions: [] }; conferences.push(conf) }

    let div = conf.divisions.find((d) => d.name === divName)
    if (!div) { div = { name: divName, teams: [] }; conf.divisions.push(div) }

    div.teams.push(entry)
  }
  return conferences
}

// Short conference label shown as the conference header chip
function shortConf(name: string) {
  if (name.includes('American')) return 'AFC'
  if (name.includes('National')) return 'NFC'
  return name
}

// Playoff seed tint — top 2 in each division clinch a bye; 3–7 wild card; rest out
function nflSeedBadge(position: number): JSX.Element {
  const base = 'inline-flex h-6 w-6 items-center justify-center rounded-md text-[11px] font-bold tabular-nums'
  if (position === 1) return <span className={`${base} bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-400`}>{position}</span>
  return <span className={`${base} text-stone-500 dark:text-zinc-500`}>{position}</span>
}

// ── NBA grouped-table helpers ──────────────────────────────────────────────────

// Group a flat list of NBA entries by conference (East/West). Unlike the NFL
// helper there's no division level — NBA standings are simply two conference
// tables. Preserves the order the backend returned teams in (already ranked
// within each conference).
function groupByConference(entries: StandingsEntryDto[]) {
  const conferences: { name: string; teams: StandingsEntryDto[] }[] = []
  for (const entry of entries) {
    const confName = entry.conference ?? 'Conference'
    let conf = conferences.find((c) => c.name === confName)
    if (!conf) { conf = { name: confName, teams: [] }; conferences.push(conf) }
    conf.teams.push(entry)
  }
  return conferences
}

// Short conference label shown on the NBA conference header chip
function shortNbaConf(name: string) {
  if (name.includes('Eastern')) return 'EAST'
  if (name.includes('Western')) return 'WEST'
  return name
}

// Format a win percentage as .XXX. Prefers the server-computed value (e.pct);
// falls back to computing from W/L if the backend didn't supply one.
function formatPct(e: StandingsEntryDto): string {
  const value = e.pct ?? (e.won + e.lost > 0 ? e.won / (e.won + e.lost) : 0)
  return value.toFixed(3).replace(/^0/, '')
}

// Format games-behind: the leader (0) shows a dash; half-games show ".5".
function formatGb(gb: number | null): string {
  if (gb == null || gb === 0) return '—'
  return Number.isInteger(gb) ? String(gb) : gb.toFixed(1)
}

// ── Main component ─────────────────────────────────────────────────────────────

export default function StandingsTable({ entries, showZones = true, glass = false }: Props) {
  if (entries.length === 0) {
    return <p className="py-8 text-center text-sm text-stone-500 dark:text-zinc-500">No standings available</p>
  }

  // Outer surface — translucent when field-backed, solid otherwise.
  const surface = glass ? 'glass-card' : 'bg-white dark:bg-zinc-900/60'

  // Detect NFL mode — any entry with a non-null division triggers the grouped layout
  const isNfl = entries.some((e) => e.division !== null)

  // ── NFL grouped layout ───────────────────────────────────────────────────────
  if (isNfl) {
    const conferences = groupByDivision(entries)

    return (
      <div className="space-y-5">
        {conferences.map((conf) => (
          <div key={conf.name} className={`overflow-hidden rounded-2xl border border-stone-200 dark:border-zinc-900 ${surface}`}>
            {/* Conference header */}
            <div className="flex items-center gap-2 border-b border-stone-200 bg-stone-50 px-4 py-2 dark:border-zinc-900 dark:bg-zinc-900/80">
              <span className="rounded bg-stone-900 px-2 py-0.5 text-[11px] font-black tracking-widest text-white dark:bg-zinc-100 dark:text-zinc-900">
                {shortConf(conf.name)}
              </span>
              <span className="text-xs font-semibold text-stone-600 dark:text-zinc-400">{conf.name}</span>
            </div>

            {/* Divisions */}
            {conf.divisions.map((div, divIdx) => (
              <div key={div.name}>
                {/* Division sub-header */}
                <div className={`px-4 py-1.5 ${divIdx > 0 ? 'border-t border-stone-100 dark:border-zinc-900' : ''} bg-stone-50/60 dark:bg-zinc-900/40`}>
                  <span className="text-[10px] font-bold uppercase tracking-[0.12em] text-stone-400 dark:text-zinc-600">
                    {div.name}
                  </span>
                </div>

                {/* Team rows */}
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    {/* Only show column headers on the first division to avoid repetition */}
                    {divIdx === 0 && (
                      <thead>
                        <tr className="text-[10px] uppercase tracking-[0.1em] text-stone-400 dark:text-zinc-600">
                          <th className="py-2 pl-4 text-left font-semibold">#</th>
                          <th className="py-2 text-left font-semibold">Team</th>
                          <th className="py-2 text-center font-semibold">W</th>
                          <th className="py-2 text-center font-semibold">L</th>
                          <th className="py-2 text-center font-semibold">T</th>
                          <th className="hidden py-2 text-center font-semibold sm:table-cell">PF</th>
                          <th className="hidden py-2 text-center font-semibold sm:table-cell">PA</th>
                          <th className="py-2 text-center font-semibold">DIFF</th>
                          <th className="py-2 pr-4 text-center font-bold text-stone-700 dark:text-zinc-300">PCT</th>
                        </tr>
                      </thead>
                    )}
                    <tbody>
                      {div.teams.map((e) => {
                        // Win % comes from the server (counts a tie as half a win); formatPct
                        // falls back to a client calc only if the backend didn't supply one.
                        const pct = formatPct(e)
                        const diff = e.goalsFor - e.goalsAgainst
                        return (
                          <tr
                            key={e.team.id}
                            className="border-t border-stone-100 transition-colors hover:bg-stone-50 dark:border-zinc-900 dark:hover:bg-zinc-800/40"
                          >
                            <td className="py-2.5 pl-4">{nflSeedBadge(e.position)}</td>
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
                            <td className="py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400">{e.won}</td>
                            <td className="py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400">{e.lost}</td>
                            <td className="py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400">{e.drawn}</td>
                            <td className="hidden py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400 sm:table-cell">{e.goalsFor}</td>
                            <td className="hidden py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400 sm:table-cell">{e.goalsAgainst}</td>
                            <td className={`py-2.5 text-center tabular-nums ${diff >= 0 ? 'text-stone-600 dark:text-zinc-400' : 'text-red-600 dark:text-red-400'}`}>
                              {diff >= 0 ? `+${diff}` : diff}
                            </td>
                            <td className="py-2.5 pr-4 text-center font-extrabold tabular-nums">{pct}</td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            ))}
          </div>
        ))}
      </div>
    )
  }

  // ── NBA conference-grouped layout ────────────────────────────────────────────
  // Triggered when entries carry a conference but no division. (NFL sets division
  // too, so it's already handled above — this branch only runs for NBA.) Renders
  // East and West as two separate tables instead of one flat 1–30 list.
  const isNba = entries.some((e) => e.conference !== null)
  if (isNba) {
    const conferences = groupByConference(entries)

    return (
      <div className="space-y-5">
        {conferences.map((conf) => (
          <div key={conf.name} className={`overflow-hidden rounded-2xl border border-stone-200 dark:border-zinc-900 ${surface}`}>
            {/* Conference header */}
            <div className="flex items-center gap-2 border-b border-stone-200 bg-stone-50 px-4 py-2 dark:border-zinc-900 dark:bg-zinc-900/80">
              <span className="rounded bg-stone-900 px-2 py-0.5 text-[11px] font-black tracking-widest text-white dark:bg-zinc-100 dark:text-zinc-900">
                {shortNbaConf(conf.name)}
              </span>
              <span className="text-xs font-semibold text-stone-600 dark:text-zinc-400">{conf.name}</span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-[10px] uppercase tracking-[0.1em] text-stone-400 dark:text-zinc-600">
                    <th className="py-2.5 pl-4 text-left font-semibold">#</th>
                    <th className="py-2.5 text-left font-semibold">Team</th>
                    <th className="py-2.5 text-center font-semibold">W</th>
                    <th className="py-2.5 text-center font-semibold">L</th>
                    <th className="py-2.5 text-center font-bold text-stone-700 dark:text-zinc-300">PCT</th>
                    <th className="py-2.5 pr-4 text-center font-semibold">GB</th>
                  </tr>
                </thead>
                <tbody>
                  {conf.teams.map((e) => (
                    <tr
                      key={e.team.id}
                      className="border-t border-stone-100 transition-colors hover:bg-stone-50 dark:border-zinc-900 dark:hover:bg-zinc-800/40"
                    >
                      <td className="py-2.5 pl-4">
                        <span className="inline-flex h-6 w-6 items-center justify-center rounded-md text-[11px] font-bold tabular-nums text-stone-500 dark:text-zinc-500">
                          {e.position}
                        </span>
                      </td>
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
                      <td className="py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400">{e.won}</td>
                      <td className="py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400">{e.lost}</td>
                      <td className="py-2.5 text-center font-extrabold tabular-nums">{formatPct(e)}</td>
                      <td className="py-2.5 pr-4 text-center tabular-nums text-stone-500 dark:text-zinc-500">{formatGb(e.gamesBehind)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ))}
      </div>
    )
  }

  // ── Football flat-table layout ───────────────────────────────────────────────
  return (
    <div className={`overflow-hidden rounded-2xl border border-stone-200 dark:border-zinc-900 ${surface}`}>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-[10px] uppercase tracking-[0.1em] text-stone-400 dark:text-zinc-600">
              <th className="py-3 pl-4 text-left font-semibold">#</th>
              <th className="py-3 text-left font-semibold">Team</th>
              <th className="py-3 text-center font-semibold">P</th>
              <th className="py-3 text-center font-semibold">W</th>
              <th className="py-3 text-center font-semibold">D</th>
              <th className="py-3 text-center font-semibold">L</th>
              <th className="hidden py-3 text-center font-semibold sm:table-cell">GF</th>
              <th className="hidden py-3 text-center font-semibold sm:table-cell">GA</th>
              <th className="py-3 text-center font-semibold">GD</th>
              <th className="py-3 pr-4 text-center font-bold text-stone-700 dark:text-zinc-300">Pts</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((e) => {
              const zone = showZones ? zoneFor(e.position, entries.length) : null
              return (
                <tr
                  key={e.team.id}
                  className={`border-t border-stone-100 transition-colors
                    hover:bg-stone-50 dark:border-zinc-900 dark:hover:bg-zinc-800/40
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
                  <td className="py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400">{e.played}</td>
                  <td className="py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400">{e.won}</td>
                  <td className="py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400">{e.drawn}</td>
                  <td className="py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400">{e.lost}</td>
                  <td className="hidden py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400 sm:table-cell">{e.goalsFor}</td>
                  <td className="hidden py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400 sm:table-cell">{e.goalsAgainst}</td>
                  <td className="py-2.5 text-center tabular-nums text-stone-600 dark:text-zinc-400">{e.goalsFor - e.goalsAgainst}</td>
                  <td className="py-2.5 pr-4 text-center font-extrabold tabular-nums">{e.points}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* Legend — only shown for domestic football leagues with promotion/relegation zones */}
      {showZones && (
        <div className="flex flex-wrap gap-x-4 gap-y-1.5 border-t border-stone-100 px-4 py-2.5 text-[11px] text-stone-500 dark:border-zinc-900 dark:text-zinc-500">
          <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-sm bg-blue-500" /> Champions League</span>
          <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-sm bg-orange-400" /> Europa League</span>
          <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-sm bg-green-500" /> Conference League</span>
          <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-sm bg-red-500" /> Relegation</span>
        </div>
      )}
    </div>
  )
}
