import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, Download } from 'lucide-react'
import clsx from 'clsx'
import { toast } from 'sonner'
import { api, download } from '../lib/api'
import { fmt, num } from '../lib/format'
import { t } from '../lib/i18n'
import { Button, Card, Field, inputCls, Pager, SectionTitle, usePaged } from './ui'
import { monthLabel } from './BalancesTab'

interface ResidualRow {
  userId: number; fullName: string; employeeCode: string | null; pm: string | null; team: string | null; totalsFrom: string
  holidayDays: number; tentativeDays: number; clientOfficeDays: number; businessTripDays: number; generalLeaveDays: number
  permit2hHours: number; permit4hHours: number; permitOtherHours: number; permitHours: number; permitDays: number; grandTotal: number
  referenceMonth: string | null; countFrom: string | null; prevLeaveDays: number | null; prevPermitHours: number | null; prevPermitDays: number | null
  prevTotalDays: number | null; curLeaveDays: number | null; curPermitHours: number | null; apDeadline: string | null
  plannedLeaveDays: number | null; plannedPermitHours: number | null; plannedTotalDays: number | null
  estLeaveDays: number | null; estPermitHours: number | null; estTotalDays: number | null
  apToPlanDays: number | null; apCovered: boolean | null; comment: string | null
}
interface Report { from: string; to: string; start: string; end: string; rows: ResidualRow[] }

type Mode = 'fiscal' | 'solar' | 'custom'
const ym = (y: number, m: number) => `${y}-${String(m).padStart(2, '0')}`
/** Anno fiscale ottobre-settembre che contiene oggi. */
const fiscalOf = (d = new Date()) => { const y = d.getMonth() >= 9 ? d.getFullYear() : d.getFullYear() - 1; return { from: ym(y, 10), to: ym(y + 1, 9), y } }
const v = (n: number | null | undefined) => (n == null ? '—' : num(Number(n)))

/** Admin: riepilogo rimanenze ipotetiche (cedolino + calendario), con export Excel. */
export function ResidualsTab() {
  const now = new Date()
  const [mode, setMode] = useState<Mode>('fiscal')
  const [fy, setFy] = useState(fiscalOf().y)
  const [year, setYear] = useState(now.getFullYear())
  const [cFrom, setCFrom] = useState(fiscalOf().from)
  const [cTo, setCTo] = useState(fiscalOf().to)
  const [q, setQ] = useState('')
  const [onlyKo, setOnlyKo] = useState(false)

  const period = useMemo(() => mode === 'fiscal' ? { from: ym(fy, 10), to: ym(fy + 1, 9) }
    : mode === 'solar' ? { from: ym(year, 1), to: ym(year, 12) } : { from: cFrom, to: cTo }, [mode, fy, year, cFrom, cTo])
  const valid = !!period.from && !!period.to && period.from <= period.to
  const rep = useQuery({
    queryKey: ['admin-residuals', period.from, period.to],
    queryFn: () => api<Report>(`/api/admin/residuals?from=${period.from}&to=${period.to}`),
    enabled: valid,
  })
  const rows = (rep.data?.rows ?? []).filter(r => (!onlyKo || r.apCovered === false)
    && `${r.fullName} ${r.team ?? ''} ${r.pm ?? ''} ${r.employeeCode ?? ''}`.toLowerCase().includes(q.toLowerCase()))
  const pg = usePaged(rows, 10, `${q}${onlyKo}${period.from}${period.to}`)
  const ko = (rep.data?.rows ?? []).filter(r => r.apCovered === false).length
  const noPayroll = (rep.data?.rows ?? []).filter(r => !r.referenceMonth).length
  const exportXlsx = () => download(`/api/admin/residuals/export?from=${period.from}&to=${period.to}`, `residuo-pianificato_${period.from}_${period.to}.xlsx`).catch((e: Error) => toast.error(e.message))
  const years = Array.from({ length: 6 }, (_, i) => now.getFullYear() - 3 + i)
  const tab = (m: Mode, label: string) => (
    <button onClick={() => setMode(m)} className={clsx('rounded-xl px-3 py-1.5 text-sm font-medium', mode === m ? 'btn-primary text-white' : 'text-muted hover:text-[var(--text)]')}>{label}</button>
  )

  return (
    <div className="space-y-5">
      <Card>
        <SectionTitle action={<Button variant="primary" disabled={!valid || !rep.data} onClick={exportXlsx}><Download size={16} />{t('Esporta Excel')}</Button>}>{t('Rimanenze ferie e permessi')}</SectionTitle>
        <p className="mb-4 text-sm text-muted">{t('Saldi reali dell\'ultimo cedolino meno ferie (H), ferie ipotetiche (TH) e permessi in calendario dal mese del cedolino in poi. I permessi sono un\'unica colonna (ore / 8 = giorni). In regola = ha pianificato almeno l\'AP di ferie + permessi.')}</p>
        <div className="glass mb-4 inline-flex flex-wrap gap-1 rounded-2xl p-1">
          {tab('fiscal', t('Anno fiscale (ott-set)'))}{tab('solar', t('Anno solare (gen-dic)'))}{tab('custom', t('Da mese a mese'))}
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          {mode === 'fiscal' && (
            <Field label={t('Anno fiscale')}>
              <select className={inputCls} value={fy} onChange={e => setFy(Number(e.target.value))}>
                {years.map(y => <option key={y} value={y}>{t('Ott {a} - Set {b}', { a: y, b: y + 1 })}</option>)}
              </select>
            </Field>
          )}
          {mode === 'solar' && (
            <Field label={t('Anno')}>
              <select className={inputCls} value={year} onChange={e => setYear(Number(e.target.value))}>{years.map(y => <option key={y} value={y}>{y}</option>)}</select>
            </Field>
          )}
          {mode === 'custom' && <>
            <Field label={t('Da')}><input type="month" className={inputCls} value={cFrom} onChange={e => setCFrom(e.target.value)} /></Field>
            <Field label={t('A')}><input type="month" className={inputCls} value={cTo} min={cFrom} onChange={e => setCTo(e.target.value)} /></Field>
          </>}
        </div>
        {!valid && <p className="mt-3 text-sm text-rose-600">{t('Il mese finale è prima di quello iniziale')}</p>}
        {rep.error && <p className="mt-3 text-sm text-rose-600">{(rep.error as Error).message}</p>}
      </Card>

      <Card>
        <SectionTitle>{valid ? t('Dal {a} al {b}', { a: monthLabel(period.from), b: monthLabel(period.to) }) : t('Riepilogo')}</SectionTitle>
        <div className="mb-3 flex flex-wrap items-center gap-2 text-sm">
          {rep.data && <span className={clsx('rounded-full px-3 py-1 font-semibold', ko ? 'bg-rose-500/15 text-rose-600 dark:text-rose-300' : 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300')}>{t('{n} non in regola', { n: ko })}</span>}
          {noPayroll > 0 && <span className="rounded-full bg-amber-500/15 px-3 py-1 font-semibold text-amber-700 dark:text-amber-300">{t('{n} senza cedolino', { n: noPayroll })}</span>}
          <label className="ml-auto flex items-center gap-2"><input type="checkbox" className="accent-violet-600" checked={onlyKo} onChange={e => setOnlyKo(e.target.checked)} />{t('Solo non in regola')}</label>
        </div>
        <input className={`${inputCls} mb-3`} placeholder={t('Cerca persona, team, PM o matricola…')} value={q} onChange={e => setQ(e.target.value)} />
        {rep.isLoading && <p className="p-6 text-center text-sm text-muted">{t('Caricamento…')}</p>}
        <div className="overflow-auto scrollbar-thin">
          <table className="w-full min-w-[1100px] text-sm">
            <thead className="text-left text-xs uppercase tracking-wide text-muted">
              <tr>
                <th className="py-2 pr-3">{t('Persona')}</th><th className="pr-3">{t('Cedolino')}</th>
                <th className="pr-2 text-right">H</th><th className="pr-2 text-right">TH</th><th className="pr-2 text-right">CO</th>
                <th className="pr-2 text-right">{t('Perm. (gg)')}</th><th className="pr-3 text-right">Grand total</th>
                <th className="pr-2 text-right">{t('Ferie AP')}</th><th className="pr-2 text-right">{t('Perm. AP (gg)')}</th><th className="pr-3 text-right">{t('AP totale')}</th>
                <th className="pr-3 text-right">{t('Ferie anno')}</th>
                <th className="pr-3 text-right">{t('Stima a fine periodo')}</th><th className="pr-3">{t('In regola')}</th>
              </tr>
            </thead>
            <tbody>
              {pg.rows.map(r => (
                <tr key={r.userId} className="border-t border-soft align-top">
                  <td className="py-2 pr-3">
                    <div className="font-medium">{r.fullName}</div>
                    <div className="text-xs text-muted">{[r.team, r.pm && `PM ${r.pm}`, r.employeeCode && `${t('matr.')} ${r.employeeCode}`].filter(Boolean).join(' · ')}</div>
                  </td>
                  <td className="pr-3">{r.referenceMonth ? <>{monthLabel(r.referenceMonth)}<div className="text-xs text-muted">{t('conteggio dal {d}', { d: fmt(r.totalsFrom, 'd MMM yyyy') })}</div></> : <span className="text-muted">{t('Non caricato')}</span>}</td>
                  <td className="pr-2 text-right tabular-nums">{v(r.holidayDays)}</td>
                  <td className="pr-2 text-right tabular-nums">{v(r.tentativeDays)}</td>
                  <td className="pr-2 text-right tabular-nums">{v(r.clientOfficeDays)}</td>
                  <td className="pr-2 text-right tabular-nums" title={`${num(r.permitHours)} h`}>{v(r.permitDays)}</td>
                  <td className="pr-3 text-right font-semibold tabular-nums">{v(r.grandTotal)}</td>
                  <td className="pr-2 text-right tabular-nums">{v(r.prevLeaveDays)}</td>
                  <td className="pr-2 text-right tabular-nums" title={r.prevPermitHours != null ? `${num(r.prevPermitHours)} h` : ''}>{v(r.prevPermitDays)}</td>
                  <td className="pr-3 text-right font-semibold tabular-nums">{v(r.prevTotalDays)}{r.apDeadline && <div className="text-[11px] font-normal text-muted">{t('entro {d}', { d: fmt(r.apDeadline, 'MM/yyyy') })}</div>}</td>
                  <td className="pr-3 text-right tabular-nums">{v(r.curLeaveDays)}</td>
                  <td className="pr-3 text-right tabular-nums" title={r.estLeaveDays != null ? t('Ferie {f} gg · permessi {p} h', { f: num(r.estLeaveDays), p: num(r.estPermitHours ?? 0) }) : ''}>{r.estTotalDays == null ? '—' : <>{num(r.estTotalDays)} {t('gg')}</>}</td>
                  <td className="pr-3 text-xs">
                    {r.apCovered == null ? <span className="text-muted">—</span>
                      : r.apCovered ? <span className="inline-flex items-center gap-1 text-emerald-600"><CheckCircle2 size={14} />{t('Sì')}</span>
                        : <span className="inline-flex items-start gap-1 text-rose-600 dark:text-rose-300"><AlertTriangle size={14} className="mt-0.5 shrink-0" />{t('Mancano {n} gg', { n: num(r.apToPlanDays ?? 0) })}</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager pg={pg} className="mt-3" />
        <p className="mt-3 text-xs text-muted">{t('Stima a fine periodo = saldo del cedolino + maturazione mensile fino a fine periodo − ferie, TH e permessi pianificati dal mese del cedolino. Passa sopra al valore per vedere ferie e permessi separati.')}</p>
      </Card>
    </div>
  )
}
