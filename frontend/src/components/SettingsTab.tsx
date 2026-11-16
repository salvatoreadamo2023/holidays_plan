import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Clock, Mail, Smartphone } from 'lucide-react'
import clsx from 'clsx'
import { toast } from 'sonner'
import { api } from '../lib/api'
import { num } from '../lib/format'
import { t } from '../lib/i18n'
import { ALL_HOURS } from '../lib/settings'
import { Button, Card, inputCls } from './ui'

interface AdminSettings { permitHours: number[]; apkUrl: string | null; emailNotifications: boolean }

/** Impostazioni generali: durate di permesso consentite, link APK, email di notifica. */
export function SettingsTab() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['admin', 'settings'], queryFn: () => api<AdminSettings>('/api/admin/settings') })
  const [hours, setHours] = useState<number[]>([])
  const [apk, setApk] = useState('')
  const [email, setEmail] = useState(false)
  useEffect(() => {
    if (q.data) { setHours(q.data.permitHours.map(Number)); setApk(q.data.apkUrl ?? ''); setEmail(q.data.emailNotifications) }
  }, [q.data])
  const save = useMutation({
    mutationFn: () => api<AdminSettings>('/api/admin/settings', { method: 'PUT', json: { permitHours: hours, apkUrl: apk.trim(), emailNotifications: email } }),
    onSuccess: () => { toast.success(t('Impostazioni salvate')); qc.invalidateQueries({ queryKey: ['admin', 'settings'] }); qc.invalidateQueries({ queryKey: ['settings'] }) },
    onError: (e: Error) => toast.error(e.message),
  })
  const toggle = (h: number) => setHours(s => (s.includes(h) ? s.filter(x => x !== h) : [...s, h].sort((a, b) => a - b)))
  const preset = (list: number[]) => setHours(list)
  if (!q.data) return <Card><div className="p-6 text-center text-sm text-muted">{t('Caricamento…')}</div></Card>

  return (
    <div className="space-y-6">
      <Card>
        <div className="mb-1 flex items-center gap-2 font-display text-lg font-semibold"><Clock size={18} className="text-amber-500" />{t('Durate dei permessi')}</div>
        <p className="mb-4 text-sm text-muted">{t('Scegli quante ore si possono chiedere con un permesso. Chi fa una richiesta vede solo le durate attive.')}</p>
        <div className="flex flex-wrap gap-2">
          {ALL_HOURS.map(h => (
            <button key={h} type="button" role="checkbox" aria-checked={hours.includes(h)} onClick={() => toggle(h)}
              className={clsx('min-w-14 rounded-xl border px-3 py-2 text-sm font-semibold transition',
                hours.includes(h) ? 'border-transparent bg-gradient-to-r from-amber-500 to-rose-500 text-white shadow-md shadow-amber-500/30' : 'border-soft text-muted hover:text-[var(--text)]')}>
              {hours.includes(h) ? '✓ ' : ''}{num(h)}h
            </button>
          ))}
        </div>
        <div className="mt-3 flex flex-wrap gap-2 text-xs">
          <span className="self-center text-muted">{t('Scelte rapide:')}</span>
          <button className="rounded-full border border-soft px-3 py-1 hover:bg-violet-500/10" onClick={() => preset(ALL_HOURS)}>{t('Tutte (anche mezz\'ore)')}</button>
          <button className="rounded-full border border-soft px-3 py-1 hover:bg-violet-500/10" onClick={() => preset([1, 2, 3, 4, 5, 6, 7, 8])}>{t('Ore intere')}</button>
          <button className="rounded-full border border-soft px-3 py-1 hover:bg-violet-500/10" onClick={() => preset([2, 4])}>{t('Solo 2 o 4 ore')}</button>
        </div>
        {hours.length === 0 && <p className="mt-3 text-sm text-rose-600">{t('Lascia almeno una durata di permesso')}</p>}
      </Card>

      <Card>
        <div className="mb-1 flex items-center gap-2 font-display text-lg font-semibold"><Smartphone size={18} className="text-violet-500" />{t('App Android (APK)')}</div>
        <p className="mb-4 text-sm text-muted">{t('Incolla il link per scaricare l\'APK (per esempio quello della build EAS). In home e nella pagina di accesso comparirà il pulsante «Scarica l\'app».')}</p>
        <input className={inputCls} type="url" placeholder="https://…" value={apk} onChange={e => setApk(e.target.value)} maxLength={1000} />
        <p className="mt-2 text-xs text-muted">{t('Lascia vuoto per nascondere il pulsante.')}</p>
      </Card>

      <Card>
        <div className="mb-1 flex items-center gap-2 font-display text-lg font-semibold"><Mail size={18} className="text-emerald-500" />{t('Notifiche via email')}</div>
        <label className="mt-2 flex cursor-pointer items-start gap-3">
          <input type="checkbox" className="mt-1 h-4 w-4 accent-violet-600" checked={email} onChange={e => setEmail(e.target.checked)} />
          <span className="text-sm">{t('Invia anche un\'email per ogni notifica (nuove richieste, decisioni, rettifiche, annullamenti). La campanella del sito e le push dell\'app funzionano comunque.')}
            <span className="mt-1 block text-xs text-muted">{t('Serve il server email configurato su Render (Amazon SES): senza, le email non partono.')}</span></span>
        </label>
      </Card>

      <div className="flex justify-end">
        <Button variant="primary" disabled={save.isPending || hours.length === 0} onClick={() => save.mutate()}>{save.isPending ? t('Salvataggio…') : t('Salva impostazioni')}</Button>
      </div>
    </div>
  )
}
