import { useEffect, useState } from 'react'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { Building2, CalendarHeart, History, Wallet, Pencil, Plus, Search, Trash2, Users, UsersRound } from 'lucide-react'
import clsx from 'clsx'
import { toast } from 'sonner'
import { api } from '../lib/api'
import { fmt, roleLabel, typeLabel } from '../lib/format'
import type { AbsenceType, AreaAdmin, HolidayAdmin, Role, TeamAdmin, UserAdmin } from '../lib/types'
import { Avatar, Button, Card, Field, inputCls, Modal } from '../components/ui'
import { t, t as tt } from '../lib/i18n'
import { BalancesTab } from '../components/BalancesTab'

const tabs = [
  { key: 'users', label: 'Persone', icon: Users },
  { key: 'areas', label: 'Aree', icon: Building2 },
  { key: 'teams', label: 'Team', icon: UsersRound },
  { key: 'holidays', label: 'Festività', icon: CalendarHeart },
  { key: 'balances', label: 'Saldi', icon: Wallet },
  { key: 'audit', label: 'Storico', icon: History },
] as const
type Tab = typeof tabs[number]['key']

const useInvalidate = () => { const qc = useQueryClient(); return () => qc.invalidateQueries({ queryKey: ['admin'] }) }

export function Admin() {
  const [tab, setTab] = useState<Tab>('users')
  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <header>
        <h1 className="font-display text-3xl font-bold tracking-tight md:text-4xl">{t('Amministrazione')}</h1>
        <p className="mt-1 text-sm text-muted">{t('Organizzazione, persone e chiusure aziendali.')}</p>
      </header>
      <div className="glass inline-flex flex-wrap gap-1 rounded-2xl p-1">
        {tabs.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)} className={clsx('relative flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium', tab === t.key ? 'text-white' : 'text-muted')}>
            {tab === t.key && <motion.span layoutId="admin-tab" className="btn-primary absolute inset-0 rounded-xl" />}
            <t.icon size={16} className="relative" /><span className="relative">{tt(t.label)}</span>
          </button>
        ))}
      </div>
      {tab === 'users' && <UsersTab />}
      {tab === 'areas' && <AreasTab />}
      {tab === 'teams' && <TeamsTab />}
      {tab === 'holidays' && <HolidaysTab />}
      {tab === 'balances' && <BalancesTab />}
      {tab === 'audit' && <AuditTab />}
    </div>
  )
}

const useOrg = () => {
  const users = useQuery({ queryKey: ['admin', 'users'], queryFn: () => api<UserAdmin[]>('/api/admin/users') })
  const areas = useQuery({ queryKey: ['admin', 'areas'], queryFn: () => api<AreaAdmin[]>('/api/admin/areas') })
  const teams = useQuery({ queryKey: ['admin', 'teams'], queryFn: () => api<TeamAdmin[]>('/api/admin/teams') })
  return { users: users.data ?? [], areas: areas.data ?? [], teams: teams.data ?? [] }
}

type UserForm = Omit<UserAdmin, 'id' | 'areaName' | 'teamName'> & { id?: number; password: string }
const emptyUser: UserForm = { username: '', email: '', fullName: '', role: 'USER', areaId: null, teamId: null, active: true, annualLeaveDays: 0, annualPermitHours: 0, password: '' }

function UsersTab() {
  const { users, areas, teams } = useOrg()
  const invalidate = useInvalidate()
  const [q, setQ] = useState('')
  const [edit, setEdit] = useState<UserForm | null>(null)
  const save = useMutation({
    mutationFn: (f: UserForm) => f.id
      ? api(`/api/admin/users/${f.id}`, { method: 'PUT', json: { ...f, newPassword: f.password || null } })
      : api('/api/admin/users', { method: 'POST', json: f }),
    onSuccess: () => { toast.success(t('Salvato')); setEdit(null); invalidate() },
    onError: (e: Error) => toast.error(e.message),
  })
  const list = users.filter(u => `${u.fullName} ${u.username} ${u.email} ${u.areaName ?? ''}`.toLowerCase().includes(q.toLowerCase()))
  const set = <K extends keyof UserForm>(k: K, v: UserForm[K]) => setEdit(e => e && { ...e, [k]: v })

  return (
    <Card className="p-0">
      <div className="flex flex-wrap items-center gap-3 border-b border-soft p-4">
        <div className="relative flex-1">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input className={`${inputCls} pl-9`} placeholder={t('Cerca persona, email, area…')} value={q} onChange={e => setQ(e.target.value)} />
        </div>
        <Button variant="primary" onClick={() => setEdit({ ...emptyUser })}><Plus size={16} /> {t('Nuova persona')}</Button>
      </div>
      <div className="scrollbar-thin overflow-x-auto">
        <table className="w-full min-w-[600px] text-sm">
          <thead className="text-left text-xs uppercase tracking-wider text-muted">
            <tr><th className="px-4 py-3">{t('Persona')}</th><th>{t('Ruolo')}</th><th>Area / Team</th><th className="px-4" /></tr>
          </thead>
          <tbody>
            {list.map(u => (
              <tr key={u.id} className={clsx('border-t border-soft/60 hover:bg-black/[.02] dark:hover:bg-white/[.03]', !u.active && 'opacity-50')}>
                <td className="px-4 py-3"><div className="flex items-center gap-3"><Avatar name={u.fullName} size={34} /><div><div className="font-semibold">{u.fullName}</div><div className="text-xs text-muted">{u.username} · {u.email}</div></div></div></td>
                <td>{roleLabel[u.role]}{!u.active && <span className="ml-2 text-xs text-rose-500">{t('disattivato')}</span>}</td>
                <td>{u.areaName ?? '–'}{u.teamName && <span className="text-muted"> / {u.teamName}</span>}</td>
                <td className="px-4 text-right"><button className="rounded-lg p-2 hover:bg-violet-500/10" aria-label={t('Modifica')} onClick={() => setEdit({ ...u, password: '' })}><Pencil size={15} /></button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Modal open={!!edit} onClose={() => setEdit(null)} title={edit?.id ? t('Modifica {name}', { name: edit.fullName }) : t('Nuova persona')}>
        {edit && (
          <form className="grid gap-4 sm:grid-cols-2" onSubmit={e => { e.preventDefault(); save.mutate(edit) }}>
            <Field label={t('Nome e cognome')}><input className={inputCls} required value={edit.fullName} onChange={e => set('fullName', e.target.value)} /></Field>
            <Field label="Username"><input className={inputCls} required disabled={!!edit.id} value={edit.username} onChange={e => set('username', e.target.value)} /></Field>
            <Field label="Email"><input className={inputCls} type="email" required value={edit.email} onChange={e => set('email', e.target.value)} /></Field>
            <Field label={edit.id ? t('Nuova password (facoltativa)') : t('Password iniziale')} hint={t('Almeno 10 caratteri')}>
              <input className={inputCls} type="password" minLength={10} required={!edit.id} value={edit.password} onChange={e => set('password', e.target.value)} autoComplete="new-password" />
            </Field>
            <Field label={t('Ruolo')}>
              <select className={inputCls} value={edit.role} onChange={e => set('role', e.target.value as Role)}>
                {(['USER', 'TEAM_LEAD', 'PROJECT_MANAGER', 'ADMIN'] as Role[]).map(r => <option key={r} value={r}>{roleLabel[r]}</option>)}
              </select>
            </Field>
            <Field label="Area">
              <select className={inputCls} value={edit.areaId ?? ''} onChange={e => { set('areaId', e.target.value ? Number(e.target.value) : null); set('teamId', null) }}>
                <option value="">—</option>{areas.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
            </Field>
            <Field label="Team">
              <select className={inputCls} value={edit.teamId ?? ''} onChange={e => set('teamId', e.target.value ? Number(e.target.value) : null)}>
                <option value="">—</option>{teams.filter(t => t.areaId === edit.areaId).map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
              </select>
            </Field>
            <Field label={t('Stato')}>
              <select className={inputCls} value={edit.active ? '1' : '0'} onChange={e => set('active', e.target.value === '1')}>
                <option value="1">{t('Attivo')}</option><option value="0">{t('Disattivato')}</option>
              </select>
            </Field>
            <div className="flex justify-end gap-2 sm:col-span-2">
              <Button type="button" onClick={() => setEdit(null)}>{t('Annulla')}</Button>
              <Button variant="primary" disabled={save.isPending}>{t('Salva')}</Button>
            </div>
          </form>
        )}
      </Modal>
    </Card>
  )
}

function AreasTab() {
  const { users, areas, teams } = useOrg()
  const invalidate = useInvalidate()
  const [edit, setEdit] = useState<{ id?: number; name: string; managerId: number | null } | null>(null)
  const save = useMutation({
    mutationFn: (f: NonNullable<typeof edit>) => api(f.id ? `/api/admin/areas/${f.id}` : '/api/admin/areas', { method: f.id ? 'PUT' : 'POST', json: f }),
    onSuccess: () => { toast.success(t('Area salvata')); setEdit(null); invalidate() },
    onError: (e: Error) => toast.error(e.message),
  })
  return (
    <>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {areas.map(a => (
          <Card key={a.id} className="group relative">
            <button className="absolute right-4 top-4 rounded-lg p-2 opacity-0 transition hover:bg-violet-500/10 group-hover:opacity-100" onClick={() => setEdit(a)} aria-label={t('Modifica')}><Pencil size={15} /></button>
            <div className="font-display text-xl font-semibold">{a.name}</div>
            <div className="mt-1 text-sm text-muted">PM: {a.managerName ?? t('non assegnato')}</div>
            <div className="mt-4 flex flex-wrap gap-2">
              {teams.filter(t => t.areaId === a.id).map(t => <span key={t.id} className="rounded-full bg-violet-500/10 px-3 py-1 text-xs font-medium text-violet-600 dark:text-violet-300">{t.name}</span>)}
            </div>
            <div className="mt-4 flex -space-x-2">{users.filter(u => u.areaId === a.id).slice(0, 10).map(u => <Avatar key={u.id} name={u.fullName} size={28} ring />)}</div>
          </Card>
        ))}
        <button onClick={() => setEdit({ name: '', managerId: null })} className="grid min-h-40 place-items-center rounded-3xl border-2 border-dashed border-soft text-muted transition hover:border-violet-400 hover:text-violet-500">
          <span className="flex items-center gap-2 font-semibold"><Plus size={18} /> {t('Nuova area')}</span>
        </button>
      </div>
      <Modal open={!!edit} onClose={() => setEdit(null)} title={edit?.id ? t('Modifica area') : t('Nuova area')}>
        {edit && (
          <form className="space-y-4" onSubmit={e => { e.preventDefault(); save.mutate(edit) }}>
            <Field label={t('Nome')}><input className={inputCls} required value={edit.name} onChange={e => setEdit({ ...edit, name: e.target.value })} /></Field>
            <Field label="Project Manager">
              <select className={inputCls} value={edit.managerId ?? ''} onChange={e => setEdit({ ...edit, managerId: e.target.value ? Number(e.target.value) : null })}>
                <option value="">—</option>{users.filter(u => u.role === 'PROJECT_MANAGER').map(u => <option key={u.id} value={u.id}>{u.fullName}</option>)}
              </select>
            </Field>
            <div className="flex justify-end gap-2"><Button type="button" onClick={() => setEdit(null)}>{t('Annulla')}</Button><Button variant="primary">{t('Salva')}</Button></div>
          </form>
        )}
      </Modal>
    </>
  )
}

function TeamsTab() {
  const { users, areas, teams } = useOrg()
  const invalidate = useInvalidate()
  const [edit, setEdit] = useState<{ id?: number; name: string; areaId: number | null; leaderId: number | null } | null>(null)
  const save = useMutation({
    mutationFn: (f: NonNullable<typeof edit>) => api(f.id ? `/api/admin/teams/${f.id}` : '/api/admin/teams', { method: f.id ? 'PUT' : 'POST', json: f }),
    onSuccess: () => { toast.success(t('Team salvato')); setEdit(null); invalidate() },
    onError: (e: Error) => toast.error(e.message),
  })
  return (
    <Card className="p-0">
      <div className="flex justify-end border-b border-soft p-4"><Button variant="primary" onClick={() => setEdit({ name: '', areaId: areas[0]?.id ?? null, leaderId: null })}><Plus size={16} /> {t('Nuovo team')}</Button></div>
      <ul>
        {teams.map(t => (
          <li key={t.id} className="flex items-center gap-4 border-t border-soft/60 px-4 py-3 first:border-t-0">
            <div className="grid h-10 w-10 place-items-center rounded-xl bg-violet-500/10 text-violet-500"><UsersRound size={18} /></div>
            <div className="flex-1"><div className="font-semibold">{t.name}</div><div className="text-xs text-muted">{t.areaName} · Team Manager / Tech Lead: {t.leaderName ?? '—'}</div></div>
            <span className="text-sm text-muted">{users.filter(u => u.teamId === t.id).length} {tt('persone')}</span>
            <button className="rounded-lg p-2 hover:bg-violet-500/10" onClick={() => setEdit(t)} aria-label={tt('Modifica')}><Pencil size={15} /></button>
          </li>
        ))}
      </ul>
      <Modal open={!!edit} onClose={() => setEdit(null)} title={edit?.id ? t('Modifica team') : t('Nuovo team')}>
        {edit && (
          <form className="space-y-4" onSubmit={e => { e.preventDefault(); save.mutate(edit) }}>
            <Field label={t('Nome')}><input className={inputCls} required value={edit.name} onChange={e => setEdit({ ...edit, name: e.target.value })} /></Field>
            <Field label="Area">
              <select className={inputCls} required value={edit.areaId ?? ''} onChange={e => setEdit({ ...edit, areaId: Number(e.target.value), leaderId: null })}>
                {areas.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
            </Field>
            <Field label="Team Manager / Tech Lead">
              <select className={inputCls} value={edit.leaderId ?? ''} onChange={e => setEdit({ ...edit, leaderId: e.target.value ? Number(e.target.value) : null })}>
                <option value="">—</option>{users.filter(u => u.role === 'TEAM_LEAD' && u.areaId === edit.areaId).map(u => <option key={u.id} value={u.id}>{u.fullName}</option>)}
              </select>
            </Field>
            <div className="flex justify-end gap-2"><Button type="button" onClick={() => setEdit(null)}>{t('Annulla')}</Button><Button variant="primary">{t('Salva')}</Button></div>
          </form>
        )}
      </Modal>
    </Card>
  )
}

function HolidaysTab() {
  const [year, setYear] = useState(new Date().getFullYear())
  const invalidate = useInvalidate()
  const q = useQuery({ queryKey: ['admin', 'holidays', year], queryFn: () => api<HolidayAdmin[]>(`/api/admin/holidays?year=${year}`) })
  const [date, setDate] = useState(''); const [name, setName] = useState('')
  const add = useMutation({
    mutationFn: () => api('/api/admin/holidays', { method: 'POST', json: { date, name } }),
    onSuccess: () => { toast.success(t('Chiusura aggiunta')); setDate(''); setName(''); invalidate() },
    onError: (e: Error) => toast.error(e.message),
  })
  const del = useMutation({
    mutationFn: (id: number) => api(`/api/admin/holidays/${id}`, { method: 'DELETE' }),
    onSuccess: () => invalidate(),
  })
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <Card className="lg:col-span-2">
        <div className="mb-4 flex items-center gap-2">
          <Button onClick={() => setYear(y => y - 1)}>‹</Button><span className="font-display text-lg font-semibold">{year}</span><Button onClick={() => setYear(y => y + 1)}>›</Button>
        </div>
        <ul className="grid gap-2 sm:grid-cols-2">
          {q.data?.map(h => (
            <li key={h.date} className="flex items-center gap-3 rounded-2xl border border-soft p-3">
              <div className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-rose-500/10 text-center leading-none text-rose-500">
                <div><div className="text-base font-bold">{fmt(h.date, 'd')}</div><div className="text-[10px] uppercase">{fmt(h.date, 'MMM')}</div></div>
              </div>
              <div className="flex-1"><div className="text-sm font-semibold">{h.national ? t(h.name) : h.name}</div><div className="text-xs text-muted">{h.national ? t('Festività nazionale') : t('Chiusura aziendale')} · {fmt(h.date, 'EEEE')}</div></div>
              {!h.national && h.id && <button className="rounded-lg p-2 text-muted hover:text-rose-500" onClick={() => del.mutate(h.id!)} aria-label={t('Elimina')}><Trash2 size={15} /></button>}
            </li>
          ))}
        </ul>
      </Card>
      <Card>
        <h3 className="font-display text-lg font-semibold">{t('Aggiungi chiusura')}</h3>
        <p className="mb-4 text-sm text-muted">{t('Santo patrono, ponti aziendali, chiusure estive.')}</p>
        <form className="space-y-3" onSubmit={e => { e.preventDefault(); add.mutate() }}>
          <Field label={t('Data')}><input type="date" className={inputCls} required value={date} onChange={e => setDate(e.target.value)} /></Field>
          <Field label={t('Descrizione')}><input className={inputCls} required maxLength={100} value={name} onChange={e => setName(e.target.value)} placeholder="Santa Rosalia" /></Field>
          <Button variant="primary" className="w-full" disabled={add.isPending}><Plus size={16} /> {t('Aggiungi')}</Button>
        </form>
      </Card>
    </div>
  )
}

type AuditRow = { id: number; createdAt: string; action: string; actorName: string; requestId: number | null; ownerName: string | null; requestType: string | null; startDate: string | null; endDate: string | null; detail: string | null }
export const auditLabel: Record<string, string> = {
  CREATED: 'ha creato una richiesta', APPROVED: 'ha approvato', REJECTED: 'ha rifiutato', WITHDRAWN: 'ha ritirato',
  CANCEL_REQUESTED: "ha chiesto l'annullamento", CANCELLED: "ha confermato l'annullamento", CANCEL_DENIED: "ha respinto l'annullamento",
  ATTACHMENT_ADDED: 'ha allegato un file', ATTACHMENT_REMOVED: 'ha tolto un allegato', CERTIFICATE_REMINDER: 'promemoria certificato inviato', AUTO_APPROVED: 'approvazione automatica (nessun responsabile)', AUTO_CANCELLED: 'annullamento automatico (nessun responsabile)', SICK_RECORDED: 'malattia registrata', RECORDED: 'registrata senza approvazione', CREATED_ON_BEHALF: "ha inserito un'assenza per conto di", DAYS_RECOMPUTED: 'giorni ricalcolati (chiusura aziendale)',
  USER_CREATED: 'ha creato un utente', USER_UPDATED: 'ha modificato un utente', PASSWORD_RESET: 'ha reimpostato una password', CONVERTED: 'ha trasformato le ferie ipotetiche in richiesta', BALANCES_UPLOADED: 'ha caricato i saldi residui',
}

const AUDIT_PAGE = 50

function AuditTab() {
  const [s, setS] = useState('')
  const [term, setTerm] = useState('')
  useEffect(() => { const t = setTimeout(() => setTerm(s.trim()), 350); return () => clearTimeout(t) }, [s])
  // Storico a pagine dalla piu' recente: "Carica altre" chiede le successive partendo dall'ultima riga.
  const q = useInfiniteQuery({
    queryKey: ['admin', 'audit', term],
    initialPageParam: null as number | null,
    queryFn: ({ pageParam }) => api<AuditRow[]>(`/api/admin/audit?size=${AUDIT_PAGE}${pageParam ? `&before=${pageParam}` : ''}${term ? `&q=${encodeURIComponent(term)}` : ''}`),
    getNextPageParam: last => (last.length < AUDIT_PAGE ? null : last[last.length - 1].id),
  })
  const list = (q.data?.pages ?? []).flat()
  return (
    <Card>
      <div className="mb-4 flex items-center gap-3">
        <div className="relative flex-1"><Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" /><input className={`${inputCls} pl-9`} placeholder={t('Cerca persona o dettaglio')} value={s} onChange={e => setS(e.target.value)} /></div>
        <span className="text-xs text-muted">{list.length} {t('operazioni')}{q.hasNextPage ? ` ${t('(ce ne sono altre)')}` : ''}</span>
      </div>
      {q.data && !list.length && <p className="py-6 text-center text-sm text-muted">{t('Nessuna operazione trovata.')}</p>}
      <ul className="divide-y divide-[var(--border)]">
        {list.map(e => (
          <li key={e.id} className="flex flex-wrap items-baseline gap-x-2 py-2.5 text-sm">
            <span className="w-32 shrink-0 text-xs text-muted">{fmt(e.createdAt, 'd MMM HH:mm')}</span>
            <span className="font-semibold">{e.actorName}</span>
            <span>{auditLabel[e.action] ? t(auditLabel[e.action]) : e.action}</span>
            {e.ownerName && e.ownerName !== e.actorName && <span className="text-muted">{e.action === 'CREATED_ON_BEHALF' ? e.ownerName : t('di {name}', { name: e.ownerName })}</span>}
            {e.requestType && e.startDate && <span className="text-muted">· {(typeLabel[e.requestType as AbsenceType] ?? e.requestType).toLowerCase()} {fmt(e.startDate, 'd MMM')}{e.endDate && e.endDate !== e.startDate ? ` → ${fmt(e.endDate, 'd MMM')}` : ''}</span>}
            {e.detail && !e.requestType && <span className="text-muted">· {e.detail}</span>}
          </li>
        ))}
      </ul>
      {q.hasNextPage && (
        <div className="mt-4 flex justify-center">
          <Button onClick={() => q.fetchNextPage()} disabled={q.isFetchingNextPage}>{q.isFetchingNextPage ? t('Caricamento…') : t('Carica altre operazioni')}</Button>
        </div>
      )}
    </Card>
  )
}
