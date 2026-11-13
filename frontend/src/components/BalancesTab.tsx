import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, Download, FileSpreadsheet, Upload } from 'lucide-react'
import { toast } from 'sonner'
import { api, download } from '../lib/api'
import { fmt, num } from '../lib/format'
import { t } from '../lib/i18n'
import { Button, Card, Field, inputCls, Pager, SectionTitle, usePaged } from './ui'

interface BalanceRow { userId: number; username: string; fullName: string; team: string | null; referenceMonth: string | null; leaveDays: number | null; permitHours: number | null; uploadedAt: string | null }
interface ImportRow { row: number; username: string; fullName: string | null; leaveDays: number | null; permitHours: number | null; error: string | null }
interface ImportResult { referenceMonth: string; applied: boolean; valid: number; errors: number; missing: number; rows: ImportRow[] }

export const monthLabel = (ym: string) => { const s = fmt(ym + '-01', 'MMMM yyyy'); return s.charAt(0).toUpperCase() + s.slice(1) }
const lastMonth = () => { const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() - 1); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}` }
const thisMonth = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}` }

/** Admin: carica il saldo residuo ufficiale (busta paga) da Excel, con anteprima prima di salvare. */
export function BalancesTab() {
  const qc = useQueryClient()
  const { data: list = [] } = useQuery({ queryKey: ['admin-balances'], queryFn: () => api<BalanceRow[]>('/api/admin/balances') })
  const [month, setMonth] = useState(lastMonth())
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportResult | null>(null)
  const input = useRef<HTMLInputElement>(null)

  const send = (apply: boolean) => {
    const fd = new FormData()
    fd.append('file', file!)
    return api<ImportResult>(`/api/admin/balances/import?month=${month}&apply=${apply}`, { method: 'POST', body: fd })
  }
  const check = useMutation({ mutationFn: () => send(false), onSuccess: setPreview, onError: (e: Error) => toast.error(e.message) })
  const save = useMutation({
    mutationFn: () => send(true),
    onSuccess: r => {
      toast.success(t('Saldi caricati per {n} persone', { n: r.valid }))
      setPreview(null); setFile(null); if (input.current) input.current.value = ''
      qc.invalidateQueries({ queryKey: ['admin-balances'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })
  const getTemplate = () => download('/api/admin/balances/template', 'saldi-residui.xlsx').catch((e: Error) => toast.error(e.message))
  const [q, setQ] = useState('')
  const filtered = list.filter(b => `${b.fullName} ${b.team ?? ''}`.toLowerCase().includes(q.toLowerCase()))
  const pg = usePaged(filtered, 10, q)
  const last = list.filter(b => b.uploadedAt).sort((a, b) => (b.uploadedAt! > a.uploadedAt! ? 1 : -1))[0]

  return (
    <div className="space-y-5">
      <Card>
        <SectionTitle>{t('Carica i saldi residui')}</SectionTitle>
        <p className="mb-4 text-sm text-muted">{t('Il saldo viene dalla busta paga ed è solo informativo: ogni persona vede il suo con il mese di riferimento. Non blocca le richieste di ferie o permessi.')}</p>
        <ol className="mb-5 space-y-1.5 text-sm">
          <li><b>1.</b> {t("Scarica l'Excel tipo: ha già una riga per ogni persona.")} <button onClick={getTemplate} className="ml-1 inline-flex items-center gap-1 font-semibold text-violet-600 hover:underline dark:text-violet-300"><Download size={14} />{t('Scarica Excel tipo')}</button></li>
          <li><b>2.</b> {t('Compila ferie residue (giorni) e permessi residui (ore) dalla busta paga.')}</li>
          <li><b>3.</b> {t('Scegli il mese di riferimento, carica il file e controlla l\'anteprima prima di confermare.')}</li>
        </ol>
        <div className="grid gap-4 sm:grid-cols-[200px_1fr_auto] sm:items-end">
          <Field label={t('Saldo residuo al mese')}>
            <input type="month" className={inputCls} value={month} max={thisMonth()} onChange={e => { setMonth(e.target.value); setPreview(null) }} />
          </Field>
          <Field label={t('File Excel (.xlsx)')}>
            <input ref={input} type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" className={inputCls + ' file:mr-3 file:rounded-lg file:border-0 file:bg-violet-500/10 file:px-3 file:py-1 file:text-sm file:font-semibold file:text-violet-600'}
              onChange={e => { setFile(e.target.files?.[0] ?? null); setPreview(null) }} />
          </Field>
          <Button variant="primary" disabled={!file || !month || check.isPending} onClick={() => check.mutate()}><FileSpreadsheet size={16} />{t('Controlla')}</Button>
        </div>
      </Card>

      {preview && (
        <Card>
          <SectionTitle action={<Button variant="primary" disabled={!preview.valid || save.isPending} onClick={() => save.mutate()}><Upload size={16} />{t('Conferma e carica ({n})', { n: preview.valid })}</Button>}>
            {t('Anteprima · saldo a {m}', { m: monthLabel(preview.referenceMonth) })}
          </SectionTitle>
          <div className="mb-3 flex flex-wrap gap-2 text-sm">
            <span className="rounded-full bg-emerald-500/15 px-3 py-1 font-semibold text-emerald-700 dark:text-emerald-300">{t('{n} righe valide', { n: preview.valid })}</span>
            {preview.errors > 0 && <span className="rounded-full bg-rose-500/15 px-3 py-1 font-semibold text-rose-600 dark:text-rose-300">{t('{n} con errori (non verranno caricate)', { n: preview.errors })}</span>}
            {preview.missing > 0 && <span className="rounded-full bg-amber-500/15 px-3 py-1 font-semibold text-amber-700 dark:text-amber-300">{t('{n} persone non presenti nel file (resta il saldo precedente)', { n: preview.missing })}</span>}
          </div>
          <div className="max-h-[420px] overflow-auto scrollbar-thin">
            <table className="w-full min-w-[560px] text-sm">
              <thead className="sticky top-0 bg-[var(--surface-strong)] text-left text-xs uppercase tracking-wide text-muted">
                <tr><th className="py-2 pr-3">{t('Riga')}</th><th className="pr-3">Username</th><th className="pr-3">{t('Persona')}</th><th className="pr-3 text-right">{t('Ferie (gg)')}</th><th className="pr-3 text-right">{t('Permessi (h)')}</th><th></th></tr>
              </thead>
              <tbody>
                {preview.rows.map(r => (
                  <tr key={r.row} className="border-t border-soft">
                    <td className="py-2 pr-3 text-muted">{r.row}</td>
                    <td className="pr-3 font-mono text-xs">{r.username}</td>
                    <td className="pr-3">{r.fullName ?? '—'}</td>
                    <td className="pr-3 text-right">{r.leaveDays == null ? '—' : num(r.leaveDays)}</td>
                    <td className="pr-3 text-right">{r.permitHours == null ? '—' : num(r.permitHours)}</td>
                    <td className="text-xs">{r.error
                      ? <span className="inline-flex items-center gap-1 text-rose-600 dark:text-rose-300"><AlertTriangle size={13} />{r.error}</span>
                      : <CheckCircle2 size={15} className="text-emerald-600" />}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      <Card>
        <SectionTitle>{t('Saldi attuali')}</SectionTitle>
        {last && <p className="mb-3 text-sm text-muted">{t('Ultimo caricamento: {d}', { d: fmt(last.uploadedAt!, 'd MMM yyyy, HH:mm') })}</p>}
        <input className={`${inputCls} mb-3`} placeholder={t('Cerca persona o team…')} value={q} onChange={e => setQ(e.target.value)} />
        <div className="overflow-auto scrollbar-thin">
          <table className="w-full min-w-[560px] text-sm">
            <thead className="text-left text-xs uppercase tracking-wide text-muted">
              <tr><th className="py-2 pr-3">{t('Persona')}</th><th className="pr-3">Team</th><th className="pr-3">{t('Saldo al mese')}</th><th className="pr-3 text-right">{t('Ferie (gg)')}</th><th className="pr-3 text-right">{t('Permessi (h)')}</th></tr>
            </thead>
            <tbody>
              {pg.rows.map(b => (
                <tr key={b.userId} className="border-t border-soft">
                  <td className="py-2 pr-3">{b.fullName}</td>
                  <td className="pr-3 text-muted">{b.team ?? '—'}</td>
                  <td className="pr-3">{b.referenceMonth ? monthLabel(b.referenceMonth) : <span className="text-muted">{t('Non caricato')}</span>}</td>
                  <td className="pr-3 text-right">{b.leaveDays == null ? '—' : num(b.leaveDays)}</td>
                  <td className="pr-3 text-right">{b.permitHours == null ? '—' : num(b.permitHours)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager pg={pg} className="mt-3" />
      </Card>
    </div>
  )
}
