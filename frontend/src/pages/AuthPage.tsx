import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { login as apiLogin, register as apiRegister } from '../api/auth'
import { useAuth } from '../context/AuthContext'

export default function AuthPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [form, setForm] = useState({ username: '', email: '', password: '' })
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  const handle = (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }))

  const submit = async (ev: React.FormEvent) => {
    ev.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const res =
        mode === 'login'
          ? await apiLogin({ username: form.username, password: form.password })
          : await apiRegister(form)
      login(res.token, res.username)
      navigate('/')
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Something went wrong'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-[80vh] items-center justify-center">
      <div className="w-full max-w-sm space-y-6">
        {/* Branding */}
        <div className="text-center">
          <h1 className="text-3xl font-extrabold">
            OneStop<span className="text-amber-600 dark:text-amber-400">Sports</span>
          </h1>
          <p className="mt-1 text-sm text-stone-500 dark:text-zinc-400">
            {mode === 'login' ? 'Welcome back' : 'Create your account'}
          </p>
        </div>

        {/* Form card */}
        <form onSubmit={submit} className="space-y-3 rounded-2xl bg-white p-6 dark:bg-zinc-800">
          <div>
            <label className="mb-1 block text-xs text-stone-500 dark:text-zinc-400">Username</label>
            <input
              name="username"
              value={form.username}
              onChange={handle}
              required
              autoComplete="username"
              autoCapitalize="none"
              className="w-full rounded-lg bg-stone-100 px-3 py-2.5 text-base outline-none ring-1 ring-stone-300 transition focus:ring-amber-500 dark:bg-zinc-700 dark:ring-zinc-600 md:text-sm"
            />
          </div>

          {mode === 'register' && (
            <div>
              <label className="mb-1 block text-xs text-stone-500 dark:text-zinc-400">Email</label>
              <input
                type="email"
                name="email"
                value={form.email}
                onChange={handle}
                required
                autoComplete="email"
                inputMode="email"
                className="w-full rounded-lg bg-stone-100 px-3 py-2.5 text-base outline-none ring-1 ring-stone-300 transition focus:ring-amber-500 dark:bg-zinc-700 dark:ring-zinc-600 md:text-sm"
              />
            </div>
          )}

          <div>
            <label className="mb-1 block text-xs text-stone-500 dark:text-zinc-400">Password</label>
            <input
              type="password"
              name="password"
              value={form.password}
              onChange={handle}
              required
              minLength={8}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              className="w-full rounded-lg bg-stone-100 px-3 py-2.5 text-base outline-none ring-1 ring-stone-300 transition focus:ring-amber-500 dark:bg-zinc-700 dark:ring-zinc-600 md:text-sm"
            />
          </div>

          {error && (
            <p className="rounded-lg bg-red-500/10 px-3 py-2 text-xs text-red-400">{error}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-lg bg-amber-500 py-2.5 text-sm font-semibold text-white transition hover:bg-amber-600 active:scale-[0.98] disabled:opacity-60"
          >
            {loading ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        {/* Toggle */}
        <p className="text-center text-xs text-stone-500 dark:text-zinc-400">
          {mode === 'login' ? "Don't have an account? " : 'Already have an account? '}
          <button
            onClick={() => { setMode(m => m === 'login' ? 'register' : 'login'); setError(null) }}
            className="text-amber-600 underline-offset-2 hover:underline dark:text-amber-400"
          >
            {mode === 'login' ? 'Sign up' : 'Sign in'}
          </button>
        </p>
      </div>
    </div>
  )
}
