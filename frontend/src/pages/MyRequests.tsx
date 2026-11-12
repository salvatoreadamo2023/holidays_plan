import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { Undo2, XCircle } from 'lucide-react'
import clsx from 'clsx'
import { toast } from 'sonner'
import { api } from '../lib/api'
import { fmt, num, range, typeLabel } from '../lib/format'
import type { RequestView, Status } from '../lib/types'
import { Button, Card, Empty, StatusChip, TypeIcon } from '../components/ui'

const filters: { key: string; label: string; match: (s: Status) => boolean }[] = [
  { key: 'all', label: 'Tutte', match: () => true },
  { key: 'open', label: 'In corso', match: s => s === 'PENDING' || s === 'CANCEL_REQUESTED' },
  { key: 'ok', label: 'Approvate', match: s => s === 'APPROVED' },
  { key: 'closed', label: 'Chiuse', match: s => ['REJECTED', 'WITHDRAWN', 'CANCELLED'].includes(s) },
]

export function MyRequests() {
  const qc = useQueryClient()
  const [f, setF] = useState('all')
  const q = useQuery({ queryKey: ['mine'], queryFn: () => api<RequestView[]>('/api/requests/mine') })
  const act = useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'withdraw' | 'cancel' }) => api(`/api/requests/${id}/${action}`, { method: 'POST' }),
    onSuccess: (_, v) => { toast.success(v.action === 'withdraw' ? 'Richiesta ritirata' : 'Annullamento chiesto al responsabile'); qc.invalidateQueries() },
    onError: (e: Error) => toast.error(e.message),
  })
  const today = new Date().toISOString().slice(0, 10)
  const list = (q.data ?? []).filter(r => filters.find(x => x.key === f)!.match(r.status))

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <header>
        <h1 className="font-display text-3xl font-bold tracking-tight md:text-4xl">Le mie richieste</h1>
        <p className="mt-1 text-sm text-muted">Storico completo, con chi ha deciso e quando.</p>
      </header>
      <div className="flex flex-wrap gap-2">
        {filters.map(x => (
          <button key={x.key} onClick={() => setF(x.key)} className={clsx('relative rounded-full px-4 py-1.5 text-sm font-medium', f === x.key ? 'text-white' : 'glass text-muted')}>
            {f === x.key && <motion.span layoutId="req-filter" className="btn-primary absolute inset-0 rounded-full" />}
            <span className="relative">{x.label}</span>
          </button>
        ))}
      </div>
      {q.data && list.length === 0 && <Card><Empty emoji="📭" title="Niente da mostrare" text="Qui compariranno le tue richieste." /></Card>}
      <div className="relative space-y-3 before:absolute before:bottom-4 before:left-[27px] before:top-4 before:w-px before:bg-[var(--border)] md:before:left-[31px]">
        <AnimatePresence initial={false}>
          {list.map((r, i) => (
            <motion.div key={r.id} layout initial={{ opacity: 0, x: -16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 16 }} transition={{ delay: i * .03 }}>
              <Card className="relative flex flex-wrap items-center gap-4 p-4">
                <TypeIcon type={r.type} size={40} />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold">{typeLabel[r.type]}</span>
                    <span className="text-sm text-muted">· {num(r.amount)} {r.type === 'PERMESSO' ? 'ore' : 'giorni'}</span>
                    <StatusChip status={r.status} />
                  </div>
                  <div className="mt-0.5 text-sm">{range(r)}</div>
                  {r.note && <div className="mt-1 text-xs text-muted">“{r.note}”</div>}
                  {r.decidedBy && (
                    <div className="mt-1 text-xs text-muted">
                      {r.status === 'REJECTED' ? 'Rifiutata' : 'Decisa'} da {r.decidedBy}{r.decidedAt && ` il ${fmt(r.decidedAt, 'd MMM')}`}
                      {r.decisionNote && <> · <span className={r.status === 'REJECTED' ? 'text-rose-500' : ''}>{r.decisionNote}</span></>}
                    </div>
                  )}
                </div>
                {r.status === 'PENDING' && <Button onClick={() => act.mutate({ id: r.id, action: 'withdraw' })}><Undo2 size={16} /> Ritira</Button>}
                {r.status === 'APPROVED' && r.endDate >= today && <Button onClick={() => act.mutate({ id: r.id, action: 'cancel' })}><XCircle size={16} /> Annulla</Button>}
              </Card>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </div>
  )
}
