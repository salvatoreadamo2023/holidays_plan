import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { ArrowRight, CheckCheck, Plane, Plus, Thermometer } from 'lucide-react'
import { Link } from 'react-router'
import { differenceInCalendarDays, parseISO } from 'date-fns'
import { api } from '../lib/api'
import { useAuth, isManager } from '../lib/auth'
import { useUi } from '../lib/ui'
import { num, range, typeLabel } from '../lib/format'
import type { Dashboard as D, RequestView } from '../lib/types'
import { Avatar, Card, Empty, SectionTitle, StatusChip, TypeIcon } from '../components/ui'
import { RingGauge } from '../components/RingGauge'
import { YearHeatmap } from '../components/YearHeatmap'

const greet = () => { const h = new Date().getHours(); return h < 12 ? 'Buongiorno' : h < 18 ? 'Buon pomeriggio' : 'Buonasera' }

const stagger = { hidden: {}, show: { transition: { staggerChildren: .07 } } }
const rise = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0 } }

export function Dashboard() {
  const { user } = useAuth()
  const { openRequest } = useUi()
  const dash = useQuery({ queryKey: ['dashboard'], queryFn: () => api<D>('/api/dashboard') })
  const mine = useQuery({ queryKey: ['mine'], queryFn: () => api<RequestView[]>('/api/requests/mine') })
  const d = dash.data
  const next = d?.upcoming.find(r => r.status !== 'PENDING')
  const daysTo = next ? differenceInCalendarDays(parseISO(next.startDate), new Date()) : null

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="mx-auto max-w-6xl space-y-6">
      <motion.header variants={rise} className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm text-muted">{greet()},</p>
          <h1 className="font-display text-4xl font-bold tracking-tight md:text-5xl">{user?.fullName.split(' ')[0]} <span className="inline-block origin-[70%_70%] animate-[wave_2.5s_ease-in-out_infinite]">👋</span></h1>
        </div>
        <button onClick={() => openRequest()} className="btn-primary hidden items-center gap-2 rounded-2xl px-5 py-3 text-sm font-semibold md:flex"><Plus size={18} /> Pianifica una pausa</button>
      </motion.header>

      {/* Hero: conto alla rovescia */}
      <motion.div variants={rise}>
        <div className="relative overflow-hidden rounded-[2rem] p-6 text-white shadow-2xl md:p-8" style={{ background: 'linear-gradient(120deg,#7c3aed,#4f46e5 45%,#0891b2)' }}>
          <div className="pointer-events-none absolute -right-20 -top-24 h-72 w-72 rounded-full bg-white/15 blur-3xl" />
          <div className="pointer-events-none absolute -bottom-24 left-1/3 h-64 w-64 rounded-full bg-fuchsia-400/30 blur-3xl" />
          <motion.div className="pointer-events-none absolute right-8 top-6 hidden text-7xl md:block" animate={{ y: [0, -10, 0], rotate: [0, 4, 0] }} transition={{ repeat: Infinity, duration: 5 }}>🏝️</motion.div>
          {next && daysTo != null ? (
            <div className="relative">
              <p className="text-sm font-medium text-white/80">La tua prossima pausa</p>
              <div className="mt-2 flex items-baseline gap-3">
                <span className="font-display text-6xl font-bold tracking-tighter md:text-7xl">{daysTo <= 0 ? 'Oggi!' : daysTo}</span>
                {daysTo > 0 && <span className="text-lg text-white/80">{daysTo === 1 ? 'giorno' : 'giorni'} a {typeLabel[next.type].toLowerCase()}</span>}
              </div>
              <p className="mt-2 text-white/85">{range(next)}</p>
            </div>
          ) : (
            <div className="relative">
              <p className="text-sm font-medium text-white/80">Nessuna pausa in programma</p>
              <p className="mt-2 font-display text-3xl font-bold md:text-4xl">È il momento di staccare un po'?</p>
              <button onClick={() => openRequest()} className="mt-5 inline-flex items-center gap-2 rounded-xl bg-white/20 px-4 py-2.5 text-sm font-semibold backdrop-blur hover:bg-white/30">Prenota le ferie <ArrowRight size={16} /></button>
            </div>
          )}
        </div>
      </motion.div>

      <div className="grid gap-6 lg:grid-cols-3">
        <motion.div variants={rise} className="lg:col-span-2">
          <Card className="h-full">
            <SectionTitle>Il tuo saldo {d?.balance.year}</SectionTitle>
            {d && (
              <div className="grid gap-8 sm:grid-cols-2">
                <RingGauge label="Ferie" unit="giorni" total={d.balance.leaveTotal} used={d.balance.leaveUsed} pending={d.balance.leavePending} from="#8b5cf6" to="#06b6d4" />
                <RingGauge label="Permessi" unit="ore" total={d.balance.permitTotal} used={d.balance.permitUsed} pending={d.balance.permitPending} from="#f59e0b" to="#f43f5e" />
              </div>
            )}
            {d && d.balance.sickDays > 0 && (
              <p className="mt-6 flex items-center gap-2 text-sm text-muted"><Thermometer size={15} /> {num(d.balance.sickDays)} giorni di malattia registrati quest'anno</p>
            )}
          </Card>
        </motion.div>

        <motion.div variants={rise} className="space-y-6">
          {isManager(user) && (
            <Link to="/approvazioni" className="group block">
              <Card className="relative overflow-hidden transition group-hover:-translate-y-0.5">
                <div className="pointer-events-none absolute -right-8 -top-8 h-28 w-28 rounded-full bg-fuchsia-500/25 blur-2xl" />
                <div className="flex items-center gap-4">
                  <div className="grid h-12 w-12 place-items-center rounded-2xl bg-gradient-to-br from-fuchsia-500 to-violet-600 text-white shadow-lg shadow-fuchsia-500/30"><CheckCheck size={22} /></div>
                  <div className="flex-1">
                    <div className="font-display text-3xl font-bold">{d?.toApprove ?? '–'}</div>
                    <div className="text-sm text-muted">richieste da valutare</div>
                  </div>
                  <ArrowRight className="text-muted transition group-hover:translate-x-1" />
                </div>
              </Card>
            </Link>
          )}
          <Card>
            <SectionTitle><span className="flex items-center gap-2"><Plane size={18} /> Fuori oggi</span></SectionTitle>
            {d && d.absentToday.length > 0 ? (
              <ul className="space-y-2.5">
                {d.absentToday.map(n => <li key={n} className="flex items-center gap-3 text-sm"><Avatar name={n} size={32} /> {n}</li>)}
              </ul>
            ) : <p className="text-sm text-muted">Tutti presenti nella tua area. ✨</p>}
          </Card>
        </motion.div>
      </div>

      <motion.div variants={rise}>
        <Card>
          <SectionTitle action={<Link to="/richieste" className="text-sm font-medium text-violet-500 hover:underline">Vedi tutte</Link>}>Prossime assenze</SectionTitle>
          {d && d.upcoming.length === 0 && <Empty emoji="🗓️" title="Calendario libero" text="Non hai richieste in programma." />}
          <div className="grid gap-3 md:grid-cols-2">
            {d?.upcoming.map(r => (
              <div key={r.id} className="flex items-center gap-4 rounded-2xl border border-soft p-3.5">
                <TypeIcon type={r.type} />
                <div className="min-w-0 flex-1">
                  <div className="font-semibold">{typeLabel[r.type]} · {num(r.amount)} {r.type === 'PERMESSO' ? 'h' : 'gg'}</div>
                  <div className="truncate text-sm text-muted">{range(r)}</div>
                </div>
                <StatusChip status={r.status} />
              </div>
            ))}
          </div>
        </Card>
      </motion.div>

      <motion.div variants={rise}>
        <Card>
          <SectionTitle>Il tuo anno a colpo d'occhio</SectionTitle>
          {mine.data && <YearHeatmap year={new Date().getFullYear()} requests={mine.data} />}
        </Card>
      </motion.div>
    </motion.div>
  )
}
