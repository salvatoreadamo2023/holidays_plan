import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { Ban, Check, Keyboard, PencilLine, X } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '../lib/api'
import { fmt, needsCertificate, num, range, roleLabel, typeText } from '../lib/format'
import type { RequestView } from '../lib/types'
import { Avatar, Button, Card, Empty, Field, inputCls, Modal, Pager, StatusChip, TypeIcon, usePaged } from '../components/ui'
import { Attachments } from '../components/Attachments'
import { AmendModal, AmendsLine } from '../components/AmendModal'
import { t } from '../lib/i18n'

function Conflicts({ r }: { r: RequestView }) {
  // Chiede al calendario chi altro dell'area e' assente in quei giorni.
  const q = useQuery({
    queryKey: ['conflicts', r.id],
    queryFn: () => api<CalendarLike>(`/api/calendar?from=${r.startDate}&to=${r.endDate}${r.user.areaId ? `&areaId=${r.user.areaId}` : ''}`),
  })
  const names = q.data ? [...new Set(q.data.entries.filter(e => e.userId !== r.user.id && e.status !== 'PENDING').map(e => q.data!.people.find(p => p.id === e.userId)?.fullName).filter(Boolean))] as string[] : []
  if (!q.data) return null
  return names.length ? (
    <div className="flex items-center gap-2 text-xs text-amber-600 dark:text-amber-300">
      <div className="flex -space-x-2">{names.slice(0, 5).map(n => <Avatar key={n} name={n} size={22} ring />)}</div>
      {t(names.length === 1 ? '{n} collega assente nello stesso periodo' : '{n} colleghi assenti nello stesso periodo', { n: names.length })}
    </div>
  ) : <div className="text-xs text-emerald-600 dark:text-emerald-300">{t("Nessuna sovrapposizione nell'area")}</div>
}
type CalendarLike = { entries: { userId: number; status: string }[]; people: { id: number; fullName: string }[] }

export function Approvals() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['to-approve'], queryFn: () => api<RequestView[]>('/api/requests/to-approve') })
  const [rejecting, setRejecting] = useState<RequestView | null>(null)
  const [reason, setReason] = useState('')
  const list = q.data ?? []
  const team = useQuery({ queryKey: ['team-active'], queryFn: () => api<RequestView[]>('/api/requests/team-active') })
  const pg = usePaged(list, 10)
  const [teamQ, setTeamQ] = useState('')
  const teamList = (team.data ?? []).filter(r => r.user.fullName.toLowerCase().includes(teamQ.toLowerCase()))
  const tpg = usePaged(teamList, 10, teamQ)
  const [amending, setAmending] = useState<RequestView | null>(null)
  const [revoking, setRevoking] = useState<RequestView | null>(null)
  const [revokeNote, setRevokeNote] = useState('')
  const revoke = useMutation({
    mutationFn: () => api(`/api/requests/${revoking!.id}/revoke`, { method: 'POST', json: { note: revokeNote } }),
    onSuccess: () => { toast.success(t('Assenza annullata'), { description: t('{who} riceve una notifica.', { who: revoking!.user.fullName }) }); setRevoking(null); setRevokeNote(''); qc.invalidateQueries() },
    onError: (e: Error) => toast.error(e.message),
  })

  const decide = useMutation({
    mutationFn: ({ r, ok, note }: { r: RequestView; ok: boolean; note?: string }) => {
      const base = `/api/requests/${r.id}`
      const path = r.status === 'CANCEL_REQUESTED' ? `${base}/cancel/${ok ? 'accept' : 'deny'}` : `${base}/${ok ? 'approve' : 'reject'}`
      return api(path, { method: 'POST', json: { note: note ?? '' } })
    },
    onSuccess: (_, v) => {
      toast.success(v.ok ? t(v.r.status === 'CANCEL_REQUESTED' ? 'Annullamento confermato per {who}' : 'Approvata per {who}', { who: v.r.user.fullName }) : t('Decisione registrata'))
      setRejecting(null); setReason(''); qc.invalidateQueries()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  // Scorciatoie: A approva, R rifiuta la prima richiesta della lista.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (rejecting || (e.target as HTMLElement).closest('input,textarea') || !list[0]) return
      if (e.key.toLowerCase() === 'a') decide.mutate({ r: list[0], ok: true })
      if (e.key.toLowerCase() === 'r') setRejecting(list[0])
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [list, rejecting, decide])

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-display text-3xl font-bold tracking-tight md:text-4xl">{t('Da approvare')}</h1>
          <p className="mt-1 text-sm text-muted">{t('Richieste delle persone che rientrano nella tua competenza.')}</p>
        </div>
        <span className="hidden items-center gap-2 text-xs text-muted md:flex"><Keyboard size={14} /> <kbd className="rounded border border-soft px-1.5">A</kbd> {t('approva')} · <kbd className="rounded border border-soft px-1.5">R</kbd> {t('rifiuta')}</span>
      </header>

      {q.data && list.length === 0 && <Card><Empty emoji="🎉" title="Inbox zero!" text={t('Non ci sono richieste in attesa. Ottimo lavoro.')} /></Card>}

      <div className="space-y-4">
        <AnimatePresence>
          {pg.rows.map((r, i) => (
            <motion.div key={r.id} layout initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, x: 120, rotate: 3, transition: { duration: .3 } }} transition={{ delay: i * .05 }}>
              <Card className={i === 0 ? 'ring-2 ring-violet-500/30' : ''}>
                <div className="flex flex-wrap items-start gap-4">
                  <Avatar name={r.user.fullName} size={48} />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-display text-lg font-semibold">{r.user.fullName}</span>
                      <span className="text-xs text-muted">{roleLabel[r.user.role]}{r.user.teamName ? ` · ${r.user.teamName}` : ''}</span>
                    </div>
                    <div className="mt-2 flex items-center gap-3">
                      <TypeIcon type={r.type} size={36} />
                      <div>
                        <div className="font-semibold">{typeText(r)} · {num(r.amount)} {r.type === 'PERMESSO' ? t(r.amount === 1 ? 'ora' : 'ore') : t(r.amount === 1 ? 'giorno' : 'giorni')}</div>
                        <div className="text-sm text-muted">{range(r)}</div>
                        <AmendsLine r={r} />
                      </div>
                      {r.status === 'CANCEL_REQUESTED' && <StatusChip status={r.status} />}
                      {needsCertificate(r) && !(r.attachments ?? []).length && <span className="rounded-full bg-amber-500/15 px-2 py-0.5 text-xs font-medium text-amber-600 dark:text-amber-300">{t('Certificato mancante')}</span>}
                    </div>
                    {r.note && <p className="mt-3 rounded-xl bg-[var(--grid)] px-3 py-2 text-sm">“{r.note}”</p>}
                    <Attachments requestId={r.id} items={r.attachments ?? []} />
                    <div className="mt-3"><Conflicts r={r} /></div>
                  </div>
                  <div className="flex w-full gap-2 sm:w-auto sm:flex-col">
                    <Button variant="success" className="flex-1" disabled={decide.isPending} onClick={() => decide.mutate({ r, ok: true })}>
                      <Check size={17} /> {r.status === 'CANCEL_REQUESTED' ? t('Conferma annullo') : t('Approva')}
                    </Button>
                    <Button className="flex-1" disabled={decide.isPending}
                      onClick={() => r.status === 'CANCEL_REQUESTED' ? decide.mutate({ r, ok: false }) : setRejecting(r)}>
                      <X size={17} /> {r.status === 'CANCEL_REQUESTED' ? t('Mantieni') : t('Rifiuta')}
                    </Button>
                  </div>
                </div>
              </Card>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
      <Pager pg={pg} />

      {!!team.data?.length && (
        <section className="space-y-3 pt-4">
          <div>
            <h2 className="font-display text-xl font-bold">{t('Assenze approvate del team')}</h2>
            <p className="mt-1 text-sm text-muted">{t('In corso o future: puoi rettificarle o annullarle se la persona te lo chiede. Riceve una notifica.')}</p>
          </div>
          <input className={inputCls} placeholder={t('Cerca persona…')} value={teamQ} onChange={e => setTeamQ(e.target.value)} />
          {tpg.rows.map(r => (
            <Card key={r.id} className="flex flex-wrap items-center gap-3 p-4">
              <TypeIcon type={r.type} size={36} />
              <div className="min-w-0 flex-1">
                <div className="font-semibold">{r.user.fullName} <span className="font-normal text-muted">· {typeText(r)} · {num(r.amount)} {r.type === 'PERMESSO' ? t(r.amount === 1 ? 'ora' : 'ore') : t(r.amount === 1 ? 'giorno' : 'giorni')}</span></div>
                <div className="text-sm text-muted">{range(r)}{r.decidedAt ? ` · ${t('approvata il {date}', { date: fmt(r.decidedAt, 'd MMM') })}` : ''}</div>
              </div>
              <Button onClick={() => setAmending(r)}><PencilLine size={16} /> {t('Rettifica')}</Button>
              <Button onClick={() => setRevoking(r)}><Ban size={16} /> {t('Annulla')}</Button>
            </Card>
          ))}
          <Pager pg={tpg} />
        </section>
      )}

      <AmendModal request={amending} onClose={() => setAmending(null)} manager />
      <Modal open={!!revoking} onClose={() => setRevoking(null)} title={t("Annulla l'assenza di {who}", { who: revoking?.user.fullName ?? '' })}>
        <div className="space-y-4">
          {revoking && <p className="text-sm">{typeText(revoking)} · {range(revoking)}</p>}
          <Field label={t('Motivo (obbligatorio)')} hint={t('Lo vedrà la persona interessata.')}>
            <textarea autoFocus className={`${inputCls} min-h-24`} maxLength={500} value={revokeNote} onChange={e => setRevokeNote(e.target.value)} placeholder={t("Es. chiesto dall'interessato")} />
          </Field>
          <div className="flex justify-end gap-2">
            <Button onClick={() => setRevoking(null)}>{t('Chiudi')}</Button>
            <Button variant="danger" disabled={!revokeNote.trim() || revoke.isPending} onClick={() => revoke.mutate()}>{t('Annulla assenza')}</Button>
          </div>
        </div>
      </Modal>

      <Modal open={!!rejecting} onClose={() => setRejecting(null)} title={t('Rifiuta la richiesta di {who}', { who: rejecting?.user.fullName ?? '' })}>
        <div className="space-y-4">
          <Field label={t('Motivo (obbligatorio)')} hint={t('Lo vedrà la persona che ha fatto la richiesta.')}>
            <textarea autoFocus className={`${inputCls} min-h-24`} maxLength={500} value={reason} onChange={e => setReason(e.target.value)} placeholder={t('Es. rilascio in produzione quella settimana')} />
          </Field>
          <div className="flex justify-end gap-2">
            <Button onClick={() => setRejecting(null)}>{t('Annulla')}</Button>
            <Button variant="danger" disabled={!reason.trim() || decide.isPending} onClick={() => rejecting && decide.mutate({ r: rejecting, ok: false, note: reason })}>{t('Rifiuta')}</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
