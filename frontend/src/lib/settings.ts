import { useQuery } from '@tanstack/react-query'
import { api } from './api'

export interface PublicSettings { permitHours: number[]; apkUrl: string | null }
export interface AuthOptions { passwordReset: boolean; apkUrl: string | null }

export const ALL_HOURS = Array.from({ length: 16 }, (_, i) => (i + 1) / 2)

/** Impostazioni decise dall'admin (ore di permesso consentite, link APK). */
export function useSettings() {
  const q = useQuery({ queryKey: ['settings'], queryFn: () => api<PublicSettings>('/api/settings'), staleTime: 60_000 })
  return { permitHours: q.data?.permitHours?.map(Number) ?? [2, 4], apkUrl: q.data?.apkUrl ?? null, loaded: !!q.data }
}

/** Opzioni pubbliche (anche senza login). */
export function useAuthOptions() {
  return useQuery({ queryKey: ['auth-options'], queryFn: () => api<AuthOptions>('/api/auth/options'), staleTime: 300_000, retry: 0 }).data
}
