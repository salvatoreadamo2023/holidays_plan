import clsx from 'clsx'
import { useEffect } from 'react'
import { num } from '../lib/format'
import { useSettings } from '../lib/settings'

/** Ore di permesso: solo le durate consentite dall'admin. */
export function HoursPicker({ value, onChange }: { value: number | null; onChange: (h: number) => void }) {
  const { permitHours, loaded } = useSettings()
  useEffect(() => {
    if (loaded && permitHours.length && !permitHours.includes(Number(value))) onChange(permitHours.includes(2) ? 2 : permitHours[0])
  }, [loaded, permitHours, value, onChange])
  return (
    <div className="flex flex-wrap gap-2" role="radiogroup">
      {permitHours.map(h => (
        <button key={h} type="button" role="radio" aria-checked={Number(value) === h} onClick={() => onChange(h)}
          className={clsx('min-w-12 rounded-xl border px-3 py-2 text-sm font-semibold transition',
            Number(value) === h ? 'border-transparent bg-gradient-to-r from-amber-500 to-rose-500 text-white shadow-md shadow-amber-500/30' : 'border-soft text-muted hover:text-[var(--text)]')}>
          {num(h)}h
        </button>
      ))}
    </div>
  )
}
