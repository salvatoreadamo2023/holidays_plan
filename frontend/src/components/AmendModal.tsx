import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import clsx from 'clsx'
import { api } from '../lib/api'
import { iso, num, range, reasonLabel, typeShort, typeText } from '../lib/format'
import type { AbsenceType, CreateRequest, LeaveReason, RequestView } from '../lib/types'
import { Button, Field, inputCls, Modal } from './ui'
import { t } from '../lib/i18n'

const types: AbsenceType[] = ['FERIE', 'PERMESSO', 'GENERAL_LEAVE', 'CLIENT_OFFICE', 'BUSINESS_TRIP']
const reasons = Object.keys(reasonLabel) as LeaveReason[]

/**
 * Rettifica di un'assenza approvata. Per l'interessato e' una nuova richiesta che, se approvata, sostituisce quella attuale;
 * per un responsabile con competenza (manager = true) la rettifica vale subito.
 */
export function AmendModal({ request, onClose, manager = false }: { request: RequestView | null; onClose: () => void; manager?: boolean }) {
  const qc = useQueryClient()
  const today = iso(new Date())
  const [form, setForm] = useState<CreateRequest>({ type: 'FERIE', startDate: today, endDate: today, hours: 2, note: '', leaveReason: null })
  useEffect(() => {
    if (request) setForm({ type: request.type, startDate: request.startDate, endDate: request.endDate, hours: request.hours ?? 2, note: request.note ?? '', leaveReason: request.leaveReason })
  }, [request])
  const set = <K extends keyof CreateRequest>(k: K, v: CreateRequest[K]) => setForm(f => ({ ...f, [k]: v }))
  const single = form.type === 'PERMESSO'
  const minStart = request && request.startDate < today ? request.startDate : today

  const send = useMutation({
    mutationFn: () => api<RequestView>(`/api/requests/${request!.id}/amend`, {
      method: 'POST',
      json: { ...form, endDate: single ? form.startDate : form.endDate, hours: single ? form.hours : null, leaveReason: form.type === 'GENERAL_LEAVE' ? form.leaveReason : null },
    }),
    onSuccess: r => {
      if (manager) toast.success(t('Assenza rettificata'), { description: t('{who} riceve una notifica.', { who: request!.user.fullName }) })
      else if (r.status === 'APPROVED') toast.success(t('Rettifica applicata'))
      else toast.success(t('Rettifica inviata'), { description: t('Finché non viene approvata resta valida la richiesta attuale.') })
      qc.invalidateQueries(); onClose()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  return (
    <Modal open={!!request} onClose={onClose} title={manager ? t('Rettifica per {who}', { who: request?.user.fullName ?? '' }) : t('Chiedi una rettifica')}>
      {request && (
        <div className="space-y-4">
          <div className="rounded-2xl bg-[var(--grid)] px-3 py-2 text-sm">
            <span className="text-muted">{t('Attuale')}:</span> <b>{typeText(request)}</b> · {range(request)} · {num(request.amount)} {request.type === 'PERMESSO' ? t(request.amount === 1 ? 'ora' : 'ore') : t(request.amount === 1 ? 'giorno' : 'giorni')}
          </div>
          <p className="text-sm text-muted">{manager
            ? t('La modifica vale subito: la richiesta attuale viene sostituita e la persona riceve una notifica.')
            : t('È come una nuova richiesta: se il responsabile la approva sostituisce quella attuale, se la rifiuta resta tutto com\'è.')}</p>
          <Field label={t('Tipo')}>
            <select className={inputCls} value={form.type} onChange={e => set('type', e.target.value as AbsenceType)}>
              {types.map(x => <option key={x} value={x}>{typeShort[x]}</option>)}
            </select>
          </Field>
          <div className={clsx('grid gap-3', single ? 'grid-cols-1' : 'grid-cols-2')}>
            <Field label={single ? t('Giorno') : t('Dal')}>
              <input type="date" className={inputCls} value={form.startDate} min={minStart}
                onChange={e => { set('startDate', e.target.value); if (e.target.value > form.endDate) set('endDate', e.target.value) }} />
            </Field>
            {!single && (
              <Field label={t('Al')}>
                <input type="date" className={inputCls} value={form.endDate} min={form.startDate} onChange={e => set('endDate', e.target.value)} />
              </Field>
            )}
          </div>
          {form.type === 'PERMESSO' && (
            <Field label={t('Ore di permesso: {n}', { n: num(form.hours) })}>
              <input type="range" min={0.5} max={8} step={0.5} value={form.hours ?? 1} onChange={e => set('hours', Number(e.target.value))} className="w-full accent-amber-500" />
            </Field>
          )}
          {form.type === 'GENERAL_LEAVE' && (
            <Field label={t('Tipologia')}>
              <select className={inputCls} value={form.leaveReason ?? ''} onChange={e => set('leaveReason', (e.target.value || null) as LeaveReason | null)}>
                <option value="">{t('Scegli…')}</option>
                {reasons.map(r => <option key={r} value={r}>{reasonLabel[r]}</option>)}
              </select>
            </Field>
          )}
          <Field label={t('Motivo / note (facoltative)')}>
            <textarea className={clsx(inputCls, 'min-h-20 resize-none')} maxLength={500} value={form.note} onChange={e => set('note', e.target.value)} placeholder={t('Es. cambio date concordato col cliente')} />
          </Field>
          <div className="flex justify-end gap-2">
            <Button onClick={onClose}>{t('Chiudi')}</Button>
            <Button variant="primary" disabled={send.isPending || (form.type === 'GENERAL_LEAVE' && !form.leaveReason)} onClick={() => send.mutate()}>
              {t(send.isPending ? 'Invio…' : manager ? 'Applica rettifica' : 'Invia rettifica')}
            </Button>
          </div>
        </div>
      )}
    </Modal>
  )
}

/** "Rettifica di: Ferie · 10–12 nov" */
export function AmendsLine({ r }: { r: RequestView }) {
  if (!r.amends) return null
  return (
    <div className="mt-1 text-xs text-muted">
      <span className="rounded-full bg-violet-500/15 px-2 py-0.5 font-medium text-violet-700 dark:text-violet-300">{t('Rettifica')}</span>{' '}
      {t('di')}: {typeText(r.amends)} · {range(r.amends)}
    </div>
  )
}
