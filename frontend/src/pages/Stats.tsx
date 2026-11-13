import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { BarChart3, CalendarRange, ChevronLeft, ChevronRight, Clock, Building2, Briefcase, Plane, Stethoscope, Users, Grid3x3, PlaneTakeoff } from 'lucide-react'
import clsx from 'clsx'
import { api } from '../lib/api'
import { num, reasonLabel, typeEmoji, typeLabel } from '../lib/format'
import type { LeaveReason } from '../lib/types'
import { Avatar, Card, Empty } from '../components/ui'

type T = 'FERIE' | 'PERMESSO' | 'GENERAL_LEAVE' | 'CLIENT_OFFICE' | 'BUSINESS_TRIP'
interface Series { months: number[]; total: number }
interface Stats {
  year: number; today: string; years: number[]; people: number
  types: Record<T, Series>
  reasons: { reason: LeaveReason; label: string; series: Series }[]
  teams: { id: number | null; name: string; people: number; types: Record<T, Series>; reasons: Stats['reasons'] }[]
  outToday: { userId: number; name: string; team: string | null; type: T; reason: string | null }[]
}
type Period = 'M' | 'Y' | 'T'

const TYPES: T[] = ['FERIE', 'PERMESSO', 'GENERAL_LEAVE', 'CLIENT_OFFICE', 'BUSINESS_TRIP']
const ABSENCE: T[] = ['FERIE', 'PERMESSO', 'GENERAL_LEAVE']
const MONTHS = ['Gen', 'Feb', 'Mar', 'Apr', 'Mag', 'Giu', 'Lug', 'Ago', 'Set', 'Ott', 'Nov', 'Dic']
const MONTHS_FULL = ['Gennaio', 'Febbraio', 'Marzo', 'Aprile', 'Maggio', 'Giugno', 'Luglio', 'Agosto', 'Settembre', 'Ottobre', 'Novembre', 'Dicembre']
const META: Record<T, { short: string; unit: string; icon: typeof Plane; color: string }> = {
  FERIE: { short: 'Ferie', unit: 'gg', icon: Plane, color: '#8b5cf6' },
  PERMESSO: { short: 'Permessi', unit: 'ore', icon: Clock, color: '#f59e0b' },
  GENERAL_LEAVE: { short: 'General leave', unit: 'gg', icon: Stethoscope, color: '#f43f5e' },
  CLIENT_OFFICE: { short: 'CO · Cliente', unit: 'gg', icon: Building2, color: '#10b981' },
  BUSINESS_TRIP: { short: 'BT · Trasferta', unit: 'gg', icon: Briefcase, color: '#84cc16' },
}
const sum = (a: number[]) => a.reduce((x, y) => x + Number(y), 0)
/** Permessi in giorni: 8 ore = 1 giorno. */
const asDays = (t: T, v: number) => (t === 'PERMESSO' ? v / 8 : v)

export default function StatsPage() {
  const now = new Date()
  const [period, setPeriod] = useState<Period>('M')
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [type, setType] = useState<T | 'ALL'>('ALL')
  const [reason, setReason] = useState<LeaveReason | ''>('')
  const [team, setTeam] = useState<string>('ALL')
  const [heatType, setHeatType] = useState<T>('FERIE')

  const q = useQuery({
    queryKey: ['stats', year, reason],
    queryFn: () => api<Stats>(`/api/admin/stats?year=${year}${reason ? `&reason=${reason}` : ''}`),
    placeholderData: prev => prev,
  })
  const s = q.data
  const teamKey = (id: number | null) => (id == null ? 'none' : String(id))
  const selTeam = s?.teams.find(t => teamKey(t.id) === team)
  const scopeTypes = selTeam ? selTeam.types : s?.types
  const scopePeople = selTeam ? selTeam.people : s?.people ?? 0
  const shown = type === 'ALL' ? TYPES : [type]

  const val = (x: Series | undefined, p: Period = period) =>
    !x ? 0 : p === 'M' ? Number(x.months[month - 1]) : p === 'Y' ? sum(x.months) : Number(x.total)
  const periodLabel = period === 'M' ? `${MONTHS_FULL[month - 1]} ${year}` : period === 'Y' ? String(year) : 'Totale storico'

  const move = (d: number) => {
    if (period === 'Y') return setYear(y => y + d)
    let m = month + d, y = year
    if (m < 1) { m = 12; y-- } else if (m > 12) { m = 1; y++ }
    setMonth(m); setYear(y)
  }

  const absenceDays = (types: Record<T, Series> | undefined, p: Period = period) =>
    !types ? 0 : ABSENCE.filter(t => shown.includes(t)).reduce((a, t) => a + asDays(t, val(types[t], p)), 0)

  const chart = useMemo(() => {
    if (!scopeTypes) return { bars: [] as { m: number; parts: { t: T; v: number }[]; tot: number }[], max: 1 }
    const bars = MONTHS.map((_, m) => {
      const parts = shown.map(t => ({ t, v: asDays(t, Number(scopeTypes[t].months[m])) }))
      return { m, parts, tot: parts.reduce((a, p) => a + p.v, 0) }
    })
    return { bars, max: Math.max(1, ...bars.map(b => b.tot)) }
  }, [scopeTypes, shown.join()])

  const teamsShown = (s?.teams ?? []).filter(t => team === 'ALL' || teamKey(t.id) === team)
  const out = (s?.outToday ?? []).filter(o => !selTeam || o.team === selTeam.name || (selTeam.id == null && !o.team))
  const reasons = ((selTeam ? selTeam.reasons : s?.reasons) ?? []).map(r => ({ ...r, v: val(r.series) })).filter(r => r.v > 0).sort((a, b) => b.v - a.v)
  const reasonTot = reasons.reduce((a, r) => a + r.v, 0)
  const heat = teamsShown.map(t => ({ t, cells: t.types[heatType].months.map(v => (t.people ? Number(v) / t.people : 0)) }))
  const heatMax = Math.max(0.0001, ...heat.flatMap(h => h.cells))

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="text-sm text-muted">Admin · {selTeam ? selTeam.name : "tutta l'azienda"} · {scopePeople} persone</div>
          <h1 className="font-display text-3xl font-bold tracking-tight md:text-4xl">Statistiche assenze</h1>
        </div>
        <Link to="/calendario" className="btn-primary inline-flex items-center gap-2 rounded-2xl px-4 py-2.5 text-sm font-semibold text-white"><CalendarRange size={16} /> Apri calendario</Link>
      </div>

      <Card className="flex flex-wrap items-center gap-2 p-3">
        <div className="flex flex-wrap gap-1.5">
          {(['ALL', ...TYPES] as const).map(t => (
            <button key={t} onClick={() => setType(t)} className={clsx('flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium', type === t ? 'btn-primary border-transparent text-white' : 'border-soft')}>
              {t !== 'ALL' && <span className="h-2 w-2 rounded-full" style={{ background: META[t].color }} />}
              {t === 'ALL' ? 'Tutti' : t === 'CLIENT_OFFICE' ? 'CO' : t === 'BUSINESS_TRIP' ? 'BT' : META[t].short}
            </button>
          ))}
        </div>
        {(type === 'ALL' || type === 'GENERAL_LEAVE') && (
          <select value={reason} onChange={e => setReason(e.target.value as LeaveReason | '')} className="rounded-full border border-soft bg-transparent px-3 py-1.5 text-sm" aria-label="Tipologia General leave">
            <option value="">General leave: tutte le tipologie</option>
            {(Object.keys(reasonLabel) as LeaveReason[]).map(r => <option key={r} value={r}>{reasonLabel[r]}</option>)}
          </select>
        )}
        <div className="flex rounded-full border border-soft p-0.5">
          {(['M', 'Y', 'T'] as Period[]).map(p => (
            <button key={p} onClick={() => setPeriod(p)} className={clsx('rounded-full px-3 py-1 text-sm font-medium', period === p ? 'btn-primary text-white' : 'text-muted')}>
              {p === 'M' ? 'Mese' : p === 'Y' ? 'Anno' : 'Totale'}
            </button>
          ))}
        </div>
        {period !== 'T' && (
          <div className="flex items-center gap-1 rounded-full border border-soft px-1 py-0.5">
            <button onClick={() => move(-1)} className="rounded-full p-1" aria-label="Precedente"><ChevronLeft size={16} /></button>
            <span className="min-w-32 text-center text-sm font-semibold">{periodLabel}</span>
            <button onClick={() => move(1)} className="rounded-full p-1" aria-label="Successivo"><ChevronRight size={16} /></button>
          </div>
        )}
        <select value={team} onChange={e => setTeam(e.target.value)} className="rounded-full border border-soft bg-transparent px-3 py-1.5 text-sm" aria-label="Team">
          <option value="ALL">Tutti i team</option>
          {s?.teams.map(t => <option key={teamKey(t.id)} value={teamKey(t.id)}>{t.name}</option>)}
        </select>
      </Card>

      {q.isError && <Card><Empty emoji="⚠️" title="Statistiche non disponibili" text={(q.error as Error).message} /></Card>}
      {s && scopeTypes && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
            {shown.map(t => {
              const M = META[t]
              return (
                <Card key={t} className="p-4">
                  <div className="flex items-center gap-3">
                    <div className="grid h-9 w-9 place-items-center rounded-xl text-white" style={{ background: M.color }}><M.icon size={18} /></div>
                    <div className="min-w-0 leading-tight"><div className="truncate font-semibold">{M.short}</div><div className="text-xs text-muted">{periodLabel}</div></div>
                  </div>
                  <div className="mt-3 flex items-baseline gap-1.5">
                    <span className="font-display text-4xl font-bold">{num(val(scopeTypes[t]))}</span><span className="text-muted">{M.unit}</span>
                    {(t === 'CLIENT_OFFICE' || t === 'BUSINESS_TRIP') && <span className="ml-1 rounded-full bg-emerald-500/15 px-2 py-0.5 text-[11px] font-semibold text-emerald-600 dark:text-emerald-300">presenza</span>}
                  </div>
                  <div className="mt-3 grid grid-cols-2 gap-2 border-t border-soft pt-2 text-xs text-muted">
                    <div>Anno {year}<div className="text-sm font-semibold text-[var(--text)]">{num(val(scopeTypes[t], 'Y'))} {M.unit}</div></div>
                    <div>Totale<div className="text-sm font-semibold text-[var(--text)]">{num(val(scopeTypes[t], 'T'))} {M.unit}</div></div>
                  </div>
                </Card>
              )
            })}
          </div>
          <p className="-mt-3 text-xs text-muted">
            Giorni di assenza nel periodo: <b className="text-[var(--text)]">{num(absenceDays(scopeTypes))}</b>
            {scopePeople ? <> · {num(absenceDays(scopeTypes) / scopePeople)} a persona</> : null}. Contano solo le richieste approvate e i giorni lavorativi; i permessi valgono 8 ore = 1 giorno; CO e BT sono presenze e non entrano nelle assenze.
          </p>

          <div className="grid gap-6 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
                <h2 className="flex items-center gap-2 font-display text-lg font-bold"><BarChart3 size={18} /> Andamento {year} per mese</h2>
                <div className="flex flex-wrap gap-3 text-xs text-muted">{shown.map(t => <span key={t} className="flex items-center gap-1"><span className="h-2 w-2 rounded-full" style={{ background: META[t].color }} />{META[t].short}</span>)}</div>
              </div>
              <div className="flex h-56 items-end gap-1.5 sm:gap-2">
                {chart.bars.map(b => {
                  const current = b.m === month - 1 && period === 'M'
                  return (
                    <button key={b.m} onClick={() => { setPeriod('M'); setMonth(b.m + 1) }} className="group flex h-full flex-1 flex-col items-center justify-end gap-1" title={`${MONTHS_FULL[b.m]}: ${num(b.tot)} gg`}>
                      <span className={clsx('text-[10px] tabular-nums', current ? 'font-bold' : 'text-muted opacity-0 group-hover:opacity-100')}>{num(b.tot)}</span>
                      <div className={clsx('flex w-full flex-col-reverse overflow-hidden rounded-md', current && 'ring-2 ring-[var(--text)] ring-offset-2 ring-offset-transparent')} style={{ height: `${(b.tot / chart.max) * 85}%`, minHeight: b.tot ? 3 : 0 }}>
                        {b.parts.map(p => p.v > 0 && <div key={p.t} style={{ flexGrow: p.v, background: META[p.t].color }} />)}
                      </div>
                      <span className={clsx('text-[11px]', current ? 'font-bold' : 'text-muted')}>{MONTHS[b.m]}</span>
                    </button>
                  )
                })}
              </div>
              <p className="mt-3 text-xs text-muted">In giorni (permessi: 8 ore = 1 giorno). Clic su un mese per vederlo nel dettaglio.</p>
            </Card>

            <Card>
              <h2 className="mb-1 flex items-center gap-2 font-display text-lg font-bold"><Stethoscope size={18} /> General leave per tipologia</h2>
              <div className="mb-3 text-xs text-muted">{periodLabel}{selTeam ? ` · ${selTeam.name}` : ''}</div>
              {!reasons.length && <p className="text-sm text-muted">Nessun General leave nel periodo.</p>}
              {reasons.map(r => (
                <div key={r.reason} className="mb-3">
                  <div className="flex justify-between text-sm"><span>{r.label}</span><span><b>{num(r.v)}</b> gg · {Math.round((r.v / reasonTot) * 100)}%</span></div>
                  <div className="mt-1 h-1.5 rounded-full bg-[var(--grid)]"><div className="h-full rounded-full bg-rose-500" style={{ width: `${(r.v / reasons[0].v) * 100}%` }} /></div>
                </div>
              ))}
            </Card>
          </div>

          <Card className="overflow-x-auto">
            <h2 className="mb-3 flex items-center gap-2 font-display text-lg font-bold"><Users size={18} /> Divisione per team <span className="text-sm font-normal text-muted">· {periodLabel}</span></h2>
            <table className="w-full min-w-[760px] text-sm">
              <thead><tr className="text-left text-xs uppercase tracking-wider text-muted">
                <th className="py-2">Team</th><th className="text-right">Persone</th>
                {shown.map(t => <th key={t} className="text-right">{t === 'CLIENT_OFFICE' ? 'CO' : t === 'BUSINESS_TRIP' ? 'BT' : META[t].short}</th>)}
                <th className="pl-4">Composizione</th><th className="text-right">Gg assenza / pers.</th><th className="pl-4">Fuori oggi</th>
              </tr></thead>
              <tbody>
                {teamsShown.map(t => {
                  const parts = shown.map(x => ({ x, v: asDays(x, val(t.types[x])) }))
                  const tot = parts.reduce((a, p) => a + p.v, 0)
                  const maxTot = Math.max(1, ...teamsShown.map(o => shown.reduce((a, x) => a + asDays(x, val(o.types[x])), 0)))
                  const outT = (s.outToday).filter(o => o.team === t.name || (t.id == null && !o.team))
                  return (
                    <tr key={teamKey(t.id)} className="border-t border-soft">
                      <td className="py-3 font-semibold">{t.name}</td>
                      <td className="text-right tabular-nums">{t.people}</td>
                      {shown.map(x => (
                        <td key={x} className="text-right tabular-nums">{num(val(t.types[x]))} <span className="text-xs text-muted">{META[x].unit}</span>
                          {period !== 'Y' && <div className="text-[11px] text-muted">anno {num(val(t.types[x], 'Y'))}</div>}</td>
                      ))}
                      <td className="pl-4"><div className="flex h-2 overflow-hidden rounded-full bg-[var(--grid)]" style={{ width: `${Math.max(8, (tot / maxTot) * 100)}%` }}>
                        {parts.map(p => p.v > 0 && <div key={p.x} style={{ flexGrow: p.v, background: META[p.x].color }} />)}</div></td>
                      <td className="text-right tabular-nums">{t.people ? num(absenceDays(t.types) / t.people) : '–'}</td>
                      <td className="pl-4"><div className="flex -space-x-2">{outT.slice(0, 5).map(o => <span key={o.userId} title={`${o.name} · ${typeLabel[o.type]}`}><Avatar name={o.name} size={26} ring /></span>)}{!outT.length && <span className="text-muted">–</span>}</div></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </Card>

          <div className="grid gap-6 lg:grid-cols-3">
            <Card className="overflow-x-auto lg:col-span-2">
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <h2 className="flex items-center gap-2 font-display text-lg font-bold"><Grid3x3 size={18} /> Team × mese <span className="text-sm font-normal text-muted">· media a persona {year}</span></h2>
                <div className="flex gap-1">{TYPES.map(t => (
                  <button key={t} onClick={() => setHeatType(t)} className={clsx('rounded-full border px-2.5 py-1 text-xs font-medium', heatType === t ? 'btn-primary border-transparent text-white' : 'border-soft')}>
                    {t === 'FERIE' ? 'Ferie' : t === 'PERMESSO' ? 'Perm. (ore)' : t === 'GENERAL_LEAVE' ? 'GL' : t === 'CLIENT_OFFICE' ? 'CO' : 'BT'}</button>
                ))}</div>
              </div>
              <table className="w-full min-w-[640px] border-separate border-spacing-1 text-xs">
                <thead><tr><th />{MONTHS.map((m, i) => <th key={m} className={clsx('font-medium', i === month - 1 && period === 'M' ? 'text-[var(--text)]' : 'text-muted')}>{m}</th>)}</tr></thead>
                <tbody>{heat.map(h => (
                  <tr key={teamKey(h.t.id)}>
                    <td className="whitespace-nowrap pr-2 text-sm">{h.t.name}</td>
                    {h.cells.map((v, i) => (
                      <td key={i} className="h-8 rounded-md text-center tabular-nums" style={{ background: v ? `color-mix(in oklab, ${META[heatType].color} ${15 + (v / heatMax) * 75}%, transparent)` : 'var(--grid)', color: v / heatMax > .55 ? '#fff' : undefined }}
                        title={`${h.t.name} · ${MONTHS_FULL[i]}: ${num(v)} ${META[heatType].unit} a persona`}>{v ? num(v) : ''}</td>
                    ))}
                  </tr>
                ))}</tbody>
              </table>
            </Card>

            <Card>
              <h2 className="mb-3 flex items-center justify-between font-display text-lg font-bold"><span className="flex items-center gap-2"><PlaneTakeoff size={18} /> Fuori oggi</span><span className="text-sm font-normal text-muted">{out.length} {out.length === 1 ? 'persona' : 'persone'}</span></h2>
              {!out.length && <p className="text-sm text-muted">Oggi sono tutti in ufficio.</p>}
              <div className="space-y-2">{out.map(o => (
                <div key={`${o.userId}-${o.type}`} className="flex items-center gap-3">
                  <Avatar name={o.name} size={32} />
                  <div className="min-w-0 flex-1 leading-tight"><div className="truncate font-medium">{o.name}</div><div className="text-xs text-muted">{o.team ?? 'Senza team'}</div></div>
                  <span className="rounded-full border border-soft px-2 py-0.5 text-xs">{typeEmoji[o.type]} {o.reason ?? (o.type === 'CLIENT_OFFICE' ? 'CO' : o.type === 'BUSINESS_TRIP' ? 'BT' : typeLabel[o.type])}</span>
                </div>
              ))}</div>
            </Card>
          </div>
        </>
      )}
    </div>
  )
}
