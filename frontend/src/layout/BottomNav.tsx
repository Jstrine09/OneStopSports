import { NavLink } from 'react-router-dom'
import { Home, Radio, Trophy, User } from 'lucide-react'

const items = [
  { to: '/',        icon: Home,   label: 'Home'    },
  { to: '/live',    icon: Radio,  label: 'Live'    },
  { to: '/leagues', icon: Trophy, label: 'Leagues' },
  { to: '/profile', icon: User,   label: 'Me'      },
]

export default function BottomNav() {
  return (
    // Fixed to bottom, hidden on large screens (sidebar takes over)
    // pb-safe accounts for iPhone home indicator
    <nav className="fixed bottom-0 left-0 right-0 z-50 border-t border-slate-700 bg-slate-900 pb-safe lg:hidden">
      <div className="flex">
        {items.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] transition
               ${isActive ? 'text-blue-400' : 'text-slate-400 hover:text-slate-200'}`
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
