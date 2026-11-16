import { useState } from 'react'
import { ChevronDown, Download, Share, Smartphone, SquarePlus } from 'lucide-react'
import clsx from 'clsx'
import { t } from '../lib/i18n'

/** Passi temporanei per iPhone: in attesa dell'app iOS si aggiunge il sito alla schermata Home (PWA). */
export function IosSteps({ className }: { className?: string }) {
  return (
    <ol className={clsx('space-y-1.5 text-xs', className)}>
      <li className="flex gap-2"><span className="font-bold text-violet-600 dark:text-violet-300">1.</span>{t('Apri questo sito con Safari.')}</li>
      <li className="flex gap-2"><span className="font-bold text-violet-600 dark:text-violet-300">2.</span><span>{t('Tocca Condividi')} <Share size={13} className="inline -mt-0.5" /> {t('in basso (o in alto su iPad).')}</span></li>
      <li className="flex gap-2"><span className="font-bold text-violet-600 dark:text-violet-300">3.</span><span>{t('Scegli «Aggiungi alla schermata Home»')} <SquarePlus size={13} className="inline -mt-0.5" /></span></li>
      <li className="flex gap-2"><span className="font-bold text-violet-600 dark:text-violet-300">4.</span>{t('Tocca «Aggiungi»: l\'icona Ferie compare tra le tue app.')}</li>
    </ol>
  )
}

/** Vero se il sito e' gia' aperto come app dalla schermata Home (iPhone o PWA installata). */
const standalone = () => {
  try { return (navigator as Navigator & { standalone?: boolean }).standalone === true || window.matchMedia('(display-mode: standalone)').matches } catch { return false }
}

/** Scheda in home: APK per Android (se l'admin ha messo il link) e passi per iPhone. */
export function GetAppCard({ apkUrl, Card }: { apkUrl: string | null; Card: (p: { className?: string; children: React.ReactNode }) => React.ReactElement }) {
  // Gia' installata sulla Home: la scheda non serve.
  if (standalone()) return null
  return (
    <Card className="relative overflow-hidden">
      <div className="pointer-events-none absolute -right-8 -top-8 h-24 w-24 rounded-full bg-emerald-500/20 blur-2xl" />
      <div className="flex items-center gap-3">
        <div className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-emerald-500 to-teal-600 text-white"><Smartphone size={20} /></div>
        <div className="min-w-0 flex-1">
          <div className="font-semibold">{t("Usa l'app sul telefono")}</div>
          <div className="text-xs text-muted">{t('Richieste, approvazioni e notifiche push sul telefono.')}</div>
        </div>
      </div>
      {apkUrl && (
        <a href={apkUrl} target="_blank" rel="noopener noreferrer" className="btn-primary mt-4 flex items-center justify-center gap-2 rounded-xl py-2.5 text-sm font-semibold">
          <Download size={16} /> {t("Android: scarica l'APK")}
        </a>
      )}
      <div className="mt-4 rounded-2xl border border-soft p-3">
        <div className="mb-2 text-sm font-semibold"> {t('iPhone: aggiungi alla schermata Home')}</div>
        <IosSteps />
        <p className="mt-2 text-[11px] text-muted">{t("Soluzione temporanea: l'app per iPhone è in arrivo.")}</p>
      </div>
    </Card>
  )
}

/** Nella pagina di accesso: link APK e passi per iPhone a scomparsa. */
export function LoginAppLinks({ apkUrl }: { apkUrl: string | null | undefined }) {
  const [ios, setIos] = useState(false)
  if (standalone()) return null
  return (
    <div className="mt-6 space-y-2">
      {apkUrl && (
        <a href={apkUrl} target="_blank" rel="noopener noreferrer" className="flex items-center justify-center gap-2 rounded-xl border border-soft py-2.5 text-sm font-medium text-muted hover:text-[var(--text)]">
          <Download size={16} /> {t("Scarica l'app Android")}
        </a>
      )}
      <button type="button" onClick={() => setIos(o => !o)} aria-expanded={ios}
        className="flex w-full items-center justify-center gap-2 rounded-xl border border-soft py-2.5 text-sm font-medium text-muted hover:text-[var(--text)]">
         {t('Su iPhone? Aggiungi alla schermata Home')} <ChevronDown size={15} className={clsx('transition', ios && 'rotate-180')} />
      </button>
      {ios && (
        <div className="rounded-xl border border-soft p-3">
          <IosSteps />
          <p className="mt-2 text-[11px] text-muted">{t("Soluzione temporanea: l'app per iPhone è in arrivo.")}</p>
        </div>
      )}
    </div>
  )
}
