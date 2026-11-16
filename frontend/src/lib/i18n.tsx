import { createContext, Fragment, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { EN, translateServerEn } from './i18n-en'
import { ES, translateServerEs } from './i18n-es'

/**
 * Traduzioni: il testo italiano e' la chiave, inglese e spagnolo stanno nei dizionari EN ed ES.
 * Se manca una voce resta l'italiano, cosi' nessuna schermata si rompe.
 */
export type Lang = 'it' | 'en' | 'es'
export const LANGS: { code: Lang; label: string }[] = [
  { code: 'it', label: 'Italiano' }, { code: 'en', label: 'English' }, { code: 'es', label: 'Español' },
]
const KEY = 'hp.lang'
const DICT: Record<Lang, Record<string, string> | null> = { it: null, en: EN, es: ES }

const readLang = (): Lang => {
  try { const v = localStorage.getItem(KEY); if (v === 'it' || v === 'en' || v === 'es') return v } catch { /* ignore */ }
  const n = navigator.language?.toLowerCase() ?? ''
  return n.startsWith('it') || !n ? 'it' : n.startsWith('es') ? 'es' : 'en'
}

let current: Lang = readLang()
if (typeof document !== 'undefined') document.documentElement.lang = current
export const getLang = () => current

export function t(s: string, vars?: Record<string, string | number>) {
  const d = DICT[current]
  let out = d ? (d[s] ?? s) : s
  if (vars) for (const [k, v] of Object.entries(vars)) out = out.split(`{${k}}`).join(String(v))
  return out
}

/** Messaggi che arrivano dal server (errori, avvisi): sono in italiano, li traduciamo qui. */
export const ts = (s: string) => (current === 'en' ? translateServerEn(s) : current === 'es' ? translateServerEs(s) : s)

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

/** Scelta della lingua: IT / EN / ES. */
export function LangToggle({ className, up }: { className?: string; up?: boolean }) {
  const { lang, setLang } = useLang()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])
  return (
    <div ref={ref} className="relative flex">
      <button onClick={() => setOpen(o => !o)} className={className} aria-haspopup="menu" aria-expanded={open}
        aria-label={t('Lingua')} title={t('Lingua')}>
        <span className="text-[11px] font-bold uppercase tracking-wide">{lang}</span>
      </button>
      {open && (
        <div role="menu" className={`glass-strong absolute right-0 z-50 w-36 overflow-hidden rounded-xl p-1 shadow-xl ${up ? 'bottom-full mb-2' : 'top-full mt-2'}`}>
          {LANGS.map(l => (
            <button key={l.code} role="menuitemradio" aria-checked={lang === l.code} onClick={() => { setOpen(false); setLang(l.code) }}
              className={`flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm hover:bg-violet-500/10 ${lang === l.code ? 'font-semibold text-violet-600 dark:text-violet-300' : ''}`}>
              {l.label}<span className="text-[10px] font-bold uppercase text-muted">{l.code}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
