import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import './index.css'
import { AuthProvider, isAdmin, isManager, useAuth } from './lib/auth'
import { UiProvider, useUi } from './lib/ui'
import { Layout } from './components/Layout'
import { Login } from './pages/Login'
import { Dashboard } from './pages/Dashboard'
import { CalendarPage } from './pages/CalendarPage'
import { MyRequests } from './pages/MyRequests'
import { Approvals } from './pages/Approvals'
import { Admin } from './pages/Admin'

const qc = new QueryClient({ defaultOptions: { queries: { staleTime: 15_000, refetchOnWindowFocus: true, retry: 1 } } })

function AppRoutes() {
  const { user, loading } = useAuth()
  const { dark } = useUi()
  if (loading) return <div className="grid h-full place-items-center"><div className="h-10 w-10 animate-spin rounded-full border-4 border-violet-500 border-t-transparent" /></div>
  return (
    <>
      <Toaster theme={dark ? 'dark' : 'light'} position="top-right" richColors closeButton />
      {!user ? <Login /> : (
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<Dashboard />} />
            <Route path="calendario" element={<CalendarPage />} />
            <Route path="richieste" element={<MyRequests />} />
            {isManager(user) && <Route path="approvazioni" element={<Approvals />} />}
            {isAdmin(user) && <Route path="admin" element={<Admin />} />}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      )}
    </>
  )
}

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js').catch(() => undefined))
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={qc}>
      <BrowserRouter>
        <UiProvider>
          <AuthProvider>
            <AppRoutes />
          </AuthProvider>
        </UiProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
