import type { PlayerCareerStatsDto, StatCategoryDto } from '../types'

interface Props {
  stats: PlayerCareerStatsDto
}

// Renders a player's career stats as one stacked table per category.
//
// Each category (e.g. "Per Game", "Totals", "Passing") gets its own table because
// the column labels differ between categories — a single combined table would either
// truncate or have wildly inconsistent columns.
//
// Mobile-friendly: each table scrolls horizontally inside its own overflow container.
// The "Season" / "Team" columns stay fixed (sticky left) so you don't lose context
// while scrolling stat columns to the right.
export default function CareerStatsTable({ stats }: Props) {
  if (!stats.categories || stats.categories.length === 0) {
    return null
  }

  // Football stats come from a single-season data feed that lags the live season,
  // so we label the season explicitly rather than letting an old year read as
  // "current". Pull the season from the first row we have.
  const footballSeason =
    stats.sport === 'football' ? stats.categories[0]?.seasons?.[0]?.season ?? null : null

  return (
    <section className="space-y-5">
      {/* Section header — keeps the visual rhythm of the page (SQUAD, FAVOURITES, etc.) */}
      <div className="mb-3 flex items-baseline justify-between px-1">
        <h2 className="text-[11px] font-bold uppercase tracking-[0.12em] text-stone-500 dark:text-zinc-400">
          Career Stats
        </h2>
        <span className="text-xs tabular-nums text-stone-400 dark:text-zinc-600">
          {stats.categories.length} {stats.categories.length === 1 ? 'category' : 'categories'}
        </span>
      </div>

      {/* Honesty badge — don't present a lagging football season as if it were live. */}
      {footballSeason && (
        <p className="-mt-3 px-1 text-[11px] text-amber-700 dark:text-amber-400/90">
          Showing the {footballSeason} season — the most recent available on the current data plan.
        </p>
      )}

      {stats.categories.map((cat) => (
        <CategoryTable key={cat.name} category={cat} />
      ))}
    </section>
  )
}

// One category's table — column headers (labels), per-season rows, and an optional
// career row at the bottom (bold, separator above).
function CategoryTable({ category }: { category: StatCategoryDto }) {
  // Defensive: an empty category shouldn't render an empty table.
  if (!category.labels?.length || (!category.seasons?.length && !category.career)) {
    return null
  }

  // Only show the Competition column when at least one row actually has a value for it.
  // NBA/NFL rows are always null on this field (only one competition per league), so the
  // column would just be a wall of em-dashes — hide it entirely for those sports.
  // Football rows always carry a competition name, so the column appears there.
  const hasCompetition = category.seasons.some((r) => !!r.competition)

  return (
    <div>
      {/* Category sub-header — same uppercase tracking style as the squad section headers
          in TeamDetailPage, so the page feels consistent. */}
      <h3 className="mb-1.5 px-1 text-[10px] font-bold uppercase tracking-[0.14em] text-stone-400 dark:text-zinc-600">
        {category.displayName}
      </h3>

      {/* The table itself sits inside an overflow-x-auto wrapper so wide stat blocks
          (NBA averages has 18 columns) can scroll on phones without breaking the page. */}
      <div className="overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/60">
        <div className="overflow-x-auto">
          <table className="w-full text-xs tabular-nums">
            <thead>
              <tr className="border-b border-stone-100 dark:border-zinc-900">
                {/* Season column header — sticky left so it stays put while scrolling */}
                <th className="sticky left-0 z-10 bg-white py-2.5 pl-4 pr-3 text-left text-[10px] font-bold uppercase tracking-[0.12em] text-stone-400 dark:bg-zinc-900/60 dark:text-zinc-600">
                  Season
                </th>
                <th className="py-2.5 pr-3 text-left text-[10px] font-bold uppercase tracking-[0.12em] text-stone-400 dark:text-zinc-600">
                  Team
                </th>
                {hasCompetition && (
                  <th className="py-2.5 pr-3 text-left text-[10px] font-bold uppercase tracking-[0.12em] text-stone-400 dark:text-zinc-600">
                    Comp
                  </th>
                )}
                {category.labels.map((label) => (
                  <th
                    key={label}
                    className="py-2.5 pr-3 text-right text-[10px] font-bold uppercase tracking-[0.12em] text-stone-400 dark:text-zinc-600"
                  >
                    {label}
                  </th>
                ))}
              </tr>
            </thead>

            <tbody>
              {category.seasons.map((row, i) => (
                <tr
                  key={`${row.season ?? 'unknown'}-${row.competition ?? row.team ?? i}-${i}`}
                  className="border-t border-stone-100 transition-colors hover:bg-stone-50 first:border-0 dark:border-zinc-900 dark:hover:bg-zinc-800/40"
                >
                  <td className="sticky left-0 bg-white py-2 pl-4 pr-3 font-semibold dark:bg-zinc-900/60">
                    {row.season ?? '—'}
                  </td>
                  <td className="py-2 pr-3 uppercase text-stone-500 dark:text-zinc-500">
                    {row.team ?? '—'}
                  </td>
                  {hasCompetition && (
                    <td className="whitespace-nowrap py-2 pr-3 text-stone-600 dark:text-zinc-400">
                      {row.competition ?? '—'}
                    </td>
                  )}
                  {row.values.map((value, j) => (
                    <td key={j} className="py-2 pr-3 text-right">
                      {value}
                    </td>
                  ))}
                </tr>
              ))}

              {/* Career aggregate row — stronger top border, bold values, accent background
                  so it visually anchors as a summary row distinct from the per-season list. */}
              {category.career && (
                <tr className="border-t-2 border-stone-200 bg-stone-50/80 dark:border-zinc-800 dark:bg-zinc-800/40">
                  <td className="sticky left-0 bg-stone-50 py-2.5 pl-4 pr-3 text-[10px] font-bold uppercase tracking-[0.14em] text-amber-700 dark:bg-zinc-800/80 dark:text-amber-400">
                    Career
                  </td>
                  <td className="py-2.5 pr-3 text-stone-400 dark:text-zinc-600">—</td>
                  {hasCompetition && (
                    <td className="py-2.5 pr-3 text-stone-400 dark:text-zinc-600">—</td>
                  )}
                  {category.career.values.map((value, j) => (
                    <td key={j} className="py-2.5 pr-3 text-right font-bold">
                      {value}
                    </td>
                  ))}
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
