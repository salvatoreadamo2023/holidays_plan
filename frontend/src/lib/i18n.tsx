import { createContext, Fragment, useContext, useState, type ReactNode } from 'react'
import { EN, translateServerEn } from './i18n-en'

/**
 * Traduzioni: il testo italiano e' la chiave, l'inglese sta nel dizionario EN.
 * Se manca una voce resta l'italiano, cosi' nessuna schermata si rompe.
 */
export type Lang = 'it' | 'en'
const KEY = 'hp.lang'

const readLang = (): Lang => {
  try { const v = localStorage.getItem(KEY); if (v === 'it' || v === 'en') return v } catch { /* ignore */ }
  return navigator.language?.toLowerCase().startsWith('it') ? 'it' : navigator.language ? 'en' : 'it'
}

let current: Lang = readLang()
if (typeof document !== 'undefined') document.documentElement.lang = current
export const getLang = () => current

export function t(s: string, vars?: Record<string, string | number>) {
  let out = current === 'en' ? (EN[s] ?? s) : s
  if (vars) for (const [k, v] of Object.entries(vars)) out = out.split(`{${k}}`).join(String(v))
  return out
}

/** Messaggi che arrivano dal server (errori, avvisi): sono in italiano, li traduciamo qui. */
export const ts = (s: string) => (current === 'en' ? translateServerEn(s) : s)

/** Mappa di etichette tradotta al momento della lettura (typeLabel[...] ecc.). */
export const tr = <T extends Record<string, string>>(o: T): T =>
  new Proxy(o, { get: (target, k) => { const v = (target as Record<string | symbol, unknown>)[k]; return typeof v === 'string' ? t(v) : v } })

const Ctx = createContext<{ lang: Lang; setLang: (l: Lang) => void } | null>(null)

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setState] = useState<Lang>(current)
  const setLang = (l: Lang) => {
    current = l
    document.documentElement.lang = l
    try { localStorage.setItem(KEY, l) } catch { /* ignore */ }
    setState(l)
  }
  // Il key rimonta l'albero: tutte le etichette si rileggono nella nuova lingua.
  return <Ctx.Provider value={{ lang, setLang }}><Fragment key={lang}>{children}</Fragment></Ctx.Provider>
}

export function useLang() {
  const c = useContext(Ctx)
  if (!c) throw new Error('useLang fuori da I18nProvider')
  return c
}

/** Pulsante IT / EN. */
export function LangToggle({ className }: { className?: string }) {
  const { lang, setLang } = useLang()
  return (
    <button onClick={() => setLang(lang === 'it' ? 'en' : 'it')} className={className}
      aria-label={lang === 'it' ? 'Switch to English' : 'Passa all\'italiano'} title={lang === 'it' ? 'English' : 'Italiano'}>
      <span className="text-[11px] font-bold tracking-wide">{lang === 'it' ? 'EN' : 'IT'}</span>
    </button>
  )
}
