import { useState } from 'react'
import { motion } from 'framer-motion'
import { ArrowLeft, ArrowRight, Lock, Mail, User } from 'lucide-react'
import { toast } from 'sonner'
import { useAuth } from '../lib/auth'
import { Aurora, Logo } from '../components/Layout'
import { inputCls } from '../components/ui'
import { typeEmoji } from '../lib/format'
import { LangToggle, t } from '../lib/i18n'
import { api } from '../lib/api'
import { useAuthOptions } from '../lib/settings'
import { LoginAppLinks } from '../components/GetApp'

export function Login() {
  const { login } = useAuth()
  const opts = useAuthOptions()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [mode, setMode] = useState<'login' | 'forgot' | 'sent'>(() =>
    typeof window !== 'undefined' && window.location.pathname === '/password-dimenticata' ? 'forgot' : 'login')
  const [email, setEmail] = useState('')
  const reset = new URLSearchParams(typeof window !== 'undefined' ? window.location.search : '').has('reset')

  const forgot = async (e: React.FormEvent) => {
    e.preventDefault(); setBusy(true)
    try {
      await api('/api/auth/password-forgot', { method: 'POST', json: { username: username.trim(), email: email.trim() } })
      setMode('sent')
    } catch (err) { toast.error((err as Error).message) } finally { setBusy(false) }
  }
  const back = () => { setMode('login'); if (window.location.pathname !== '/') window.history.replaceState(null, '', '/') }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setBusy(true)
    try { await login(username, password) } catch (err) { toast.error((err as Error).message) } finally { setBusy(false) }
  }

  return (
    <div className="relative grid min-h-full place-items-center overflow-hidden px-4 py-10">
      <Aurora />
      {/* Orbite decorative */}
      <div className="pointer-events-none absolute inset-0 hidden items-center justify-center md:flex" aria-hidden>
        {[420, 620, 820].map((s, i) => (
          <motion.div key={s} className="absolute rounded-full border border-soft" style={{ width: s, height: s }}
            animate={{ rotate: i % 2 ? -360 : 360 }} transition={{ repeat: Infinity, duration: 60 + i * 20, ease: 'linear' }}>
            <span className="absolute -top-5 left-1/2 -translate-x-1/2 text-3xl">{Object.values(typeEmoji)[i]}</span>
          </motion.div>
        ))}
      </div>
      <motion.div initial={{ opacity: 0, y: 24, scale: .97 }} animate={{ opacity: 1, y: 0, scale: 1 }} transition={{ type: 'spring', damping: 24 }}
        className="glass-strong relative z-10 w-full max-w-md rounded-[2rem] p-8 shadow-2xl">
        <div className="flex items-center justify-between"><Logo /><LangToggle className="grid h-9 w-9 place-items-center rounded-xl border border-soft text-muted hover:text-[var(--text)]" /></div>
        <h1 className="mt-8 font-display text-4xl font-bold leading-tight tracking-tight">
          {t('Addio Excel.')}<br /><span className="gradient-text">{t('Ciao ferie.')}</span>
        </h1>
        <p className="mt-3 text-sm text-muted">{t('Chiedi, approva e pianifica le assenze del tuo team in pochi secondi.')}</p>
        {reset && mode === 'login' && <p className="mt-6 rounded-2xl bg-emerald-500/10 p-3 text-sm text-emerald-700 dark:text-emerald-300">{t('Password aggiornata: accedi con la nuova password')}</p>}
        {mode === 'login' && (<>
        <form onSubmit={submit} className="mt-8 space-y-4">
          <div className="relative">
            <User size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
            <input className={`${inputCls} pl-10`} placeholder="Username" autoComplete="username" value={username} onChange={e => setUsername(e.target.value)} required />
          </div>
          <div className="relative">
            <Lock size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
            <input className={`${inputCls} pl-10`} type="password" placeholder="Password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required />
          </div>
          <button disabled={busy} className="btn-primary group flex w-full items-center justify-center gap-2 rounded-xl py-3.5 font-semibold transition active:scale-[.98] disabled:opacity-60">
            {busy ? t('Accesso…') : t('Entra')} <ArrowRight size={18} className="transition group-hover:translate-x-1" />
          </button>
        </form>
        {opts?.passwordReset && (
          <button type="button" onClick={() => setMode('forgot')} className="mt-4 w-full text-center text-sm font-medium text-violet-600 hover:underline dark:text-violet-300">{t('Password dimenticata?')}</button>
        )}
        </>)}
        {mode === 'forgot' && (
          <form onSubmit={forgot} className="mt-8 space-y-4">
            <div>
              <h2 className="font-display text-xl font-semibold">{t('Password dimenticata?')}</h2>
              <p className="mt-1 text-sm text-muted">{t('Scrivi il tuo username e l\'email aziendale: ti mandiamo un link per scegliere una nuova password.')}</p>
            </div>
            <div className="relative">
              <User size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
              <input className={`${inputCls} pl-10`} placeholder="Username" autoComplete="username" maxLength={100} value={username} onChange={e => setUsername(e.target.value)} required />
            </div>
            <div className="relative">
              <Mail size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
              <input className={`${inputCls} pl-10`} type="email" placeholder={t('Email')} autoComplete="email" maxLength={254} value={email} onChange={e => setEmail(e.target.value)} required />
            </div>
            <button disabled={busy} className="btn-primary flex w-full items-center justify-center gap-2 rounded-xl py-3.5 font-semibold transition active:scale-[.98] disabled:opacity-60">
              {busy ? t('Invio…') : t('Mandami il link')} <Mail size={18} />
            </button>
            <button type="button" onClick={back} className="flex w-full items-center justify-center gap-1 text-sm text-muted hover:text-[var(--text)]"><ArrowLeft size={15} /> {t('Torna all\'accesso')}</button>
          </form>
        )}
        {mode === 'sent' && (
          <div className="mt-8 space-y-4">
            <div className="text-4xl">📬</div>
            <p className="text-sm">{t('Se username ed email corrispondono a un utente, riceverai a breve un\'email con il link per scegliere una nuova password. Il link vale 30 minuti e si può usare una sola volta. Controlla anche la cartella spam.')}</p>
            <button type="button" onClick={back} className="flex w-full items-center justify-center gap-1 text-sm font-medium text-violet-600 hover:underline dark:text-violet-300"><ArrowLeft size={15} /> {t('Torna all\'accesso')}</button>
          </div>
        )}
        <LoginAppLinks apkUrl={opts?.apkUrl} />
      </motion.div>
    </div>
  )
}
