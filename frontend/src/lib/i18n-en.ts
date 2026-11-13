/** Dizionario inglese: chiave = testo italiano mostrato nell'interfaccia. */
export const EN: Record<string, string> = {
  // Tipologie, stati, ruoli
  'Ferie': 'Holidays', 'Permesso': 'Time off', 'General leave': 'General leave', 'Presso cliente (CO)': 'Client office (CO)',
  'Trasferta (BT)': 'Business trip (BT)', 'Ferie ipotetiche (TH)': 'Tentative holidays (TH)', 'Ipotesi (TH)': 'Tentative (TH)',
  'Ferie ipotetiche salvate': 'Tentative holidays saved', 'Quando sei sicuro, trasformale in richiesta da Le mie richieste.': 'When you are sure, turn them into a request from My requests.',
  "Solo un'idea di pianificazione: non va approvata, non scala le ferie e non conta nelle statistiche. È visibile a tutti. Quando sei sicuro, trasformala in una richiesta di ferie da Le mie richieste.": 'Just a planning idea: it needs no approval, does not use up holidays and is not counted in statistics. Everyone can see it. When you are sure, turn it into a holiday request from My requests.',
  'Richiesta di ferie inviata al responsabile': 'Holiday request sent to your manager', 'Trasforma in richiesta': 'Turn into request', 'Assente': 'Absent', 'Cliente (CO)': 'Client (CO)',
  'Malattia': 'Sick leave', 'Malattia figlio': 'Child sick leave', 'Visita medica': 'Medical appointment', 'Legge 104': 'Law 104 leave',
  'Maternità / paternità': 'Maternity / paternity', 'Congedo parentale': 'Parental leave', 'Congedo matrimoniale': 'Marriage leave',
  'Lutto': 'Bereavement', 'Donazione sangue': 'Blood donation', 'Seggio elettorale': 'Polling station duty', 'Studio / esami': 'Study / exams', 'Altro': 'Other',
  'In attesa': 'Pending', 'Approvata': 'Approved', 'Rifiutata': 'Rejected', 'Ritirata': 'Withdrawn', 'Annullamento chiesto': 'Cancellation requested', 'Annullata': 'Cancelled',
  'Dipendente': 'Employee', 'Team Manager / Tech Lead': 'Team Manager / Tech Lead', 'Project Manager': 'Project Manager', 'Admin': 'Admin',
  'mezza giornata': 'half day',

  // Generali
  'Qualcosa è andato storto': 'Something went wrong', 'Export non riuscito': 'Export failed', 'Chiudi': 'Close',
  'Home': 'Home', 'Calendario': 'Calendar', 'Richieste': 'Requests', 'Approvazioni': 'Approvals', 'Statistiche': 'Statistics',
  'Nuova richiesta': 'New request', 'Cerca un comando': 'Search a command', 'Cambia tema': 'Change theme', 'Cambia password': 'Change password', 'Esci': 'Log out',
  'disponibili': 'available', 'usati': 'used', 'in attesa': 'pending', 'totali': 'total',
  'ore': 'hours', 'giorni': 'days', 'ora': 'hour', 'giorno': 'day', 'giorno lavorativo': 'working day', 'gg': 'd', 'giorni in tutto': 'days in total', 'Annulla': 'Cancel', 'Salva': 'Save',

  // Allegati
  'Il file supera i {n} MB': 'The file exceeds {n} MB', 'Caricamento non riuscito': 'Upload failed', 'Allegato caricato': 'Attachment uploaded',
  'Allegato rimosso': 'Attachment removed', 'Rimuovi allegato': 'Remove attachment', 'Caricamento…': 'Loading…', 'Allega file': 'Attach file',

  // Palette comandi
  'Comandi': 'Commands', 'Cosa vuoi fare?': 'What do you want to do?', 'Nessun risultato': 'No results', 'Azioni': 'Actions',
  'Nuova richiesta di ferie': 'New holiday request', 'Nuovo permesso a ore': 'New hourly time off', 'Tema chiaro': 'Light theme', 'Tema scuro': 'Dark theme',
  'Vai a': 'Go to', 'Calendario area': 'Area calendar', 'Le mie richieste': 'My requests', 'Da approvare': 'To approve', 'Amministrazione': 'Administration',

  // Login e password
  'Addio Excel.': 'Goodbye Excel.', 'Ciao ferie.': 'Hello holidays.',
  'Chiedi, approva e pianifica le assenze del tuo team in pochi secondi.': 'Request, approve and plan your team\'s time off in seconds.',
  'Accesso…': 'Signing in…', 'Entra': 'Sign in',
  'La nuova password deve avere almeno 10 caratteri': 'The new password must be at least 10 characters', 'Le due password non coincidono': 'The two passwords do not match',
  'Password aggiornata': 'Password updated', 'Password attuale': 'Current password', 'Nuova password (min 10 caratteri)': 'New password (min 10 characters)',
  'Ripeti la nuova password': 'Repeat the new password', 'Salvataggio…': 'Saving…', 'Scegli la tua password': 'Choose your password',
  'È il primo accesso o la password è stata reimpostata: scegline una personale per continuare.': 'This is your first sign-in or your password was reset: choose a personal one to continue.',

  // Le mie richieste
  'Tutte': 'All', 'In corso': 'Open', 'Approvate': 'Approved', 'Chiuse': 'Closed',
  'Richiesta ritirata': 'Request withdrawn', 'Annullamento chiesto al responsabile': 'Cancellation requested from your manager',
  'Storico completo, con chi ha deciso e quando.': 'Full history, with who decided and when.', 'Niente da mostrare': 'Nothing to show',
  'Qui compariranno le tue richieste.': 'Your requests will appear here.', 'Certificato mancante': 'Certificate missing',
  'Rifiutata da {who}': 'Rejected by {who}', 'Decisa da {who}': 'Decided by {who}', 'il {date}': 'on {date}', 'Ritira': 'Withdraw', 'Togli': 'Remove',

  // Approvazioni
  '{n} collega assente nello stesso periodo': '{n} colleague absent in the same period', '{n} colleghi assenti nello stesso periodo': '{n} colleagues absent in the same period',
  "Nessuna sovrapposizione nell'area": 'No overlap in the area', 'Annullamento confermato per {who}': 'Cancellation confirmed for {who}', 'Approvata per {who}': 'Approved for {who}',
  'Decisione registrata': 'Decision recorded', 'Richieste delle persone che rientrano nella tua competenza.': 'Requests from the people you are responsible for.',
  'approva': 'approve', 'rifiuta': 'reject', 'Non ci sono richieste in attesa. Ottimo lavoro.': 'No pending requests. Great job.',
  'Conferma annullo': 'Confirm cancellation', 'Approva': 'Approve', 'Mantieni': 'Keep', 'Rifiuta': 'Reject', 'Rifiuta la richiesta di {who}': 'Reject {who}\'s request',
  'Motivo (obbligatorio)': 'Reason (required)', 'Lo vedrà la persona che ha fatto la richiesta.': 'The person who made the request will see it.',
  'Es. rilascio in produzione quella settimana': 'E.g. production release that week',

  // Home
  'Buongiorno': 'Good morning', 'Buon pomeriggio': 'Good afternoon', 'Buonasera': 'Good evening', 'Pianifica una pausa': 'Plan a break',
  'La tua prossima pausa': 'Your next break', 'Oggi!': 'Today!', 'giorno a {what}': 'day to {what}', 'giorni a {what}': 'days to {what}',
  'Nessuna pausa in programma': 'No break planned', "È il momento di staccare un po'?": 'Time for a break?', 'Prenota le ferie': 'Book holidays',
  'Il tuo andamento': 'Your summary', 'Permessi': 'Time off',
  'Conteggi dalle richieste approvate dell\'anno. "Assente" somma ferie, general leave e permessi (8 ore = 1 giorno). CO e trasferte non contano.':
    'Counts from this year\'s approved requests. "Absent" adds up holidays, general leave and time off (8 hours = 1 day). CO and business trips do not count.',
  'richieste da valutare': 'requests to review', 'Fuori oggi': 'Out today', 'Tutti presenti nella tua area.': 'Everyone is in today in your area.',
  'Vedi tutte': 'See all', 'Prossime assenze': 'Upcoming absences', 'Calendario libero': 'Calendar clear', 'Non hai richieste in programma.': 'You have no upcoming requests.',
  "Il tuo anno a colpo d'occhio": 'Your year at a glance',

  // Nuova richiesta
  "Richiesta inviata, ma l'allegato non è stato caricato: {err}": 'Request sent, but the attachment was not uploaded: {err}',
  'Assenza inserita': 'Absence added', 'Registrata per {who}, già approvata.': 'Recorded for {who}, already approved.',
  'Assenza registrata': 'Absence recorded', 'I responsabili sono stati avvisati.': 'Your managers have been notified.',
  'Registrato': 'Recorded', 'Visibile a tutti nel calendario.': 'Visible to everyone in the calendar.',
  'Richiesta inviata': 'Request sent', 'Il tuo responsabile riceverà la notifica.': 'Your manager will be notified.',
  'Per chi': 'For whom', "L'assenza viene registrata già approvata e la persona riceve un avviso. Puoi inserire anche date passate.": 'The absence is recorded as already approved and the person is notified. Past dates are allowed.',
  'Per me': 'For me', 'Tipo di assenza': 'Absence type', 'Giorno': 'Day', 'Dal': 'From', 'Al': 'To', 'Tipologia': 'Reason',
  "La vedono solo i tuoi responsabili e l'admin: ai colleghi compare solo «Assente».": 'Only your managers and the admin see it: colleagues only see "Absent".',
  'Scegli…': 'Choose…', "Non è un'assenza: viene registrato subito, senza approvazione, ed è visibile a tutti nel calendario.": 'This is not an absence: it is recorded immediately, with no approval, and is visible to everyone in the calendar.',
  'Ore di permesso: {n}': 'Hours off: {n}', 'Note (facoltative)': 'Notes (optional)',
  'Es. numero protocollo certificato…': 'E.g. certificate reference number…', 'Es. cliente, città…': 'E.g. client, city…', 'Es. viaggio, visita medica…': 'E.g. trip, medical appointment…',
  'Anteprima': 'Preview', '{unit} lavorativi': 'working {unit}', "{n} colleghi dell'area assenti in quei giorni": '{n} colleagues from the area absent on those days',
  "Nessun collega dell'area assente: via libera!": 'No colleague from the area is absent: all clear!', 'Calcolo in corso…': 'Calculating…',
  'Allegato (facoltativo)': 'Attachment (optional)',
  'Certificato o giustificativo: PDF, JPG o PNG fino a {n} MB. Puoi aggiungerlo anche dopo da Le mie richieste.': 'Certificate or supporting document: PDF, JPG or PNG up to {n} MB. You can also add it later from My requests.',
  'Invio…': 'Sending…', 'Inserisci assenza': 'Add absence', 'Registra': 'Record', 'Invia richiesta': 'Send request',

  // Calendario
  'Responsabili': 'Managers', 'Senza team': 'No team', 'Trascina sulla tua riga per prenotare dei giorni': 'Drag across your row to book days',
  'Tutte le aree': 'All areas', 'Mese precedente': 'Previous month', 'Mese successivo': 'Next month', 'Oggi': 'Today', 'assenze': 'absences',
  'Riepilogo e dettaglio del mese per HR e paghe (solo assenze approvate)': 'Monthly summary and detail for HR and payroll (approved absences only)',
  'Report mese': 'Monthly report', 'Tutti i team': 'All teams', 'Persone': 'People', 'tu': 'you',
  'Nessuna persona in questa selezione.': 'No people in this selection.', 'Weekend e festivi': 'Weekends and holidays',

  // Statistiche
  'CO · Cliente': 'CO · Client', 'BT · Trasferta': 'BT · Business trip', 'Totale storico': 'All-time total', "tutta l'azienda": 'whole company',
  'persone': 'people', 'persona': 'person', 'Statistiche assenze': 'Absence statistics', 'Apri calendario': 'Open calendar', 'Tutti': 'All',
  'Tipologia General leave': 'General leave reason', 'General leave: tutte le tipologie': 'General leave: all reasons',
  'Mese': 'Month', 'Anno': 'Year', 'Totale': 'Total', 'Precedente': 'Previous', 'Successivo': 'Next', 'Statistiche non disponibili': 'Statistics unavailable',
  'presenza': 'presence', 'Giorni di assenza nel periodo:': 'Absence days in the period:', 'a persona': 'per person',
  'Contano solo le richieste approvate e i giorni lavorativi; i permessi valgono 8 ore = 1 giorno; CO e BT sono presenze e non entrano nelle assenze.':
    'Only approved requests and working days count; time off counts as 8 hours = 1 day; CO and BT are presence and are not counted as absences.',
  'Andamento {year} per mese': '{year} by month', 'In giorni (permessi: 8 ore = 1 giorno). Clic su un mese per vederlo nel dettaglio.': 'In days (time off: 8 hours = 1 day). Click a month to see it in detail.',
  'General leave per tipologia': 'General leave by reason', 'Nessun General leave nel periodo.': 'No general leave in the period.',
  'Divisione per team': 'Breakdown by team', 'Composizione': 'Composition', 'Gg assenza / pers.': 'Absence days / person', 'anno': 'year',
  'Team × mese': 'Team × month', 'media a persona': 'average per person', 'Perm. (ore)': 'Time off (h)', 'Oggi sono tutti in ufficio.': 'Everyone is in the office today.',

  // Admin
  'Aree': 'Areas', 'Team': 'Teams', 'Festività': 'Public holidays', 'Storico': 'History',
  'Organizzazione, persone e chiusure aziendali.': 'Organisation, people and company closures.', 'Salvato': 'Saved',
  'Cerca persona, email, area…': 'Search person, email, area…', 'Nuova persona': 'New person', 'Persona': 'Person', 'Ruolo': 'Role',
  'disattivato': 'deactivated', 'Modifica': 'Edit', 'Modifica {name}': 'Edit {name}', 'Nome e cognome': 'Full name',
  'Nuova password (facoltativa)': 'New password (optional)', 'Password iniziale': 'Initial password', 'Almeno 10 caratteri': 'At least 10 characters',
  'Stato': 'Status', 'Attivo': 'Active', 'Disattivato': 'Deactivated', 'Area salvata': 'Area saved', 'non assegnato': 'not assigned',
  'Nuova area': 'New area', 'Modifica area': 'Edit area', 'Nome': 'Name', 'Team salvato': 'Team saved', 'Nuovo team': 'New team', 'Modifica team': 'Edit team',
  'Chiusura aggiunta': 'Closure added', 'Festività nazionale': 'National holiday', 'Chiusura aziendale': 'Company closure', 'Elimina': 'Delete',
  'Aggiungi chiusura': 'Add closure', 'Santo patrono, ponti aziendali, chiusure estive.': 'Patron saint day, company bridges, summer closures.',
  'Data': 'Date', 'Descrizione': 'Description', 'Aggiungi': 'Add', 'Cerca persona o dettaglio': 'Search person or detail',
  'operazioni': 'operations', '(ce ne sono altre)': '(more available)', 'Nessuna operazione trovata.': 'No operations found.',
  'di {name}': 'for {name}', 'Carica altre operazioni': 'Load more operations',
  // Storico (audit)
  'ha creato una richiesta': 'created a request', 'ha approvato': 'approved', 'ha rifiutato': 'rejected', 'ha ritirato': 'withdrew',
  "ha chiesto l'annullamento": 'requested cancellation', "ha confermato l'annullamento": 'confirmed the cancellation', "ha respinto l'annullamento": 'denied the cancellation',
  'ha allegato un file': 'attached a file', 'ha tolto un allegato': 'removed an attachment', 'promemoria certificato inviato': 'certificate reminder sent',
  'approvazione automatica (nessun responsabile)': 'automatic approval (no manager)', 'annullamento automatico (nessun responsabile)': 'automatic cancellation (no manager)',
  'malattia registrata': 'sick leave recorded', 'registrata senza approvazione': 'recorded without approval', "ha inserito un'assenza per conto di": 'added an absence for',
  'giorni ricalcolati (chiusura aziendale)': 'days recalculated (company closure)', 'ha creato un utente': 'created a user', 'ha modificato un utente': 'edited a user',
  'ha reimpostato una password': 'reset a password', 'ha trasformato le ferie ipotetiche in richiesta': 'turned tentative holidays into a request',

  // Festivita' nazionali
  'Capodanno': "New Year's Day", 'Epifania': 'Epiphany', 'Festa della Liberazione': 'Liberation Day', 'Festa dei Lavoratori': 'Labour Day',
  'Festa della Repubblica': 'Republic Day', 'Ferragosto': 'Assumption Day', 'Ognissanti': "All Saints' Day", 'Immacolata': 'Immaculate Conception',
  'Natale': 'Christmas Day', 'Santo Stefano': "St Stephen's Day", "Lunedi' dell'Angelo": 'Easter Monday', 'Pasquetta': 'Easter Monday',
}

/** Messaggi del server (italiano) -> inglese. Prima le frasi fisse, poi quelle con parti variabili. */
const SERVER: Record<string, string> = {
  'Credenziali non valide': 'Invalid credentials', 'Troppi tentativi falliti, riprova tra qualche minuto': 'Too many failed attempts, try again in a few minutes',
  'Devi cambiare la password prima di continuare': 'You must change your password before continuing', 'Sessione non valida': 'Invalid session',
  'Utente disattivato': 'User deactivated', 'Ruolo cambiato, accedi di nuovo': 'Role changed, please sign in again',
  'Operazione non consentita': 'Operation not allowed', 'Elemento non trovato': 'Item not found', 'Non trovato': 'Not found',
  'Link scaduto, riaprilo dall\'app': 'Link expired, open it again from the app', 'Dati non validi': 'Invalid data',
  'La richiesta e\' stata modificata da qualcun altro, ricarica la pagina': 'The request was changed by someone else, reload the page',
  'Dato duplicato o in uso': 'Duplicate or in-use value', 'Il file supera i 5 MB': 'The file exceeds 5 MB',
  'Anno non valido': 'Invalid year', 'Mese non valido': 'Invalid month', 'Password attuale errata': 'Current password is wrong',
  'La nuova password deve essere diversa da quella attuale': 'The new password must be different from the current one',
  'Scegli la tipologia': 'Choose the reason', 'La mezza giornata non e\' piu\' prevista: usa il permesso a ore': 'Half days are no longer available: use hourly time off',
  'La data di fine e\' prima della data di inizio': 'The end date is before the start date',
  'Si puo\' inserire un\'assenza al massimo un anno indietro': 'An absence can be added at most one year back',
  'Non si possono chiedere ferie o permessi nel passato': 'Holidays or time off cannot be requested in the past', 'Data troppo lontana nel futuro': 'Date too far in the future',
  'Il permesso a ore vale per un solo giorno': 'Hourly time off is for a single day', 'Indica quante ore di permesso': 'Enter how many hours off',
  'Le ore vanno indicate a multipli di mezz\'ora': 'Hours must be in multiples of half an hour', 'Il giorno scelto non e\' lavorativo': 'The chosen day is not a working day',
  'Nel periodo scelto non ci sono giorni lavorativi': 'There are no working days in the chosen period', 'Tipo non valido': 'Invalid type',
  'Hai gia\' una richiesta che si sovrappone a queste date': 'You already have a request overlapping these dates',
  'Hai gia\' una richiesta in quei giorni': 'You already have a request on those days',
  'La richiesta non e\' piu\' in attesa': 'The request is no longer pending', 'Indica il motivo del rifiuto': 'Enter the reason for rejecting',
  'Si possono ritirare solo richieste in attesa': 'Only pending requests can be withdrawn', 'Si possono annullare solo richieste approvate': 'Only approved requests can be cancelled',
  'Il periodo e\' gia\' trascorso': 'The period has already passed', 'Nessun annullamento da valutare': 'No cancellation to review',
  'La richiesta e\' stata ritirata': 'The request was withdrawn', 'Il file e\' vuoto': 'The file is empty', 'File non leggibile': 'Unreadable file',
  'Formato non ammesso: carica un PDF, JPG o PNG': 'Format not allowed: upload a PDF, JPG or PNG', 'Area inesistente': 'Area does not exist',
  'Team inesistente': 'Team does not exist', 'Il team non appartiene all\'area scelta': 'The team does not belong to the chosen area',
  'Assegna un\'area all\'utente': 'Assign an area to the user', 'Username gia\' in uso': 'Username already in use', 'Email gia\' in uso': 'Email already in use',
  'Non puoi togliere a te stesso il ruolo di Admin o disattivarti': 'You cannot remove your own Admin role or deactivate yourself',
  'Utente inesistente': 'User does not exist', 'Esiste gia\' una chiusura in quella data': 'There is already a closure on that date',
  'Intervallo non valido (max 3 mesi)': 'Invalid range (max 3 months)',
  'Le ferie ipotetiche si mettono solo da oggi in avanti': 'Tentative holidays can only be set from today onwards',
  'Si possono trasformare solo ferie ipotetiche (TH) attive': 'Only active tentative holidays (TH) can be converted', 'Token non valido': 'Invalid token',
}
const ROLE_EN: Record<string, string> = { USER: 'Employee', TEAM_LEAD: 'Team Manager / Tech Lead', PROJECT_MANAGER: 'Project Manager', ADMIN: 'Admin' }
const PATTERNS: [RegExp, (m: RegExpMatchArray) => string][] = [
  [/^Una singola richiesta puo' coprire al massimo (\d+) giorni$/, m => `A single request can cover at most ${m[1]} days`],
  [/^Si puo' registrare al massimo (\d+) giorni indietro$/, m => `You can record at most ${m[1]} days back`],
  [/^Hai gia' (\d+) richieste in attesa$/, m => `You already have ${m[1]} pending requests`],
  [/^(.+) ha gia' un'assenza che si sovrappone a queste date$/, m => `${m[1]} already has an absence overlapping these dates`],
  [/^(.+) ha gia' un'assenza in quei giorni$/, m => `${m[1]} already has an absence on those days`],
  [/^Puoi allegare al massimo (\d+) file per richiesta$/, m => `You can attach at most ${m[1]} files per request`],
  [/^L'utente scelto deve avere ruolo (\w+)$/, m => `The chosen user must have the ${ROLE_EN[m[1]] ?? m[1]} role`],
]

export function translateServerEn(s: string) {
  if (SERVER[s]) return SERVER[s]
  if (EN[s]) return EN[s]
  for (const [re, f] of PATTERNS) { const m = s.match(re); if (m) return f(m) }
  return s
}
