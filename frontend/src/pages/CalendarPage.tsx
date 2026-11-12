import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { addMonths, endOfMonth, format, isToday, parseISO, startOfMonth } from 'date-fns'
import { it } from 'date-fns/locale'
import { ChevronLeft, ChevronRight, Download, MousePointerClick } from 'lucide-react'
import clsx from 'clsx'
import { toast } from 'sonner'
import { api, download } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useUi } from '../lib/ui'
import { iso, roleLabel, statusLabel, typeEmoji, typeLabel } from '../lib/format'
import type { CalendarView } from '../lib/types'
import { Avatar, Button, Card } from '../components/ui'

const NAME_COL = 200

export function CalendarPage() {
  const { user } = useAuth()
  const { openRequest } = useUi()
  const [month, setMonth] = useState(() => startOfMonth(new Date()))
  const [dir, setDir] = useState(0)
  const [areaId, setAreaId] = useState<number | null>(null)
  const [teamId, setTeamId] = useState<number | null>(null)
  const [drag, setDrag] = useState<{ a: number; b: number } | null>(null)

  const from = iso(month), to = iso(endOfMonth(month))
  const params = new URLSearchParams({ from, to })
  if (areaId) params.set('areaId', String(areaId))
  if (teamId) params.set('teamId', String(teamId))
  const q = useQuery({ queryKey: ['calendar', from, areaId, teamId], queryFn: () => api<CalendarView>(`/api/calendar?${params}`), placeholderData: p => p })
  const v = q.data

  const groups = useMemo(() => {
    if (!v) return []
    const m = new Map<string, CalendarView['people']>()
    for (const p of v.people) {
      const k = p.teamName ?? (p.role === 'PROJECT_MANAGER' ? 'Responsabili' : p.role === 'ADMIN' ? 'Amministrazione' : 'Senza team')
      m.set(k, [...(m.get(k) ?? []), p])
    }
    return [...m.entries()].sort(([a], [b]) => (a === 'Responsabili' ? -1 : b === 'Responsabili' ? 1 : a.localeCompare(b)))
  }, [v])

  const go = (n: number) => { setDir(n); setMonth(m => addMonths(m, n)) }
  const nDays = v?.days.length ?? 30
  const cols = `${NAME_COL}px repeat(${nDays}, minmax(28px, 1fr))`
  const dayIndex = (d: string) => v ? v.days.findIndex(x => x.date === d) : -1

  const absentPerDay = useMemo(() => {
    if (!v) return [] as number[]
    return v.days.map(d => new Set(v.entries.filter(e => e.status !== 'PENDING' && e.startDate <= d.date && e.endDate >= d.date).map(e => e.userId)).size)
  }, [v])
  const maxAbsent = Math.max(1, ...absentPerDay)

  const finishDrag = () => {
    if (drag && v) {
      const [a, b] = [Math.min(drag.a, drag.b), Math.max(drag.a, drag.b)]
      openRequest({ startDate: v.days[a].date, endDate: v.days[b].date })
    }
    setDrag(null)
  }

  return (
    <div className="mx-auto max-w-[1600px] space-y-5">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-3xl font-bold tracking-tight md:text-4xl">Calendario</h1>
          <p className="mt-1 flex items-center gap-1.5 text-sm text-muted"><MousePointerClick size={14} /> Trascina sulla tua riga per prenotare dei giorni</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {v && v.areas.length > 1 && (
            <select className="glass rounded-xl px-3 py-2.5 text-sm font-medium" value={areaId ?? ''} onChange={e => { setAreaId(e.target.value ? Number(e.target.value) : null); setTeamId(null) }}>
              <option value="">Tutte le aree</option>
              {v.areas.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          )}
          <div className="glass flex items-center rounded-xl">
            <button onClick={() => go(-1)} className="p-2.5 hover:text-violet-500" aria-label="Mese precedente"><ChevronLeft size={18} /></button>
            <span className="min-w-36 text-center font-display font-semibold capitalize">{format(month, 'MMMM yyyy', { locale: it })}</span>
            <button onClick={() => go(1)} className="p-2.5 hover:text-violet-500" aria-label="Mese successivo"><ChevronRight size={18} /></button>
          </div>
          <Button onClick={() => { setDir(0); setMonth(startOfMonth(new Date())) }}>Oggi</Button>
          <Button onClick={() => download(`/api/calendar/export?${params}`, `assenze_${from}.csv`).catch(e => toast.error(e.message))}><Download size={16} /> Excel</Button>
        </div>
      </header>

      {v && v.teams.length > 1 && (
        <div className="flex flex-wrap gap-2">
          {[{ id: null, name: 'Tutti i team' }, ...v.teams].map(t => (
            <button key={t.id ?? 'all'} onClick={() => setTeamId(t.id)}
              className={clsx('relative rounded-full px-4 py-1.5 text-sm font-medium transition', teamId === t.id ? 'text-white' : 'glass text-muted hover:text-[var(--text)]')}>
              {teamId === t.id && <motion.span layoutId="team-chip" className="btn-primary absolute inset-0 rounded-full" />}
              <span className="relative">{t.name}</span>
            </button>
          ))}
        </div>
      )}

      <Card className="overflow-hidden p-0">
        <div className="scrollbar-thin overflow-x-auto" onMouseLeave={() => setDrag(null)} onMouseUp={finishDrag}>
          <AnimatePresence mode="wait" custom={dir}>
            <motion.div key={from} custom={dir} initial={{ opacity: 0, x: dir * 40 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: dir * -40 }} transition={{ duration: .25 }}
              className="min-w-[960px] select-none">
              {/* Intestazione giorni + densita' assenze */}
              <div className="grid border-b border-[var(--border)]" style={{ gridTemplateColumns: cols }}>
                <div className="sticky left-0 z-20 bg-[var(--surface-strong)] px-4 py-3 text-xs font-semibold uppercase tracking-wider text-muted backdrop-blur">Persone</div>
                {v?.days.map((d, i) => (
                  <div key={d.date} title={d.holiday ?? undefined}
                    className={clsx('relative flex flex-col items-center py-2 text-center', (d.weekend || d.holiday) && 'bg-[var(--weekend)]')}>
                    <span className="text-[10px] uppercase text-muted">{format(parseISO(d.date), 'EEEEE', { locale: it })}</span>
                    <span className={clsx('mt-0.5 grid h-7 w-7 place-items-center rounded-full text-sm font-semibold',
                      isToday(parseISO(d.date)) && 'btn-primary', d.holiday && !isToday(parseISO(d.date)) && 'text-rose-500')}>
                      {format(parseISO(d.date), 'd')}
                    </span>
                    <span className="mt-1 h-1 w-5 overflow-hidden rounded-full bg-[var(--grid)]">
                      <motion.span className="block h-full rounded-full bg-gradient-to-r from-violet-500 to-cyan-400"
                        initial={{ width: 0 }} animate={{ width: `${(absentPerDay[i] / maxAbsent) * 100}%` }} />
                    </span>
                  </div>
                ))}
              </div>

              {groups.map(([team, people]) => (
                <div key={team}>
                  <div className="sticky left-0 px-4 pb-1 pt-4 text-xs font-bold uppercase tracking-wider text-violet-500">{team}</div>
                  {people.map((p, pi) => {
                    const mine = p.id === user?.id
                    const entries = v!.entries.filter(e => e.userId === p.id)
                    return (
                      <motion.div key={p.id} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: pi * .03 }}
                        className={clsx('group relative grid border-b border-[var(--grid)]', mine && 'bg-violet-500/[.06]')} style={{ gridTemplateColumns: cols }}>
                        <div className="sticky left-0 z-10 flex items-center gap-2.5 bg-[var(--surface-strong)] px-4 py-2.5 backdrop-blur">
                          <Avatar name={p.fullName} size={30} />
                          <div className="min-w-0">
                            <div className="truncate text-sm font-semibold">{p.fullName}{mine && <span className="ml-1.5 text-xs font-normal text-violet-500">(tu)</span>}</div>
                            <div className="truncate text-[11px] text-muted">{roleLabel[p.role]}</div>
                          </div>
                        </div>
                        {v!.days.map((d, i) => {
                          const sel = mine && drag && i >= Math.min(drag.a, drag.b) && i <= Math.max(drag.a, drag.b)
                          return (
                            <div key={d.date}
                              onMouseDown={mine ? () => setDrag({ a: i, b: i }) : undefined}
                              onMouseEnter={mine && drag ? () => setDrag({ ...drag, b: i }) : undefined}
                              className={clsx('h-12 border-l border-[var(--grid)]', (d.weekend || d.holiday) && 'bg-[var(--weekend)]',
                                mine && 'cursor-crosshair hover:bg-violet-500/10', sel && '!bg-violet-500/25')}
                              style={{ gridRow: 1, gridColumn: i + 2 }} />
                          )
                        })}
                        {entries.map((e, ei) => {
                          const s = Math.max(0, dayIndex(e.startDate) === -1 ? 0 : dayIndex(e.startDate))
                          const en = dayIndex(e.endDate) === -1 ? nDays - 1 : dayIndex(e.endDate)
                          const label = `${typeLabel[e.type]}${e.halfDay ? ' · ½' : ''}${e.hours ? ` · ${e.hours}h` : ''}`
                          const short = en - s < 1
                          return (
                            <motion.div key={ei}
                              initial={{ scaleX: 0, opacity: 0 }} animate={{ scaleX: 1, opacity: 1 }} transition={{ delay: .15 + ei * .05, type: 'spring', damping: 22 }}
                              title={`${p.fullName} · ${label} · ${statusLabel[e.status]}\n${e.startDate} → ${e.endDate}`}
                              className={clsx('pointer-events-auto z-[5] mx-0.5 my-2 flex origin-left items-center overflow-hidden rounded-lg px-1.5 text-xs font-semibold text-white shadow-md',
                                `bar-${e.type}`, e.status === 'PENDING' && 'bar-pending', e.status === 'CANCEL_REQUESTED' && 'opacity-60 line-through')}
                              style={{ gridRow: 1, gridColumn: `${s + 2} / ${en + 3}` }}>
                              <span className={clsx('truncate', short && 'mx-auto')}>{short ? (e.hours ? `${e.hours}h` : e.halfDay ? '½' : typeEmoji[e.type]) : label}</span>
                            </motion.div>
                          )
                        })}
                      </motion.div>
                    )
                  })}
                </div>
              ))}
              {v && v.people.length === 0 && <p className="p-10 text-center text-muted">Nessuna persona in questa selezione.</p>}
            </motion.div>
          </AnimatePresence>
        </div>
      </Card>

      <div className="flex flex-wrap items-center gap-5 text-sm text-muted">
        {(['FERIE', 'PERMESSO', 'MALATTIA', 'ASSENZA'] as const).map(t => (
          <span key={t} className="flex items-center gap-2"><span className={clsx('h-3 w-6 rounded', `bar-${t}`)} /> {typeLabel[t]}</span>
        ))}
        <span className="flex items-center gap-2"><span className="bar-FERIE bar-pending h-3 w-6 rounded" /> In attesa</span>
        <span className="flex items-center gap-2"><span className="h-3 w-6 rounded bg-[var(--weekend)] ring-1 ring-[var(--border)]" /> Weekend e festivi</span>
      </div>
    </div>
  )
}
