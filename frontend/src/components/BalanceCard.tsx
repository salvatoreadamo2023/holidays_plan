import { useQuery } from '@tanstack/react-query'
import { Wallet } from 'lucide-react'
import { api } from '../lib/api'
import { fmt, num } from '../lib/format'
import { t } from '../lib/i18n'
import { Card } from './ui'
import { monthLabel } from './BalancesTab'

export interface MyBalance { available: boolean; referenceMonth: string | null; leaveDays: number | null; permitHours: number | null; uploadedAt: string | null; leaveRequestedSince: number | null; permitRequestedSince: number | null }

/** Saldo residuo ufficiale (busta paga) caricato dall'amministrazione: solo informativo. */
export function BalanceCard() {
  const { data: b } = useQuery({ queryKey: ['my-balance'], queryFn: () => api<MyBalance>('/api/balances/me') })
  if (!b?.available) return null
  const since = [
    Number(b.leaveRequestedSince) > 0 ? t('{n} gg di ferie', { n: num(b.leaveRequestedSince!) }) : null,
    Number(b.permitRequestedSince) > 0 ? t('{n} h di permessi', { n: num(b.permitRequestedSince!) }) : null,
  ].filter(Boolean)
  return (
    <Card className="relative overflow-hidden">
      <div className="pointer-events-none absolute -right-10 -top-10 h-28 w-28 rounded-full bg-violet-500/15 blur-2xl" />
      <div className="mb-3 flex items-center gap-2 text-sm font-semibold"><Wallet size={18} className="text-violet-600 dark:text-violet-300" />{t('Saldo residuo a {m}', { m: monthLabel(b.referenceMonth!) })}</div>
      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-2xl border border-soft p-3">
          <div className="font-display text-2xl font-bold">{b.leaveDays == null ? '—' : num(b.leaveDays)}</div>
          <div className="text-xs text-muted">{t('giorni di ferie')}</div>
        </div>
        <div className="rounded-2xl border border-soft p-3">
          <div className="font-display text-2xl font-bold">{b.permitHours == null ? '—' : num(b.permitHours)}</div>
          <div className="text-xs text-muted">{t('ore di permessi')}</div>
        </div>
      </div>
      {since.length > 0 && <p className="mt-3 text-xs">{t('Da allora hai richiesto {x}.', { x: since.join(t(' e ')) })}</p>}
      <p className="mt-2 text-[11px] text-muted">{t('Dati caricati dall\'amministrazione il {d}. Fa fede la busta paga.', { d: fmt(b.uploadedAt!, 'dd/MM/yyyy') })}</p>
    </Card>
  )
}
