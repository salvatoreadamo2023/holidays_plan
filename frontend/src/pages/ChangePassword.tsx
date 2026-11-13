import { useState } from 'react'
import { KeyRound } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Aurora, Logo } from '../components/Layout'
import { inputCls } from '../components/ui'
import { t } from '../lib/i18n'

/** Cambio password: obbligatorio al primo accesso o dopo un reset dell'admin, facoltativo dal menu. */
export function ChangePassword({ forced = false, onDone }: { forced?: boolean; onDone?: () => void }) {
  const { refresh, logout } = useAuth()
  const [cur, setCur] = useState('')
  const [pw, setPw] = useState('')
  const [pw2, setPw2] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (pw.length < 10) return toast.error(t('La nuova password deve avere almeno 10 caratteri'))
    if (pw !== pw2) return toast.error(t('Le due password non coincidono'))
    setBusy(true)
    try {
      await api('/api/auth/password', { method: 'POST', json: { currentPassword: cur, newPassword: pw } })
      toast.success(t('Password aggiornata'))
      await refresh()
      onDone?.()
    } catch (err) { toast.error((err as Error).message) } finally { setBusy(false) }
  }

  const form = (
    <form onSubmit={submit} className="space-y-4">
      <input className={inputCls} type="password" placeholder={t('Password attuale')} autoComplete="current-password" value={cur} onChange={e => setCur(e.target.value)} required />
      <input className={inputCls} type="password" placeholder={t('Nuova password (min 10 caratteri)')} autoComplete="new-password" value={pw} onChange={e => setPw(e.target.value)} required />
      <input className={inputCls} type="password" placeholder={t('Ripeti la nuova password')} autoComplete="new-password" value={pw2} onChange={e => setPw2(e.target.value)} required />
      <button disabled={busy} className="btn-primary flex w-full items-center justify-center gap-2 rounded-xl py-3.5 font-semibold disabled:opacity-60">
        <KeyRound size={18} /> {busy ? t('Salvataggio…') : t('Cambia password')}
      </button>
    </form>
  )

  if (!forced) return <div className="glass mx-auto max-w-md rounded-3xl p-6"><h1 className="mb-4 font-display text-2xl font-bold">{t('Cambia password')}</h1>{form}</div>

  return (
    <div className="relative grid min-h-full place-items-center overflow-hidden px-4 py-10">
      <Aurora />
      <div className="glass-strong relative z-10 w-full max-w-md rounded-[2rem] p-8 shadow-2xl">
        <Logo />
        <h1 className="mt-8 font-display text-3xl font-bold">{t('Scegli la tua password')}</h1>
        <p className="mb-6 mt-2 text-sm text-muted">{t('È il primo accesso o la password è stata reimpostata: scegline una personale per continuare.')}</p>
        {form}
        <button onClick={logout} className="mt-4 w-full text-center text-sm text-muted hover:underline">{t('Esci')}</button>
      </div>
    </div>
  )
}
