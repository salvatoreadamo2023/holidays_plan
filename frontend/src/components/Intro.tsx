import { useEffect, useMemo, useState } from 'react'
import { t } from '../lib/i18n'

const KEY = 'hp.intro'

/** L'intro si vede una volta per sessione del browser e per utente (dopo il login o alla prima apertura). */
export function shouldShowIntro(userId: number | string) {
  try { return sessionStorage.getItem(KEY) !== String(userId) } catch { return false }
}

/**
 * Intro animata in stile Digisol/CGI: sfondo scuro con aloni rossi rotanti, puntini che salgono,
 * logo CGI, titolo con riflesso, linea rossa che si allunga. Si chiude da sola o con un clic.
 */
export function Intro({ userId, onDone }: { userId: number | string; onDone: () => void }) {
  const [hide, setHide] = useState(false)
  const dots = useMemo(() => Array.from({ length: 26 }, () => ({
    left: `${Math.random() * 100}%`, bottom: `${-10 + Math.random() * 30}%`,
    animationDuration: `${3.5 + Math.random() * 4}s`, animationDelay: `${Math.random() * 4}s`, opacity: 0.3 + Math.random() * 0.6,
  })), [])

  const close = () => setHide(true)
  useEffect(() => {
    const a = setTimeout(close, 3600)
    return () => clearTimeout(a)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (!hide) return
    const b = setTimeout(() => {
      try { sessionStorage.setItem(KEY, String(userId)) } catch { /* ignore */ }
      onDone()
    }, 700)
    return () => clearTimeout(b)
  }, [hide, onDone, userId])

  return (
    <div className={`cgi-intro${hide ? ' hide' : ''}`} onClick={close} role="presentation">
      <div className="aura" /><div className="aura b" />
      <div className="dots">{dots.map((d, i) => <i key={i} style={d} />)}</div>
      <div className="center">
        <div className="mark"><img src="/cgi-logo.png" alt="CGI" /></div>
        <div className="word">{t('Gestione Ferie')}</div>
        <div className="rule" />
        <div className="tag">
          <span className="w">{t('Ferie e permessi')}</span><span className="w">&middot;</span>
          <span className="w">{t('in un clic')}</span><span className="w"><b>by CGI</b></span>
        </div>
      </div>
    </div>
  )
}
