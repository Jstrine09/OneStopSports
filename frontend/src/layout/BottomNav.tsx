import { NavLink } from 'react-router-dom'
import { Home, Radio, Trophy, User, Search } from 'lucide-react'

const items = [
  { to: '/',        icon: Home,   label: 'Home'    },
  { to: '/live',    icon: Radio,  label: 'Live'    },
  { to: '/leagues', icon: Trophy, label: 'Leagues' },
  { to: '/search',  icon: Search, label: 'Search'  },
  { to: '/profile', icon: User,   label: 'Me'      },
]

export default function BottomNav() {
  return (
    // Fixed to bottom, hidden on large screens (sidebar takes over)
    // pb-safe accounts for iPhone home indicator
    <nav className="fixed bottom-0 left-0 right-0 z-50 border-t border-stone-200 bg-white pb-safe dark:border-zinc-900 dark:bg-zinc-950 lg:hidden">
      <div className="flex">
        {items.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              // min-h-[44px] guarantees a comfortable tap target per WCAG/Apple HIG.
              `flex min-h-[44px] flex-1 flex-col items-center justify-center gap-0.5 py-2 text-[11px] transition
               ${isActive ? 'text-amber-600 dark:text-amber-400' : 'text-stone-500 hover:text-stone-700 dark:text-zinc-500 dark:hover:text-zinc-200'}`
            }
          >
            <Icon size={22} />
            {label}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}
