import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

interface Draft { startDate?: string; endDate?: string }
interface UiCtx {
  requestOpen: boolean; draft: Draft
  openRequest: (d?: Draft) => void; closeRequest: () => void
  paletteOpen: boolean; setPaletteOpen: (v: boolean) => void
  dark: boolean; toggleDark: () => void
}
const Ctx = createContext<UiCtx | null>(null)

const readTheme = () => {
  try { const t = localStorage.getItem('hp.theme'); if (t) return t === 'dark' } catch { /* ignore */ }
  return false
}

export function UiProvider({ children }: { children: ReactNode }) {
  const [requestOpen, setRequestOpen] = useState(false)
  const [draft, setDraft] = useState<Draft>({})
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [dark, setDark] = useState(readTheme)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark)
    try { localStorage.setItem('hp.theme', dark ? 'dark' : 'light') } catch { /* ignore */ }
  }, [dark])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setPaletteOpen(v => !v) }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <Ctx.Provider value={{
      requestOpen, draft,
      openRequest: d => { setDraft(d ?? {}); setRequestOpen(true) },
      closeRequest: () => setRequestOpen(false),
      paletteOpen, setPaletteOpen,
      dark, toggleDark: () => setDark(v => !v),
    }}>{children}</Ctx.Provider>
  )
}

export function useUi() {
  const c = useContext(Ctx)
  if (!c) throw new Error('useUi fuori da UiProvider')
  return c
}
