import { motion, AnimatePresence } from 'framer-motion'
import { X } from 'lucide-react'
import clsx from 'clsx'
import type { ReactNode } from 'react'
import { avatarHue, initials, statusLabel, typeEmoji, typeLabel } from '../lib/format'
import type { AbsenceType, Status } from '../lib/types'
import { t } from '../lib/i18n'

export function Avatar({ name, size = 36, ring = false }: { name: string; size?: number; ring?: boolean }) {
  const h = avatarHue(name)
  return (
    <div
      title={name}
      className={clsx('grid shrink-0 place-items-center rounded-full font-semibold text-white', ring && 'ring-2 ring-[var(--bg)]')}
      style={{
        width: size, height: size, fontSize: size * 0.38,
        background: `linear-gradient(135deg, hsl(${h} 80% 62%), hsl(${(h + 40) % 360} 80% 52%))`,
      }}
    >
      {initials(name)}
    </div>
  )
}

const statusStyle: Record<Status, string> = {
  PENDING: 'bg-amber-500/15 text-amber-600 dark:text-amber-300 ring-amber-500/30',
  APPROVED: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 ring-emerald-500/30',
  REJECTED: 'bg-rose-500/15 text-rose-600 dark:text-rose-300 ring-rose-500/30',
  WITHDRAWN: 'bg-slate-500/15 text-slate-600 dark:text-slate-300 ring-slate-500/30',
  CANCEL_REQUESTED: 'bg-orange-500/15 text-orange-600 dark:text-orange-300 ring-orange-500/30',
  CANCELLED: 'bg-slate-500/15 text-slate-600 dark:text-slate-300 ring-slate-500/30',
}

export function StatusChip({ status }: { status: Status }) {
  return (
    <span className={clsx('inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ring-1', statusStyle[status])}>
      <span className={clsx('h-1.5 w-1.5 rounded-full bg-current', status === 'PENDING' && 'animate-pulse')} />
      {statusLabel[status]}
    </span>
  )
}

export function TypeBadge({ type }: { type: AbsenceType | 'ASSENZA' }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-sm font-medium">
      <span className={clsx('h-2.5 w-2.5 rounded-full', `bar-${type}`)} />
      {typeLabel[type]}
    </span>
  )
}

export function TypeIcon({ type, size = 44 }: { type: AbsenceType | 'ASSENZA'; size?: number }) {
  return (
    <div className={clsx('grid shrink-0 place-items-center rounded-2xl shadow-lg', `bar-${type}`)} style={{ width: size, height: size, fontSize: size * 0.45 }}>
      <span aria-hidden>{typeEmoji[type]}</span>
    </div>
  )
}

export function Card({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={clsx('glass rounded-3xl p-5 shadow-[0_8px_40px_-20px_rgba(0,0,0,.25)]', className)}>{children}</div>
}

export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="font-display text-lg font-semibold tracking-tight">{children}</h2>
      {action}
    </div>
  )
}

export function Button({ children, variant = 'ghost', className, ...rest }:
  React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'ghost' | 'danger' | 'success' }) {
  return (
    <button
      {...rest}
      className={clsx(
        'inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold transition active:scale-[.97] disabled:pointer-events-none disabled:opacity-50',
        variant === 'primary' && 'btn-primary',
        variant === 'ghost' && 'glass hover:bg-black/5 dark:hover:bg-white/10',
        variant === 'danger' && 'bg-rose-500/90 text-white hover:bg-rose-500',
        variant === 'success' && 'bg-emerald-500/90 text-white hover:bg-emerald-500',
        className,
      )}
    >
      {children}
    </button>
  )
}

export function Modal({ open, onClose, title, children, side = false }:
  { open: boolean; onClose: () => void; title: string; children: ReactNode; side?: boolean }) {
  return (
    <AnimatePresence>
      {open && (
        <motion.div className="fixed inset-0 z-50 flex" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
          <motion.div
            role="dialog" aria-modal="true" aria-label={title}
            className={clsx('glass-strong relative z-10 flex flex-col overflow-hidden shadow-2xl',
              side ? 'ml-auto h-full w-full max-w-md rounded-l-3xl' : 'm-auto max-h-[90vh] w-[min(560px,calc(100%-32px))] rounded-3xl')}
            initial={side ? { x: 480 } : { y: 24, scale: .97 }}
            animate={side ? { x: 0 } : { y: 0, scale: 1 }}
            exit={side ? { x: 480 } : { y: 24, scale: .97, opacity: 0 }}
            transition={{ type: 'spring', damping: 30, stiffness: 320 }}
          >
            <div className="flex items-center justify-between border-b border-soft px-6 py-4">
              <h3 className="font-display text-lg font-semibold">{title}</h3>
              <button onClick={onClose} className="rounded-lg p-1.5 hover:bg-black/5 dark:hover:bg-white/10" aria-label={t('Chiudi')}><X size={18} /></button>
            </div>
            <div className="scrollbar-thin flex-1 overflow-y-auto px-6 py-5">{children}</div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

export const inputCls = 'w-full rounded-xl border border-soft bg-white/60 px-3.5 py-2.5 text-sm outline-none transition focus:border-violet-400 focus:ring-4 focus:ring-violet-500/15 dark:bg-white/5'

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-muted">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-muted">{hint}</span>}
    </label>
  )
}

export function Empty({ emoji, title, text }: { emoji: string; title: string; text?: string }) {
  return (
    <div className="flex flex-col items-center gap-2 py-12 text-center">
      <motion.div className="text-5xl" animate={{ y: [0, -8, 0] }} transition={{ repeat: Infinity, duration: 3 }}>{emoji}</motion.div>
      <p className="font-display text-lg font-semibold">{title}</p>
      {text && <p className="max-w-sm text-sm text-muted">{text}</p>}
    </div>
  )
}
