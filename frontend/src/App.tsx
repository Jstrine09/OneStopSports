import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './context/AuthContext'
import AppLayout from './layout/AppLayout'
import HomePage from './pages/HomePage'
import LivePage from './pages/LivePage'
import LeaguesPage from './pages/LeaguesPage'
import AuthPage from './pages/AuthPage'
import ProfilePage from './pages/ProfilePage'
import TeamDetailPage from './pages/TeamDetailPage'
import MatchDetailPage from './pages/MatchDetailPage'
import PlayerDetailPage from './pages/PlayerDetailPage'
import SearchPage from './pages/SearchPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<AppLayout />}>
              <Route index        element={<HomePage />}   />
              <Route path="live"    element={<LivePage />}    />
              <Route path="leagues" element={<LeaguesPage />} />
              <Route path="profile" element={<ProfilePage />} />
              <Route path="auth"       element={<AuthPage />}       />
              <Route path="teams/:id"   element={<TeamDetailPage />} />
              <Route path="match/:id"   element={<MatchDetailPage />} />
              <Route path="players/:id" element={<PlayerDetailPage />} />
              <Route path="search"    element={<SearchPage />} />
              <Route path="*"         element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  )
}
