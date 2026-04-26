import { NavLink } from 'react-router-dom'
import { Home, Radio, Trophy, User, Search } from 'lucide-react'

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
    <aside className="fixed left-0 top-0 hidden h-full w-56 flex-col border-r border-slate-700 bg-slate-900 lg:flex">
      {/* Logo */}
      <div className="px-6 py-5">
        <span className="text-xl font-extrabold tracking-tight text-white">
          OneStop<span className="text-blue-400">Sports</span>
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
                 ? 'bg-blue-500/20 text-blue-400'
                 : 'text-slate-400 hover:bg-slate-800 hover:text-white'
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
    </aside>
  )
}
