import { NavLink } from 'react-router-dom'
import { Home, Radio, Trophy, User, Search } from 'lucide-react'
import ThemeToggle from '../components/ThemeToggle'

const items = [
  { to: '/',        icon: Home,   label: 'Home'    },
  { to: '/live',    icon: Radio,  label: 'Live'    },
  { to: '/leagues', icon: Trophy, label: 'Leagues' },
  { to: '/search',  icon: Search, label: 'Search'  },
  { to: '/profile', icon: User,   label: 'Me'      },
]

export default function Sidebar() {
  return (
    // Only shown on large screens
    <aside className="fixed left-0 top-0 hidden h-full w-56 flex-col border-r border-slate-200 bg-white dark:border-zinc-900 dark:bg-zinc-950 lg:flex">
      {/* Logo — pt-safe ensures the logo clears the notch on iPad/Mac with notch */}
      <div className="pt-safe px-6 py-5">
        <span className="text-xl font-extrabold tracking-tight text-slate-900 dark:text-zinc-100">
          OneStop<span className="text-amber-400">Sports</span>
        </span>
      </div>

      {/* Nav items */}
      <nav className="flex flex-col gap-1 px-3">
        {items.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition
               ${isActive
                 ? 'bg-amber-500/10 text-amber-400'
                 : 'text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:text-zinc-500 dark:hover:bg-zinc-900 dark:hover:text-zinc-100'
               }`
            }
          >
            {({ isActive }) => (
              <>
                <Icon size={20} strokeWidth={isActive ? 2.5 : 1.75} />
                {label}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      {/* Theme toggle — pushed to the bottom of the sidebar */}
      <div className="mt-auto border-t border-slate-200 px-3 py-3 dark:border-zinc-900">
        <ThemeToggle showLabel />
      </div>
    </aside>
  )
}
