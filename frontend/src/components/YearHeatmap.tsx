import { eachDayOfInterval, endOfYear, getDay, startOfYear, format, isToday } from 'date-fns'
import { it } from 'date-fns/locale'
import { motion } from 'framer-motion'
import clsx from 'clsx'
import type { RequestView } from '../lib/types'
import { iso, typeLabel } from '../lib/format'

/** "Il tuo anno": una griglia stile contributi, ogni quadrato un giorno. */
export function YearHeatmap({ year, requests, holidays }: { year: number; requests: RequestView[]; holidays?: Set<string> }) {
  const days = eachDayOfInterval({ start: startOfYear(new Date(year, 0, 1)), end: endOfYear(new Date(year, 0, 1)) })
  const byDay = new Map<string, RequestView>()
  for (const r of requests) {
    if (!['APPROVED', 'PENDING', 'CANCEL_REQUESTED'].includes(r.status)) continue
    for (const d of eachDayOfInterval({ start: new Date(r.startDate), end: new Date(r.endDate) })) byDay.set(iso(d), r)
  }
  const offset = (getDay(days[0]) + 6) % 7
  const cells: (Date | null)[] = [...Array(offset).fill(null), ...days]
  while (cells.length % 7) cells.push(null)
  const weeks: (Date | null)[][] = []
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7))

  const monthStarts = Array.from({ length: 12 }, (_, m) => {
    const idx = cells.findIndex(c => c && c.getMonth() === m && c.getDate() === 1)
    return Math.floor(idx / 7)
  })
  return (
    <div className="scrollbar-thin overflow-x-auto pb-2">
      <div className="min-w-[640px]">
        <div className="grid gap-[3px]" style={{ gridTemplateColumns: `repeat(${weeks.length}, 1fr)` }}>
          {weeks.map((w, wi) => (
            <div key={wi} className="grid gap-[3px]">
              {w.map((d, di) => {
                if (!d) return <div key={di} className="aspect-square" />
                const k = iso(d)
                const r = byDay.get(k)
                const weekend = di >= 5 || holidays?.has(k)
                return (
                  <motion.div
                    key={di}
                    initial={{ opacity: 0, scale: 0 }} animate={{ opacity: 1, scale: 1 }}
                    transition={{ delay: wi * 0.008 }}
                    title={`${format(d, 'EEEE d MMMM', { locale: it })}${r ? ' · ' + typeLabel[r.type] : ''}`}
                    className={clsx('aspect-square rounded-[3px]',
                      r ? `bar-${r.type}` : weekend ? 'bg-[var(--weekend)]' : 'bg-[var(--grid)]',
                      r?.status === 'PENDING' && 'opacity-50',
                      isToday(d) && 'ring-2 ring-violet-500')}
                  />
                )
              })}
            </div>
          ))}
        </div>
        <div className="relative mt-2 h-4 text-[11px] text-muted">
          {['gen', 'feb', 'mar', 'apr', 'mag', 'giu', 'lug', 'ago', 'set', 'ott', 'nov', 'dic'].map((m, i) => (
            <span key={m} className="absolute" style={{ left: `${(monthStarts[i] / weeks.length) * 100}%` }}>{m}</span>
          ))}
        </div>
      </div>
    </div>
  )
}
