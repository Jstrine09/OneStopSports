import { Outlet } from 'react-router-dom'
import BottomNav from './BottomNav'
import Sidebar from './Sidebar'
import ThemeToggle from '../components/ThemeToggle'

export default function AppLayout() {
  return (
    <div className="min-h-screen bg-stone-50 text-stone-900 dark:bg-zinc-950 dark:text-zinc-100">
      {/* Note: sport fields are no longer a single full-page background — they're
          rendered per-section (per league group on Home, behind the Leagues panel,
          etc.) via SportFieldBackdrop, so each section shows the right sport's
          field themed to its league colour. */}

      {/* Desktop sidebar (≥ lg) */}
      <Sidebar />

      {/* Mobile top bar — shows theme toggle; hidden on desktop (sidebar handles it)
          pt-[env(safe-area-inset-top)] makes the bar clear the iPhone notch in PWA mode */}
      <header
        className="fixed left-0 right-0 top-0 z-40 flex justify-end
                   border-b border-stone-200 bg-white px-2
                   pt-[env(safe-area-inset-top)]
                   dark:border-zinc-900 dark:bg-zinc-950 lg:hidden"
      >
        <div className="flex h-11 items-center">
          <ThemeToggle />
        </div>
      </header>

      {/* Main content
          - On mobile/tablet: full width, padded bottom for nav bar.
            pt-mobile-header offsets the content below the fixed top bar (header + safe area).
          - On desktop: offset left by sidebar width, standard top padding */}
      <main className="lg:ml-56">
        <div className="mx-auto max-w-2xl px-4 pb-24 pt-mobile-header lg:pb-8 lg:pt-6">
          <Outlet />
        </div>
      </main>

      {/* Mobile/tablet bottom nav (< lg) */}
      <BottomNav />
    </div>
  )
}
