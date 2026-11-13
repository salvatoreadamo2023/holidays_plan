import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { Send, Undo2, XCircle } from 'lucide-react'
import clsx from 'clsx'
import { toast } from 'sonner'
import { api } from '../lib/api'
import { fmt, isPresence, needsCertificate, num, range, typeText } from '../lib/format'
import type { RequestView, Status } from '../lib/types'
import { Button, Card, Empty, StatusChip, TypeIcon } from '../components/ui'
import { Attachments } from '../components/Attachments'
import { t } from '../lib/i18n'

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
    mutationFn: ({ id, action }: { id: number; action: 'withdraw' | 'cancel' | 'convert' }) => api(`/api/requests/${id}/${action}`, { method: 'POST' }),
    onSuccess: (_, v) => { toast.success(v.action === 'withdraw' ? t('Richiesta ritirata') : v.action === 'convert' ? t('Richiesta di ferie inviata al responsabile') : t('Annullamento chiesto al responsabile')); qc.invalidateQueries() },
    onError: (e: Error) => toast.error(e.message),
  })
  const today = new Date().toISOString().slice(0, 10)
  const list = (q.data ?? []).filter(r => filters.find(x => x.key === f)!.match(r.status))

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <header>
        <h1 className="font-display text-3xl font-bold tracking-tight md:text-4xl">{t('Le mie richieste')}</h1>
        <p className="mt-1 text-sm text-muted">{t('Storico completo, con chi ha deciso e quando.')}</p>
      </header>
      <div className="flex flex-wrap gap-2">
        {filters.map(x => (
          <button key={x.key} onClick={() => setF(x.key)} className={clsx('relative rounded-full px-4 py-1.5 text-sm font-medium', f === x.key ? 'text-white' : 'glass text-muted')}>
            {f === x.key && <motion.span layoutId="req-filter" className="btn-primary absolute inset-0 rounded-full" />}
            <span className="relative">{t(x.label)}</span>
          </button>
        ))}
      </div>
      {q.data && list.length === 0 && <Card><Empty emoji="📭" title={t('Niente da mostrare')} text={t('Qui compariranno le tue richieste.')} /></Card>}
      <div className="relative space-y-3 before:absolute before:bottom-4 before:left-[27px] before:top-4 before:w-px before:bg-[var(--border)] md:before:left-[31px]">
        <AnimatePresence initial={false}>
          {list.map((r, i) => (
            <motion.div key={r.id} layout initial={{ opacity: 0, x: -16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 16 }} transition={{ delay: i * .03 }}>
              <Card className="relative flex flex-wrap items-center gap-4 p-4">
                <TypeIcon type={r.type} size={40} />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold">{typeText(r)}</span>
                    <span className="text-sm text-muted">· {num(r.amount)} {r.type === 'PERMESSO' ? t(r.amount === 1 ? 'ora' : 'ore') : t(r.amount === 1 ? 'giorno' : 'giorni')}</span>
                    <StatusChip status={r.status} />
                    {needsCertificate(r) && !(r.attachments ?? []).length && ['PENDING', 'APPROVED'].includes(r.status) && <span className="rounded-full bg-amber-500/15 px-2 py-0.5 text-xs font-medium text-amber-600 dark:text-amber-300">{t('Certificato mancante')}</span>}
                  </div>
                  <div className="mt-0.5 text-sm">{range(r)}</div>
                  {r.note && <div className="mt-1 text-xs text-muted">“{r.note}”</div>}
                  {r.decidedBy && (
                    <div className="mt-1 text-xs text-muted">
                      {t(r.status === 'REJECTED' ? 'Rifiutata da {who}' : 'Decisa da {who}', { who: r.decidedBy })}{r.decidedAt && ` ${t('il {date}', { date: fmt(r.decidedAt, 'd MMM') })}`}
                      {r.decisionNote && <> · <span className={r.status === 'REJECTED' ? 'text-rose-500' : ''}>{r.decisionNote}</span></>}
                    </div>
                  )}
                  <Attachments requestId={r.id} items={r.attachments ?? []} editable={r.status !== 'WITHDRAWN'} />
                </div>
                {r.status === 'PENDING' && <Button onClick={() => act.mutate({ id: r.id, action: 'withdraw' })}><Undo2 size={16} /> {t('Ritira')}</Button>}
                {r.type === 'TENTATIVE' && r.status === 'APPROVED' && r.startDate >= today && <Button variant="primary" disabled={act.isPending} onClick={() => act.mutate({ id: r.id, action: 'convert' })}><Send size={16} /> {t('Trasforma in richiesta')}</Button>}
                {r.status === 'APPROVED' && r.endDate >= today && <Button onClick={() => act.mutate({ id: r.id, action: 'cancel' })}><XCircle size={16} /> {isPresence(r.type) ? t('Togli') : t('Annulla')}</Button>}
              </Card>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </div>
  )
}
