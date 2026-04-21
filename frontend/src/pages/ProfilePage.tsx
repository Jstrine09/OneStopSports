import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { LogOut, User } from 'lucide-react'

export default function ProfilePage() {
  const { isAuthenticated, username, logout } = useAuth()
  const navigate = useNavigate()

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center gap-4 py-16">
        <User size={48} className="text-slate-600" />
        <p className="text-slate-400">Sign in to save your favourite teams and players</p>
        <button
          onClick={() => navigate('/auth')}
          className="rounded-lg bg-blue-500 px-6 py-2.5 text-sm font-semibold text-white hover:bg-blue-600"
        >
          Sign in
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">My Profile</h1>

      <div className="flex items-center gap-3 rounded-xl bg-slate-800 px-4 py-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-blue-500/20 text-blue-400">
          <User size={24} />
        </div>
        <div>
          <p className="font-semibold">{username}</p>
          <p className="text-xs text-slate-400">MatchDay member</p>
        </div>
      </div>

      <button
        onClick={() => { logout(); navigate('/') }}
        className="flex w-full items-center gap-2 rounded-xl bg-slate-800 px-4 py-3 text-sm text-red-400 transition hover:bg-slate-700"
      >
        <LogOut size={16} />
        Sign out
      </button>
    </div>
  )
}
