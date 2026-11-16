import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { ArrowRight, Lock } from 'lucide-react'
import { toast } from 'sonner'
import { Aurora, Logo } from '../components/Layout'
import { inputCls } from '../components/ui'
import { api, tokenStore } from '../lib/api'
import { LangToggle, t } from '../lib/i18n'

/** Pagina aperta dal link nell'email: sceglie la nuova password. */
export function ResetPassword() {
  // Letto una sola volta: subito dopo l'indirizzo viene ripulito e il token non e' piu' nella barra.
  const [token] = useState(() => new URLSearchParams(window.location.search).get('token') ?? '')
  const [valid, setValid] = useState<boolean | null>(null)
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    // Il token non deve restare nella cronologia del browser o finire nei referrer.
    window.history.replaceState(null, '', '/password-reset')
    api<{ valid: boolean }>(`/api/auth/password-reset/check?token=${encodeURIComponent(token)}`)
      .then(r => setValid(r.valid)).catch(() => setValid(false))
  }, [token])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (password !== confirm) { toast.error(t('Le due password non coincidono')); return }
    setBusy(true)
    try {
      await api('/api/auth/password-reset', { method: 'POST', json: { token, password, confirm } })
      tokenStore.clear()
      window.location.replace('/?reset')
    } catch (err) { toast.error((err as Error).message) } finally { setBusy(false) }
  }

  return (
    <div className="relative grid min-h-full place-items-center overflow-hidden px-4 py-10">
      <Aurora />
      <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} className="glass-strong relative z-10 w-full max-w-md rounded-[2rem] p-8 shadow-2xl">
        <div className="flex items-center justify-between"><Logo /><LangToggle className="grid h-9 w-9 place-items-center rounded-xl border border-soft text-muted hover:text-[var(--text)]" /></div>
        <h1 className="mt-8 font-display text-3xl font-bold tracking-tight">{t('Nuova password')}</h1>
        {valid === null && <p className="mt-4 text-sm text-muted">{t('Caricamento…')}</p>}
        {valid === false && (
          <div className="mt-6 space-y-4">
            <p className="rounded-2xl bg-rose-500/10 p-3 text-sm text-rose-700 dark:text-rose-300">{t('Il link non è valido, è scaduto oppure è già stato usato. Puoi chiederne uno nuovo.')}</p>
            <a href="/password-dimenticata" className="btn-primary flex w-full items-center justify-center gap-2 rounded-xl py-3 font-semibold">{t('Chiedi un nuovo link')}</a>
          </div>
        )}
        {valid && (
          <form onSubmit={submit} className="mt-6 space-y-4">
            <p className="text-sm text-muted">{t('Almeno 10 caratteri. Dopo il cambio dovrai accedere di nuovo su tutti i dispositivi.')}</p>
            <div className="relative">
              <Lock size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
              <input className={`${inputCls} pl-10`} type="password" placeholder={t('Nuova password')} autoComplete="new-password" minLength={10} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} required />
            </div>
            <div className="relative">
              <Lock size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
              <input className={`${inputCls} pl-10`} type="password" placeholder={t('Conferma password')} autoComplete="new-password" minLength={10} maxLength={72} value={confirm} onChange={e => setConfirm(e.target.value)} required />
            </div>
            <button disabled={busy} className="btn-primary group flex w-full items-center justify-center gap-2 rounded-xl py-3.5 font-semibold disabled:opacity-60">
              {busy ? t('Salvataggio…') : t('Salva la nuova password')} <ArrowRight size={18} />
            </button>
          </form>
        )}
      </motion.div>
    </div>
  )
}
