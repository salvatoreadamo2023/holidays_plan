import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { AlertTriangle, CalendarDays, Sparkles, Users } from 'lucide-react'
import { toast } from 'sonner'
import clsx from 'clsx'
import { api } from '../lib/api'
import { isAutoRecorded, isPresence, iso, num, reasonLabel, typeEmoji, typeShort } from '../lib/format'
import type { AbsenceType, CreateRequest, LeaveReason, Preview, UserSummary } from '../lib/types'
import { useUi } from '../lib/ui'
import { Avatar, Button, Field, inputCls, Modal } from './ui'
import { ACCEPT, MAX_MB, uploadAttachment } from './Attachments'

const types: AbsenceType[] = ['FERIE', 'PERMESSO', 'GENERAL_LEAVE', 'CLIENT_OFFICE', 'BUSINESS_TRIP']
const reasons = Object.keys(reasonLabel) as LeaveReason[]

function useDebounced<T>(v: T, ms = 350) {
  const [d, setD] = useState(v)
  useEffect(() => { const t = setTimeout(() => setD(v), ms); return () => clearTimeout(t) }, [v, ms])
  return d
}

export function NewRequestDrawer() {
  const { requestOpen, closeRequest, draft } = useUi()
  const qc = useQueryClient()
  const today = iso(new Date())
  const [form, setForm] = useState<CreateRequest>({ type: 'FERIE', startDate: today, endDate: today, hours: 2, note: '', forUserId: null, leaveReason: null })

  useEffect(() => {
    if (requestOpen) setForm(f => ({ ...f, startDate: draft.startDate ?? today, endDate: draft.endDate ?? draft.startDate ?? today, note: '' }))
  }, [requestOpen, draft, today])

  // Admin e PM possono inserire un'assenza per conto di una persona del proprio team (gia' approvata).
  const people = useQuery({
    queryKey: ['on-behalf-people'],
    queryFn: () => api<UserSummary[]>('/api/requests/on-behalf/people'),
    enabled: requestOpen, staleTime: 5 * 60_000,
  })
  const forOther = !!form.forUserId
  const single = form.type === 'PERMESSO'
  const payload: CreateRequest = {
    ...form,
    endDate: single ? form.startDate : form.endDate,
    hours: form.type === 'PERMESSO' ? form.hours : null,
    forUserId: form.forUserId || null,
    leaveReason: form.type === 'GENERAL_LEAVE' ? form.leaveReason : null,
  }
  const debounced = useDebounced(payload)
  const preview = useQuery({
    queryKey: ['preview', debounced],
    queryFn: () => api<Preview>('/api/requests/preview', { method: 'POST', json: debounced }),
    enabled: requestOpen && !!debounced.startDate && !!debounced.endDate,
    retry: false,
  })

  const [file, setFile] = useState<File | null>(null)
  useEffect(() => { if (requestOpen) setFile(null) }, [requestOpen])
  const create = useMutation({
    mutationFn: async () => {
      const r = await api<{ id: number }>('/api/requests', { method: 'POST', json: payload })
      if (file) {
        try { await uploadAttachment(r.id, file) }
        catch (e) { toast.error(`Richiesta inviata, ma l'allegato non è stato caricato: ${(e as Error).message}`) }
      }
      return r
    },
    onSuccess: () => {
      const who = people.data?.find(u => u.id === payload.forUserId)?.fullName
      if (who) toast.success('Assenza inserita', { description: `Registrata per ${who}, già approvata.` })
      else if (payload.type === 'GENERAL_LEAVE') toast.success('Assenza registrata', { description: 'I responsabili sono stati avvisati.' })
      else if (isPresence(payload.type)) toast.success('Registrato', { description: 'Visibile a tutti nel calendario.' })
      else toast.success('Richiesta inviata', { description: 'Il tuo responsabile riceverà la notifica.' })
      qc.invalidateQueries(); closeRequest()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const set = <K extends keyof CreateRequest>(k: K, v: CreateRequest[K]) => setForm(f => ({ ...f, [k]: v }))
  const p = preview.data
  const err = preview.error as Error | null

  return (
    <Modal open={requestOpen} onClose={closeRequest} title="Nuova richiesta" side>
      <div className="space-y-6">
        {!!people.data?.length && (
          <Field label="Per chi" hint={forOther ? "L'assenza viene registrata già approvata e la persona riceve un avviso. Puoi inserire anche date passate." : undefined}>
            <select className={inputCls} value={form.forUserId ?? ''} onChange={e => set('forUserId', e.target.value ? Number(e.target.value) : null)}>
              <option value="">Per me</option>
              {people.data.map(u => <option key={u.id} value={u.id}>{u.fullName}{u.teamName ? ` · ${u.teamName}` : ''}</option>)}
            </select>
          </Field>
        )}
        <div className="grid grid-cols-3 gap-2 sm:grid-cols-5" role="radiogroup" aria-label="Tipo di assenza">
          {types.map(t => (
            <button
              key={t} role="radio" aria-checked={form.type === t}
              onClick={() => set('type', t)}
              className={clsx('relative flex flex-col items-center gap-1.5 rounded-2xl border px-2 py-4 text-sm font-semibold transition',
                form.type === t ? 'border-transparent text-white shadow-lg' : 'border-soft hover:bg-black/5 dark:hover:bg-white/5')}
            >
              {form.type === t && <motion.span layoutId="type-pill" className={clsx('absolute inset-0 rounded-2xl', `bar-${t}`)} transition={{ type: 'spring', damping: 26, stiffness: 300 }} />}
              <span className="relative text-2xl">{typeEmoji[t]}</span>
              <span className="relative text-center text-xs leading-tight sm:text-sm">{typeShort[t]}</span>
            </button>
          ))}
        </div>

        <div className={clsx('grid gap-3', single ? 'grid-cols-1' : 'grid-cols-2')}>
          <Field label={single ? 'Giorno' : 'Dal'}>
            <input type="date" className={inputCls} value={form.startDate}
              onChange={e => { set('startDate', e.target.value); if (e.target.value > form.endDate) set('endDate', e.target.value) }} />
          </Field>
          {!single && (
            <Field label="Al">
              <input type="date" className={inputCls} value={form.endDate} min={form.startDate} onChange={e => set('endDate', e.target.value)} />
            </Field>
          )}
        </div>

        {form.type === 'GENERAL_LEAVE' && (
          <Field label="Tipologia" hint="La vedono solo i tuoi responsabili e l'admin: ai colleghi compare solo «Assente».">
            <select className={inputCls} value={form.leaveReason ?? ''} onChange={e => set('leaveReason', (e.target.value || null) as LeaveReason | null)}>
              <option value="">Scegli…</option>
              {reasons.map(r => <option key={r} value={r}>{reasonLabel[r]}</option>)}
            </select>
          </Field>
        )}
        {isPresence(form.type) && (
          <p className="rounded-2xl bg-emerald-500/10 p-3 text-sm text-emerald-700 dark:text-emerald-300">Non è un'assenza: viene registrato subito, senza approvazione, ed è visibile a tutti nel calendario.</p>
        )}

        {form.type === 'PERMESSO' && (
          <Field label={`Ore di permesso: ${num(form.hours)}`}>
            <input type="range" min={0.5} max={8} step={0.5} value={form.hours ?? 1}
              onChange={e => set('hours', Number(e.target.value))} className="w-full accent-amber-500" />
          </Field>
        )}

        <Field label="Note (facoltative)">
          <textarea className={clsx(inputCls, 'min-h-20 resize-none')} maxLength={500} value={form.note}
            placeholder={form.type === 'GENERAL_LEAVE' ? 'Es. numero protocollo certificato…' : isPresence(form.type) ? 'Es. cliente, città…' : 'Es. viaggio, visita medica…'}
            onChange={e => set('note', e.target.value)} />
        </Field>

        <AnimatePresence mode="wait">
          {err ? (
            <motion.div key="err" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
              className="flex gap-3 rounded-2xl bg-rose-500/10 p-4 text-sm text-rose-600 dark:text-rose-300">
              <AlertTriangle size={18} className="shrink-0" /> {err.message}
            </motion.div>
          ) : p ? (
            <motion.div key="ok" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
              className="relative overflow-hidden rounded-2xl border border-soft p-4">
              <div className="pointer-events-none absolute -right-10 -top-10 h-32 w-32 rounded-full bg-violet-500/20 blur-2xl" />
              <div className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-muted"><Sparkles size={14} /> Anteprima</div>
              <div className="flex items-end gap-6">
                <div>
                  <div className="font-display text-4xl font-bold">{num(p.amount)}</div>
                  <div className="text-xs text-muted">{p.unit} {form.type === 'PERMESSO' ? '' : 'lavorativi'}</div>
                </div>
              </div>
              {p.warnings.map(w => (
                <p key={w} className="mt-3 flex items-center gap-2 text-sm text-amber-600 dark:text-amber-300"><AlertTriangle size={15} /> {w}</p>
              ))}
              <div className="mt-4 border-t border-soft pt-3">
                <p className="mb-2 flex items-center gap-2 text-sm font-medium"><Users size={15} />
                  {p.colleaguesAbsent.length ? `${p.colleaguesAbsent.length} colleghi dell'area assenti in quei giorni` : 'Nessun collega dell\'area assente: via libera!'}
                </p>
                <div className="flex -space-x-2">{p.colleaguesAbsent.slice(0, 8).map(n => <Avatar key={n} name={n} size={30} ring />)}</div>
              </div>
            </motion.div>
          ) : (
            <motion.div key="loading" className="flex items-center gap-2 text-sm text-muted"><CalendarDays size={16} /> Calcolo in corso…</motion.div>
          )}
        </AnimatePresence>

        <Field label="Allegato (facoltativo)" hint={`Certificato o giustificativo: PDF, JPG o PNG fino a ${MAX_MB} MB. Puoi aggiungerlo anche dopo da Le mie richieste.`}>
          <input type="file" accept={ACCEPT} className={clsx(inputCls, 'py-2 text-sm')} onChange={e => setFile(e.target.files?.[0] ?? null)} />
        </Field>

        <Button variant="primary" className="w-full py-3.5 text-base" disabled={create.isPending || !!err || !p || p.warnings.length > 0 || (form.type === 'GENERAL_LEAVE' && !form.leaveReason)}
          onClick={() => create.mutate()}>
          {create.isPending ? 'Invio…' : forOther ? 'Inserisci assenza' : isAutoRecorded(form.type) ? 'Registra' : 'Invia richiesta'}
        </Button>
      </div>
    </Modal>
  )
}
