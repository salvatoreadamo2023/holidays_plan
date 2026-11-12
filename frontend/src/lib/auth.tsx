import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, setUnauthorizedHandler, tokenStore } from './api'
import type { UserSummary } from './types'

interface AuthCtx {
  user: UserSummary | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

const Ctx = createContext<AuthCtx | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const qc = useQueryClient()

  const logout = useCallback(() => { tokenStore.clear(); setUser(null); qc.clear() }, [qc])

  useEffect(() => {
    setUnauthorizedHandler(() => { setUser(null); qc.clear() })
    if (!tokenStore.get()) { setLoading(false); return }
    api<UserSummary>('/api/auth/me').then(setUser).catch(() => tokenStore.clear()).finally(() => setLoading(false))
  }, [qc])

  const login = useCallback(async (username: string, password: string) => {
    const r = await api<{ token: string; user: UserSummary }>('/api/auth/login', { method: 'POST', json: { username, password } })
    tokenStore.set(r.token)
    setUser(r.user)
  }, [])

  return <Ctx.Provider value={{ user, loading, login, logout }}>{children}</Ctx.Provider>
}

export function useAuth() {
  const c = useContext(Ctx)
  if (!c) throw new Error('useAuth fuori da AuthProvider')
  return c
}

export const isManager = (u: UserSummary | null) => !!u && u.role !== 'USER'
export const isAdmin = (u: UserSummary | null) => u?.role === 'ADMIN'
