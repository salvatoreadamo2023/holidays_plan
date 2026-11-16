/** Diccionario español: clave = texto italiano mostrado en la interfaz. */
import { ES_DICT, ES_SERVER } from './i18n-es-dict'
export const ES: Record<string, string> = ES_DICT

const ROLE_ES: Record<string, string> = { USER: 'Empleado', TEAM_LEAD: 'Team Manager / Tech Lead', PROJECT_MANAGER: 'Project Manager', ADMIN: 'Admin' }
const PATTERNS: [RegExp, (m: RegExpMatchArray) => string][] = [
  [/^Valore non numerico per (ferie|permessi)( AP)?: "(.*)"$/, (m) => `Valor no numérico para ${m[1] === 'ferie' ? 'vacaciones' : 'permisos'}${m[2] ? ' AA' : ''}: "${m[3]}"`],
  [/^Valore fuori scala per (ferie|permessi)( AP)?: (.*)$/, (m) => `Valor fuera de rango para ${m[1] === 'ferie' ? 'vacaciones' : 'permisos'}${m[2] ? ' AA' : ''}: ${m[3]}`],
  [/^Durata del permesso non consentita: scegli tra (.+) ore$/, (m) => `Duración del permiso no permitida: elige entre ${m[1]} horas`],
  [/^Durata di permesso non valida: (.+)$/, (m) => `Duración de permiso no válida: ${m[1]}`],
  [/^Nuova richiesta da (.+)$/, (m) => `Nueva solicitud de ${m[1]}`],
  [/^(.+) chiede di annullare$/, (m) => `${m[1]} pide anular`],
  [/^Rettifica chiesta da (.+)$/, (m) => `Rectificación pedida por ${m[1]}`],
  [/^(.+) assente$/, (m) => `${m[1]} ausente`],
  [/^(.+) ha gia' un'altra assenza che si sovrappone a queste date$/, (m) => `${m[1]} ya tiene otra ausencia que se solapa con estas fechas`],
  [/^Rettificata da (.+)$/, (m) => `Rectificada por ${m[1]}`],
  [/^Superata dalla rettifica di (.+)$/, (m) => `Sustituida por la rectificación de ${m[1]}`],
  [/^Una singola richiesta puo' coprire al massimo (\d+) giorni$/, (m) => `Una sola solicitud puede cubrir como máximo ${m[1]} días`],
  [/^Si puo' registrare al massimo (\d+) giorni indietro$/, (m) => `Se puede registrar como máximo ${m[1]} días atrás`],
  [/^Hai gia' (\d+) richieste in attesa$/, (m) => `Ya tienes ${m[1]} solicitudes pendientes`],
  [/^(.+) ha gia' un'assenza che si sovrappone a queste date$/, (m) => `${m[1]} ya tiene una ausencia que se solapa con estas fechas`],
  [/^(.+) ha gia' un'assenza in quei giorni$/, (m) => `${m[1]} ya tiene una ausencia esos días`],
  [/^Puoi allegare al massimo (\d+) file per richiesta$/, (m) => `Puedes adjuntar como máximo ${m[1]} archivos por solicitud`],
  [/^L'utente scelto deve avere ruolo (\w+)$/, (m) => `El usuario elegido debe tener el rol ${ROLE_ES[m[1]] ?? m[1]}`],
]

export function translateServerEs(s: string) {
  if (ES_SERVER[s]) return ES_SERVER[s]
  if (ES[s]) return ES[s]
  for (const [re, f] of PATTERNS) { const m = s.match(re); if (m) return f(m) }
  return s
}
