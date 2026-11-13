import { Command } from 'cmdk'
import { AnimatePresence, motion } from 'framer-motion'
import { CalendarRange, CheckCheck, Home, Languages, ListChecks, LogOut, Moon, Plus, Settings, Sun } from 'lucide-react'
import { useNavigate } from 'react-router'
import { isAdmin, isManager, useAuth } from '../lib/auth'
import { useUi } from '../lib/ui'
import { t, useLang } from '../lib/i18n'

export function CommandPalette() {
  const { paletteOpen, setPaletteOpen, openRequest, toggleDark, dark } = useUi()
  const { user, logout } = useAuth()
  const { lang, setLang } = useLang()
  const nav = useNavigate()
  const run = (fn: () => void) => { setPaletteOpen(false); fn() }
  const item = 'flex cursor-pointer items-center gap-3 rounded-xl px-3 py-2.5 text-sm'

  return (
    <AnimatePresence>
      {paletteOpen && (
        <motion.div className="fixed inset-0 z-[60] flex items-start justify-center bg-black/40 pt-[15vh] backdrop-blur-sm"
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setPaletteOpen(false)}>
          <motion.div initial={{ scale: .96, y: -10 }} animate={{ scale: 1, y: 0 }} exit={{ scale: .96, opacity: 0 }}
            onClick={e => e.stopPropagation()} className="w-[min(560px,calc(100%-32px))]">
            <Command label={t('Comandi')} className="glass-strong overflow-hidden rounded-3xl shadow-2xl">
              <Command.Input autoFocus placeholder={t('Cosa vuoi fare?')} className="w-full border-b border-soft bg-transparent px-5 py-4 text-base outline-none" />
              <Command.List className="max-h-80 overflow-y-auto p-2">
                <Command.Empty className="px-3 py-6 text-center text-sm text-muted">{t('Nessun risultato')}</Command.Empty>
                <Command.Group heading={t('Azioni')} className="px-1 text-xs text-muted">
                  <Command.Item className={item} onSelect={() => run(() => openRequest())}><Plus size={16} /> {t('Nuova richiesta di ferie')}</Command.Item>
                  <Command.Item className={item} onSelect={() => run(() => openRequest())}><Plus size={16} /> {t('Nuovo permesso a ore')}</Command.Item>
                  <Command.Item className={item} onSelect={() => run(() => setLang(lang === 'it' ? 'en' : 'it'))}><Languages size={16} /> {lang === 'it' ? 'English' : 'Italiano'}</Command.Item>
                  <Command.Item className={item} onSelect={() => run(toggleDark)}>{dark ? <Sun size={16} /> : <Moon size={16} />} {dark ? t('Tema chiaro') : t('Tema scuro')}</Command.Item>
                </Command.Group>
                <Command.Group heading={t('Vai a')} className="px-1 text-xs text-muted">
                  <Command.Item className={item} onSelect={() => run(() => nav('/'))}><Home size={16} /> Home</Command.Item>
                  <Command.Item className={item} onSelect={() => run(() => nav('/calendario'))}><CalendarRange size={16} /> {t('Calendario area')}</Command.Item>
                  <Command.Item className={item} onSelect={() => run(() => nav('/richieste'))}><ListChecks size={16} /> {t('Le mie richieste')}</Command.Item>
                  {isManager(user) && <Command.Item className={item} onSelect={() => run(() => nav('/approvazioni'))}><CheckCheck size={16} /> {t('Da approvare')}</Command.Item>}
                  {isAdmin(user) && <Command.Item className={item} onSelect={() => run(() => nav('/admin'))}><Settings size={16} /> {t('Amministrazione')}</Command.Item>}
                  <Command.Item className={item} onSelect={() => run(logout)}><LogOut size={16} /> {t('Esci')}</Command.Item>
                </Command.Group>
              </Command.List>
            </Command>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
