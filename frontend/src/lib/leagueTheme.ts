// League → brand-color theme mapping.
//
// Each value is a compound Tailwind class string that bakes BOTH a light-mode
// shade and a `dark:` shade so the theme reads correctly in either theme.
// Light mode needs darker accents (500–700 range) to clear WCAG AA on white;
// dark mode needs lighter accents (300–400 range) to clear AA on zinc-950.
//
// Important: all class names are kept as literal strings (no template-string
// composition) so Tailwind's JIT can statically detect every utility used.

export interface LeagueTheme {
  name: string
  text: string        // accent text — for headings, active labels (light: 700, dark: 400)
  textStrong: string  // brighter accent — for emphasis (light: 800, dark: 300)
  bg: string          // solid background — for active pills, top-accent bars (works in both modes)
  tint: string        // soft background tint — for card washes / pill bgs (light: 100, dark: 500/10)
  ring: string        // ring border — for active selectors (light: 500/50, dark: 400/40)
  border: string      // visible 1px border — for accents (light: 500/40, dark: 400/40)
  glow: string        // radial wash bg — used by StadiumBackdrop's currentColor (works either mode)
  topAccent: string   // border-top color class — works in both modes
  hoverBorder: string // hover border state with `hover:` prefix baked in (both modes)
  hoverTint: string   // hover bg state with `hover:` prefix baked in (both modes)
}

// ── Defaults / fallbacks ──────────────────────────────────────────────────────

const DEFAULT: LeagueTheme = {
  name: 'default',
  text: 'text-amber-600 dark:text-amber-400',
  textStrong: 'text-amber-700 dark:text-amber-300',
  bg: 'bg-amber-500',
  tint: 'bg-amber-100 dark:bg-amber-500/10',
  ring: 'ring-amber-500/50 dark:ring-amber-400/40',
  border: 'border-amber-500/40 dark:border-amber-400/40',
  glow: 'bg-amber-500/[0.06] dark:bg-amber-500/[0.10]',
  topAccent: 'border-t-amber-500',
  hoverBorder: 'hover:border-amber-500/40 dark:hover:border-amber-400/50',
  hoverTint: 'hover:bg-amber-50 dark:hover:bg-amber-500/10',
}

// ── Football leagues ──────────────────────────────────────────────────────────

const PREMIER_LEAGUE: LeagueTheme = {
  name: 'Premier League',
  text: 'text-purple-700 dark:text-purple-400',
  textStrong: 'text-purple-800 dark:text-purple-300',
  bg: 'bg-purple-500',
  tint: 'bg-purple-100 dark:bg-purple-500/10',
  ring: 'ring-purple-500/50 dark:ring-purple-400/40',
  border: 'border-purple-500/40 dark:border-purple-400/40',
  glow: 'bg-purple-500/[0.06] dark:bg-purple-500/[0.10]',
  topAccent: 'border-t-purple-500',
  hoverBorder: 'hover:border-purple-500/40 dark:hover:border-purple-400/50',
  hoverTint: 'hover:bg-purple-50 dark:hover:bg-purple-500/10',
}

const LA_LIGA: LeagueTheme = {
  name: 'La Liga',
  text: 'text-rose-700 dark:text-rose-400',
  textStrong: 'text-rose-800 dark:text-rose-300',
  bg: 'bg-rose-500',
  tint: 'bg-rose-100 dark:bg-rose-500/10',
  ring: 'ring-rose-500/50 dark:ring-rose-400/40',
  border: 'border-rose-500/40 dark:border-rose-400/40',
  glow: 'bg-rose-500/[0.06] dark:bg-rose-500/[0.10]',
  topAccent: 'border-t-rose-500',
  hoverBorder: 'hover:border-rose-500/40 dark:hover:border-rose-400/50',
  hoverTint: 'hover:bg-rose-50 dark:hover:bg-rose-500/10',
}

const BUNDESLIGA: LeagueTheme = {
  name: 'Bundesliga',
  text: 'text-red-700 dark:text-red-400',
  textStrong: 'text-red-800 dark:text-red-300',
  bg: 'bg-red-500',
  tint: 'bg-red-100 dark:bg-red-500/10',
  ring: 'ring-red-500/50 dark:ring-red-400/40',
  border: 'border-red-500/40 dark:border-red-400/40',
  glow: 'bg-red-500/[0.06] dark:bg-red-500/[0.10]',
  topAccent: 'border-t-red-500',
  hoverBorder: 'hover:border-red-500/40 dark:hover:border-red-400/50',
  hoverTint: 'hover:bg-red-50 dark:hover:bg-red-500/10',
}

const SERIE_A: LeagueTheme = {
  name: 'Serie A',
  text: 'text-blue-700 dark:text-blue-400',
  textStrong: 'text-blue-800 dark:text-blue-300',
  bg: 'bg-blue-500',
  tint: 'bg-blue-100 dark:bg-blue-500/10',
  ring: 'ring-blue-500/50 dark:ring-blue-400/40',
  border: 'border-blue-500/40 dark:border-blue-400/40',
  glow: 'bg-blue-500/[0.06] dark:bg-blue-500/[0.10]',
  topAccent: 'border-t-blue-500',
  hoverBorder: 'hover:border-blue-500/40 dark:hover:border-blue-400/50',
  hoverTint: 'hover:bg-blue-50 dark:hover:bg-blue-500/10',
}

const LIGUE_1: LeagueTheme = {
  name: 'Ligue 1',
  // yellow-700 isn't quite dark enough on white — push to yellow-800 for readable contrast
  text: 'text-yellow-800 dark:text-yellow-400',
  textStrong: 'text-yellow-900 dark:text-yellow-300',
  bg: 'bg-yellow-400',
  tint: 'bg-yellow-100 dark:bg-yellow-400/10',
  ring: 'ring-yellow-500/50 dark:ring-yellow-400/40',
  border: 'border-yellow-500/40 dark:border-yellow-400/40',
  glow: 'bg-yellow-400/[0.06] dark:bg-yellow-400/[0.10]',
  topAccent: 'border-t-yellow-400',
  hoverBorder: 'hover:border-yellow-500/40 dark:hover:border-yellow-400/50',
  hoverTint: 'hover:bg-yellow-50 dark:hover:bg-yellow-400/10',
}

const CHAMPIONS_LEAGUE: LeagueTheme = {
  name: 'Champions League',
  text: 'text-sky-700 dark:text-sky-400',
  textStrong: 'text-sky-800 dark:text-sky-300',
  bg: 'bg-sky-500',
  tint: 'bg-sky-100 dark:bg-sky-500/10',
  ring: 'ring-sky-500/50 dark:ring-sky-400/40',
  border: 'border-sky-500/40 dark:border-sky-400/40',
  glow: 'bg-sky-500/[0.08] dark:bg-sky-500/[0.12]', // CL gets a slightly stronger halo
  topAccent: 'border-t-sky-500',
  hoverBorder: 'hover:border-sky-500/40 dark:hover:border-sky-400/50',
  hoverTint: 'hover:bg-sky-50 dark:hover:bg-sky-500/10',
}

// ── American sports ───────────────────────────────────────────────────────────

const NBA: LeagueTheme = {
  name: 'NBA',
  text: 'text-orange-700 dark:text-orange-400',
  textStrong: 'text-orange-800 dark:text-orange-300',
  bg: 'bg-orange-500',
  tint: 'bg-orange-100 dark:bg-orange-500/10',
  ring: 'ring-orange-500/50 dark:ring-orange-400/40',
  border: 'border-orange-500/40 dark:border-orange-400/40',
  glow: 'bg-orange-500/[0.06] dark:bg-orange-500/[0.10]',
  topAccent: 'border-t-orange-500',
  hoverBorder: 'hover:border-orange-500/40 dark:hover:border-orange-400/50',
  hoverTint: 'hover:bg-orange-50 dark:hover:bg-orange-500/10',
}

const NFL: LeagueTheme = {
  name: 'NFL',
  text: 'text-indigo-700 dark:text-indigo-400',
  textStrong: 'text-indigo-800 dark:text-indigo-300',
  bg: 'bg-indigo-500',
  tint: 'bg-indigo-100 dark:bg-indigo-500/10',
  ring: 'ring-indigo-500/50 dark:ring-indigo-400/40',
  border: 'border-indigo-500/40 dark:border-indigo-400/40',
  glow: 'bg-indigo-500/[0.06] dark:bg-indigo-500/[0.10]',
  topAccent: 'border-t-indigo-500',
  hoverBorder: 'hover:border-indigo-500/40 dark:hover:border-indigo-400/50',
  hoverTint: 'hover:bg-indigo-50 dark:hover:bg-indigo-500/10',
}

// ── Resolver ──────────────────────────────────────────────────────────────────

/**
 * Resolve a league theme by name (with sport slug as fallback).
 * Matching is case-insensitive and uses substring contains so minor name
 * variations (e.g. "Premier League" vs "English Premier League") still hit.
 */
export function getLeagueTheme(
  leagueName: string | null | undefined,
  sportSlug?: string | null,
): LeagueTheme {
  if (leagueName) {
    const name = leagueName.toLowerCase()
    if (name.includes('premier league'))                          return PREMIER_LEAGUE
    if (name.includes('la liga') || name.includes('primera'))     return LA_LIGA
    if (name.includes('bundesliga'))                              return BUNDESLIGA
    if (name.includes('serie a'))                                 return SERIE_A
    if (name.includes('ligue 1'))                                 return LIGUE_1
    if (name.includes('champions league') || name.includes('uefa')) return CHAMPIONS_LEAGUE
    if (name === 'nba' || name.includes('national basketball'))   return NBA
    if (name === 'nfl' || name.includes('national football'))     return NFL
  }
  if (sportSlug === 'basketball')        return NBA
  if (sportSlug === 'american-football') return NFL
  return DEFAULT
}

export const DEFAULT_THEME = DEFAULT
