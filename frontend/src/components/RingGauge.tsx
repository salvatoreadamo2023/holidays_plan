import { motion } from 'framer-motion'
import { num } from '../lib/format'
import { t } from '../lib/i18n'

/** Anello animato: consumato (pieno) + in attesa (tratteggiato) su totale. */
export function RingGauge({ total, used, pending, unit, label, from, to, size = 168 }:
  { total: number; used: number; pending: number; unit: string; label: string; from: string; to: string; size?: number }) {
  const r = size / 2 - 14
  const c = 2 * Math.PI * r
  const usedPct = total > 0 ? Math.min(1, used / total) : 0
  const pendPct = total > 0 ? Math.min(1 - usedPct, pending / total) : 0
  const available = total - used - pending
  const id = label.replace(/\W/g, '')
  return (
    <div className="flex flex-col items-center">
      <div className="relative" style={{ width: size, height: size }}>
        <svg width={size} height={size} className="-rotate-90">
          <defs>
            <linearGradient id={`g-${id}`} x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stopColor={from} /><stop offset="1" stopColor={to} />
            </linearGradient>
          </defs>
          <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--grid)" strokeWidth="14" />
          <motion.circle
            cx={size / 2} cy={size / 2} r={r} fill="none" stroke={`url(#g-${id})`} strokeWidth="14" strokeLinecap="round"
            strokeDasharray={c} initial={{ strokeDashoffset: c }} animate={{ strokeDashoffset: c * (1 - usedPct) }}
            transition={{ duration: 1.4, ease: [0.22, 1, 0.36, 1] }}
          />
          {pendPct > 0 && (
            <motion.circle
              cx={size / 2} cy={size / 2} r={r} fill="none" stroke={from} strokeOpacity=".45" strokeWidth="14" strokeDasharray={`${c * pendPct} ${c}`}
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 1.1 }}
              style={{ transform: `rotate(${usedPct * 360}deg)`, transformOrigin: 'center' }}
            />
          )}
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <motion.span className="font-display text-4xl font-bold tracking-tight" initial={{ opacity: 0, scale: .6 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: .3, type: 'spring' }}>
            {num(available)}
          </motion.span>
          <span className="text-xs text-muted">{unit} {t('disponibili')}</span>
        </div>
      </div>
      <p className="mt-3 font-semibold">{label}</p>
      <p className="text-xs text-muted">{num(used)} {t('usati')} · {num(pending)} {t('in attesa')} · {num(total)} {t('totali')}</p>
    </div>
  )
}
