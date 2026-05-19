import { Moon, Sun } from 'lucide-react'
import { useTheme } from '../context/ThemeContext'

interface Props {
  /** When true, shows a text label next to the icon (used inside the sidebar). */
  showLabel?: boolean
}

export default function ThemeToggle({ showLabel = false }: Props) {
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'dark'

  return (
    <button
      onClick={toggleTheme}
      aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition
        text-stone-500 hover:bg-stone-100 hover:text-stone-900
        dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-white`}
    >
      {isDark ? <Sun size={20} strokeWidth={1.75} /> : <Moon size={20} strokeWidth={1.75} />}
      {showLabel && (isDark ? 'Light mode' : 'Dark mode')}
    </button>
  )
}
