import { Outlet } from 'react-router-dom'
import BottomNav from './BottomNav'
import Sidebar from './Sidebar'

export default function AppLayout() {
  return (
    <div className="min-h-screen bg-slate-900 text-white">
      {/* Desktop sidebar (≥ lg) */}
      <Sidebar />

      {/* Main content
          - On mobile/tablet: full width, padded bottom for nav bar
          - On desktop: offset left by sidebar width */}
      <main className="lg:ml-56">
        <div className="mx-auto max-w-2xl px-4 pb-24 pt-4 lg:pb-8 lg:pt-6">
          <Outlet />
        </div>
      </main>

      {/* Mobile/tablet bottom nav (< lg) */}
      <BottomNav />
    </div>
  )
}
