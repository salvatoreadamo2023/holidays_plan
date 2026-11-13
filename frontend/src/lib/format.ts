import { format, parseISO } from 'date-fns'
import { it } from 'date-fns/locale'
import type { AbsenceType, LeaveReason, Role, Status } from './types'

export const fmt = (d: string | Date, f = 'd MMM yyyy') => format(typeof d === 'string' ? parseISO(d) : d, f, { locale: it })
export const iso = (d: Date) => format(d, 'yyyy-MM-dd')

export const num = (n: number | null | undefined) =>
  n == null ? '–' : Number(n).toLocaleString('it-IT', { maximumFractionDigits: 1 })

export const typeLabel: Record<AbsenceType | 'ASSENZA', string> = {
  FERIE: 'Ferie', PERMESSO: 'Permesso', GENERAL_LEAVE: 'General leave', CLIENT_OFFICE: 'Presso cliente (CO)', BUSINESS_TRIP: 'Trasferta (BT)', ASSENZA: 'Assente',
}
export const typeShort: Record<AbsenceType, string> = {
  FERIE: 'Ferie', PERMESSO: 'Permesso', GENERAL_LEAVE: 'General leave', CLIENT_OFFICE: 'Cliente (CO)', BUSINESS_TRIP: 'Trasferta (BT)',
}
/** CO e BT: lavoro fuori sede, non assenze. Registrati subito come il General leave. */
export const isPresence = (t: string) => t === 'CLIENT_OFFICE' || t === 'BUSINESS_TRIP'
export const isAutoRecorded = (t: string) => t === 'GENERAL_LEAVE' || isPresence(t)

export const reasonLabel: Record<LeaveReason, string> = {
  MALATTIA: 'Malattia', MALATTIA_FIGLIO: 'Malattia figlio', VISITA_MEDICA: 'Visita medica', LEGGE_104: 'Legge 104',
  MATERNITA_PATERNITA: 'Maternità / paternità', CONGEDO_PARENTALE: 'Congedo parentale', CONGEDO_MATRIMONIALE: 'Congedo matrimoniale',
  LUTTO: 'Lutto', DONAZIONE_SANGUE: 'Donazione sangue', SEGGIO_ELETTORALE: 'Seggio elettorale', STUDIO_ESAMI: 'Studio / esami', ALTRO: 'Altro',
}
export const needsCertificate = (r: { type: string; leaveReason?: LeaveReason | null }) =>
  r.type === 'GENERAL_LEAVE' && (r.leaveReason === 'MALATTIA' || r.leaveReason === 'MALATTIA_FIGLIO')
/** "General leave · Legge 104" quando la tipologia e' visibile. */
export const typeText = (r: { type: AbsenceType; leaveReason?: LeaveReason | null }) =>
  r.leaveReason ? `${typeLabel[r.type]} · ${reasonLabel[r.leaveReason]}` : typeLabel[r.type]
export const typeEmoji: Record<AbsenceType | 'ASSENZA', string> = {
  FERIE: '🌴', PERMESSO: '⏱️', GENERAL_LEAVE: '🩺', CLIENT_OFFICE: '🏢', BUSINESS_TRIP: '🧳', ASSENZA: '✈️',
}

export const statusLabel: Record<Status, string> = {
  PENDING: 'In attesa', APPROVED: 'Approvata', REJECTED: 'Rifiutata', WITHDRAWN: 'Ritirata',
  CANCEL_REQUESTED: 'Annullamento chiesto', CANCELLED: 'Annullata',
}

export const roleLabel: Record<Role, string> = {
  USER: 'Dipendente', TEAM_LEAD: 'Team Manager / Tech Lead', PROJECT_MANAGER: 'Project Manager', ADMIN: 'Admin',
}

export const range = (r: { startDate: string; endDate: string; halfDay?: boolean; hours?: number | null }) => {
  const same = r.startDate === r.endDate
  const base = same ? fmt(r.startDate, 'EEE d MMM') : `${fmt(r.startDate, 'd MMM')} → ${fmt(r.endDate, 'd MMM yyyy')}`
  if (r.hours) return `${base} · ${num(r.hours)} h`
  if (r.halfDay) return `${base} · mezza giornata`
  return base
}

export const initials = (name: string) => name.split(/\s+/).map(p => p[0]).slice(0, 2).join('').toUpperCase()

const hues = [262, 190, 330, 25, 160, 220, 290, 45]
export const avatarHue = (name: string) => hues[[...name].reduce((a, c) => a + c.charCodeAt(0), 0) % hues.length]
