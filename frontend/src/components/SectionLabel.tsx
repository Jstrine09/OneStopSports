import type { ReactNode } from 'react'

/**
 * SectionLabel
 *
 * The small uppercase heading that introduces a section ("FAVOURITE TEAMS",
 * "STANDINGS", "SQUAD", etc.). One component so every section header across the
 * app shares the exact same size, weight, and letter-spacing — the design
 * language from the Claude Design handoff (project/ui.jsx).
 *
 * Pass a leading icon or coloured bullet as the first child and it sits inline
 * before the text (the flex + gap handle spacing). Use `className` to recolor —
 * e.g. a league accent like theme.text — otherwise it's the muted default.
 */
interface Props {
  children: ReactNode
  /** Override the colour (e.g. a league accent class). Defaults to muted grey. */
  className?: string
}

export default function SectionLabel({ children, className }: Props) {
  return (
    <h2
      className={`flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.13em] ${
        className ?? 'text-stone-500 dark:text-zinc-400'
      }`}
    >
      {children}
    </h2>
  )
}
