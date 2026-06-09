import type { ReactNode } from 'react'

/**
 * RowCard
 *
 * The rounded, bordered surface that holds a vertical list of rows (favourites,
 * search results, squad members, standings, …). Extracted so every list-card in
 * the app has identical corners, border, and background in both themes.
 *
 * It only renders the surface — the rows are the children. Each row should carry
 * its own divider class so they stack cleanly, e.g.:
 *   `border-b border-stone-100 dark:border-zinc-800/70 last:border-0`
 * (exported as ROW_DIVIDER below so callers don't have to remember the string).
 */
interface Props {
  children: ReactNode
  /** Extra classes for the surface (rarely needed). */
  className?: string
}

// Shared divider for rows inside a RowCard — keeps every list looking the same.
export const ROW_DIVIDER = 'border-b border-stone-100 dark:border-zinc-800/70 last:border-0'

export default function RowCard({ children, className }: Props) {
  return (
    <div
      className={`overflow-hidden rounded-2xl border border-stone-200 bg-white dark:border-zinc-900 dark:bg-zinc-900/60 ${
        className ?? ''
      }`}
    >
      {children}
    </div>
  )
}
