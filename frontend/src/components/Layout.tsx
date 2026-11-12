import { useEffect } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { AnimatePresence, motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import { CalendarRange, CheckCheck, Command, Home, ListChecks, LogOut, Moon, Plus, Settings, Sun } from 'lucide-react'
import clsx from 'clsx'
import { isAdmin, isManager, useAuth } from '../lib/auth'
import { useUi } from '../lib/ui'
import { api } from '../lib/api'
import { roleLabel } from '../lib/format'
import type { RequestView } from '../lib/types'
import { Avatar } from './ui'
import { CommandPalette } from './CommandPalette'
import { NewRequestDrawer } from './NewRequestDrawer'

export function Aurora() {
  return <div className="aurora" aria-hidden><span /><span /><span /></div>
}

export function Logo() {
  return (
    <div className="flex items-center gap-2.5">
      <img src="/favicon.svg" alt="" className="h-9 w-9 rounded-xl shadow-lg shadow-violet-500/30" />
      <div className="leading-tight">
        <div className="font-display text-lg font-bold tracking-tight">Ferie</div>
        <div className="text-[11px] text-muted">Holidays Plan</div>
      </div>
    </div>
  )
}

export function Layout() {
  const { user, logout } = useAuth()
  const { openRequest, setPaletteOpen, dark, toggleDark } = useUi()
  const loc = useLocation()
  const nav = useNavigate()
  useEffect(() => {
    if (new URLSearchParams(loc.search).has('nuova')) { openRequest(); nav('/', { replace: true }) }
  }, [loc.search, openRequest, nav])
  const toApprove = useQuery({
    queryKey: ['to-approve'],
    queryFn: () => api<RequestView[]>('/api/requests/to-approve'),
    enabled: isManager(user),
    refetchInterval: 60_000,
  })
  const count = toApprove.data?.length ?? 0

  const links = [
    { to: '/', label: 'Home', icon: Home, end: true },
    { to: '/calendario', label: 'Calendario', icon: CalendarRange },
    { to: '/richieste', label: 'Richieste', icon: ListChecks },
    ...(isManager(user) ? [{ to: '/approvazioni', label: 'Approvazioni', icon: CheckCheck, badge: count }] : []),
    ...(isAdmin(user) ? [{ to: '/admin', label: 'Admin', icon: Settings }] : []),
  ]

  return (
    <div className="relative min-h-full">
      <Aurora />
      <aside className="glass fixed inset-y-3 left-3 z-30 hidden w-64 flex-col rounded-3xl p-4 lg:flex">
        <Logo />
        <button onClick={() => openRequest()} className="btn-primary mt-6 flex items-center justify-center gap-2 rounded-2xl py-3 text-sm font-semibold transition active:scale-95">
          <Plus size={18} /> Nuova richiesta
        </button>
        <nav className="mt-6 flex flex-col gap-1">
          {links.map(l => (
            <NavLink key={l.to} to={l.to} end={l.end}
              className={({ isActive }) => clsx('relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition',
                isActive ? 'text-[var(--text)]' : 'text-muted hover:text-[var(--text)]')}>
              {({ isActive }) => (<>
                {isActive && <motion.span layoutId="nav-pill" className="absolute inset-0 rounded-xl bg-violet-500/12 ring-1 ring-violet-500/25" transition={{ type: 'spring', damping: 28, stiffness: 320 }} />}
                <l.icon size={18} className="relative" />
                <span className="relative">{l.label}</span>
                {'badge' in l && !!l.badge && (
                  <span className="relative ml-auto grid h-5 min-w-5 place-items-center rounded-full bg-gradient-to-r from-fuchsia-500 to-violet-500 px-1.5 text-[11px] font-bold text-white shadow-lg shadow-fuchsia-500/40">{l.badge}</span>
                )}
              </>)}
            </NavLink>
          ))}
        </nav>
        <button onClick={() => setPaletteOpen(true)} className="mt-4 flex items-center gap-2 rounded-xl border border-soft px-3 py-2 text-xs text-muted hover:text-[var(--text)]">
          <Command size={14} /> Cerca un comando <kbd className="ml-auto rounded-md border border-soft px-1.5 py-0.5 font-sans">Ctrl K</kbd>
        </button>
        <div className="mt-auto flex items-center gap-3 rounded-2xl border border-soft p-3">
          {user && <Avatar name={user.fullName} size={38} />}
          <div className="min-w-0 flex-1">
            <div className="truncate text-sm font-semibold">{user?.fullName}</div>
            <div className="truncate text-[11px] text-muted">{user && roleLabel[user.role]}{user?.areaName ? ` · ${user.areaName}` : ''}</div>
          </div>
          <button onClick={toggleDark} className="rounded-lg p-1.5 text-muted hover:text-[var(--text)]" aria-label="Cambia tema">{dark ? <Sun size={16} /> : <Moon size={16} />}</button>
          <button onClick={logout} className="rounded-lg p-1.5 text-muted hover:text-rose-500" aria-label="Esci"><LogOut size={16} /></button>
        </div>
      </aside>

      {/* Barra superiore mobile */}
      <header className="glass sticky top-0 z-30 flex items-center justify-between px-4 py-3 lg:hidden">
        <Logo />
        <div className="flex items-center gap-1">
          <button onClick={toggleDark} className="rounded-lg p-2" aria-label="Cambia tema">{dark ? <Sun size={18} /> : <Moon size={18} />}</button>
          <button onClick={logout} className="rounded-lg p-2" aria-label="Esci"><LogOut size={18} /></button>
        </div>
      </header>

      <main className="relative z-10 px-4 pb-28 pt-4 lg:ml-72 lg:px-8 lg:pb-10 lg:pt-8">
        <AnimatePresence mode="wait">
          <motion.div key={loc.pathname} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: .25 }}>
            <Outlet />
          </motion.div>
        </AnimatePresence>
      </main>

      {/* Navigazione mobile */}
      <nav className="glass-strong fixed inset-x-3 bottom-[max(12px,env(safe-area-inset-bottom))] z-30 flex items-center justify-around rounded-2xl px-2 py-2 lg:hidden">
        {links.slice(0, 2).map(l => (
          <NavLink key={l.to} to={l.to} end={l.end} className={({ isActive }) => clsx('flex flex-col items-center gap-0.5 rounded-xl px-3 py-1.5 text-[10px] font-medium', isActive ? 'text-violet-500' : 'text-muted')}>
            <l.icon size={20} />{l.label}
          </NavLink>
        ))}
        <button onClick={() => openRequest()} className="btn-primary -mt-8 grid h-14 w-14 place-items-center rounded-2xl shadow-xl" aria-label="Nuova richiesta"><Plus size={24} /></button>
        {links.slice(2, 4).map(l => (
          <NavLink key={l.to} to={l.to} className={({ isActive }) => clsx('relative flex flex-col items-center gap-0.5 rounded-xl px-3 py-1.5 text-[10px] font-medium', isActive ? 'text-violet-500' : 'text-muted')}>
            <l.icon size={20} />{l.label}
            {'badge' in l && !!l.badge && <span className="absolute right-1 top-0 h-2 w-2 rounded-full bg-fuchsia-500" />}
          </NavLink>
        ))}
      </nav>

      <NewRequestDrawer />
      <CommandPalette />
    </div>
  )
}
