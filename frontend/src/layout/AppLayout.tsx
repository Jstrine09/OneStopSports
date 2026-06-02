import { Outlet } from 'react-router-dom'
import BottomNav from './BottomNav'
import Sidebar from './Sidebar'
import ThemeToggle from '../components/ThemeToggle'
import PitchBackdrop from '../components/PitchBackdrop'

export default function AppLayout() {
  return (
    <div className="min-h-screen bg-stone-50 text-stone-900 dark:bg-zinc-950 dark:text-zinc-100">
      {/* Full-page animated pitch background.
          - `fixed inset-0` so it stays put while content scrolls; `z-0` sits it
            ABOVE the page background colour but BELOW the content (`relative z-10`).
          - `lg:left-56` offsets it past the desktop sidebar (w-56) so the full
            pitch lives in the content area instead of being clipped behind the rail.
          - `hidden md:block` removes the graphic entirely on phone-sized screens
            (< 768px), where there isn't room for it to read well.
          - `pointer-events-none` (also set inside PitchBackdrop) so it never
            intercepts clicks.
          - Neutral colour (stone/zinc) rather than a league theme, because it spans
            every page across every sport. variant="contain" shows the WHOLE pitch
            (no cropping). */}
      <div className="pointer-events-none fixed inset-0 z-0 hidden md:block lg:left-56">
        <PitchBackdrop colorClass="text-stone-400 dark:text-zinc-700" variant="contain" intensity="subtle" />
      </div>

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
      <main className="relative z-10 lg:ml-56">
        <div className="mx-auto max-w-2xl px-4 pb-24 pt-mobile-header lg:pb-8 lg:pt-6">
          <Outlet />
        </div>
      </main>

      {/* Mobile/tablet bottom nav (< lg) */}
      <BottomNav />
    </div>
  )
}
