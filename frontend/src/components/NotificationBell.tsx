import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { Bell, CheckCheck } from 'lucide-react'
import { useNavigate } from 'react-router'
import { formatDistanceToNowStrict, parseISO } from 'date-fns'
import clsx from 'clsx'
import { api } from '../lib/api'
import { dateLocale } from '../lib/format'
import { t, ts } from '../lib/i18n'

interface Item { id: number; title: string; body: string | null; screen: string | null; requestId: number | null; createdAt: string; read: boolean }
interface Inbox { unread: number; items: Item[] }

const PATH: Record<string, string> = { Approvazioni: '/approvazioni', Richieste: '/richieste', Calendario: '/calendario' }

/** Campanella: le stesse notifiche che sull'app arrivano come push. */
export function NotificationBell({ className, panelClass }: { className?: string; panelClass?: string }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const qc = useQueryClient()
  const nav = useNavigate()
  const unread = useQuery({ queryKey: ['notif-unread'], queryFn: () => api<{ unread: number }>('/api/notifications/unread'), refetchInterval: 30_000 })
  const inbox = useQuery({ queryKey: ['notif-inbox'], queryFn: () => api<Inbox>('/api/notifications?limit=30'), enabled: open })
  const count = unread.data?.unread ?? 0

  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc) }
  }, [open])

  // Quando arriva una notifica nuova, aggiorna anche le liste aperte (richieste, approvazioni).
  const last = useRef(count)
  useEffect(() => {
    if (count > last.current) { qc.invalidateQueries({ queryKey: ['notif-inbox'] }); qc.invalidateQueries({ queryKey: ['to-approve'] }); qc.invalidateQueries({ queryKey: ['mine'] }) }
    last.current = count
  }, [count, qc])

  const refresh = () => { qc.invalidateQueries({ queryKey: ['notif-unread'] }); qc.invalidateQueries({ queryKey: ['notif-inbox'] }) }
  const readAll = async () => { await api('/api/notifications/read-all', { method: 'POST' }).catch(() => undefined); refresh() }
  const openItem = async (n: Item) => {
    setOpen(false)
    if (!n.read) await api(`/api/notifications/${n.id}/read`, { method: 'POST' }).catch(() => undefined)
    refresh()
    if (n.screen && PATH[n.screen]) nav(PATH[n.screen])
  }

  return (
    <div ref={ref} className="relative flex">
      <button onClick={() => setOpen(o => !o)} className={clsx('relative', className)} aria-haspopup="dialog" aria-expanded={open}
        aria-label={count ? t('Notifiche ({n} da leggere)', { n: count }) : t('Notifiche')} title={t('Notifiche')}>
        <Bell size={17} />
        {count > 0 && (
          <span className="absolute -right-1 -top-1 grid h-[18px] min-w-[18px] place-items-center rounded-full bg-gradient-to-r from-fuchsia-500 to-rose-500 px-1 text-[10px] font-bold text-white shadow-lg shadow-fuchsia-500/40">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>
      <AnimatePresence>
        {open && (
          <motion.div role="dialog" aria-label={t('Notifiche')} initial={{ opacity: 0, y: -6, scale: .98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -6, scale: .98 }} transition={{ duration: .15 }}
            className={clsx('glass-strong z-50 flex max-h-[70vh] flex-col overflow-hidden rounded-2xl shadow-2xl', panelClass ?? 'absolute right-0 top-full mt-2 w-96')}>
            <div className="flex items-center justify-between gap-2 border-b border-soft px-4 py-3">
              <div className="font-display font-semibold">{t('Notifiche')}</div>
              {count > 0 && (
                <button onClick={readAll} className="flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-violet-600 hover:bg-violet-500/10 dark:text-violet-300">
                  <CheckCheck size={14} /> {t('Segna tutte come lette')}
                </button>
              )}
            </div>
            <div className="overflow-y-auto">
              {inbox.isLoading && <div className="p-6 text-center text-sm text-muted">{t('Caricamento…')}</div>}
              {inbox.data && inbox.data.items.length === 0 && (
                <div className="p-8 text-center text-sm text-muted"><div className="mb-2 text-3xl">🔔</div>{t('Nessuna notifica per ora.')}</div>
              )}
              {inbox.data?.items.map(n => (
                <button key={n.id} onClick={() => openItem(n)}
                  className={clsx('flex w-full gap-3 border-b border-soft px-4 py-3 text-left transition last:border-0 hover:bg-violet-500/5', !n.read && 'bg-violet-500/[.06]')}>
                  <span className={clsx('mt-1.5 h-2 w-2 shrink-0 rounded-full', n.read ? 'bg-transparent' : 'bg-fuchsia-500')} />
                  <span className="min-w-0 flex-1">
                    <span className={clsx('block text-sm', !n.read && 'font-semibold')}>{ts(n.title)}</span>
                    {n.body && <span className="mt-0.5 block text-xs text-muted">{ts(n.body)}</span>}
                    <span className="mt-1 block text-[11px] text-muted">{formatDistanceToNowStrict(parseISO(n.createdAt), { addSuffix: true, locale: dateLocale() })}</span>
                  </span>
                </button>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
