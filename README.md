# Ferie · Holidays Plan

App aziendale per ferie, permessi a ore e malattia, pensata per sostituire il file Excel.

## Ruoli

| Ruolo | Cosa fa |
| --- | --- |
| **Admin** | Vede tutte le aree, gestisce persone, aree, team, saldi e festività. Approva anche i PM. |
| **Project Manager** | Responsabile di un'area: vede il calendario completo e approva User e Team Manager/Tech Lead dell'area. |
| **Team Manager / Tech Lead** | Guida un team dentro l'area, vede il calendario completo dell'area e approva gli User. |
| **User** | Chiede ferie/permessi/malattia, vede il proprio saldo e chi è assente nell'area (senza tipo né note). |

Nessuno approva le proprie richieste: decide sempre un livello superiore. Basta una sola approvazione.

## Sviluppo in locale

Requisiti: Java 17+, Maven, Node 20+.

```bash
# Backend (profilo dev: H2 in memoria + dati demo, Swagger su /swagger-ui.html)
mvn spring-boot:run

# Frontend con hot reload su http://localhost:5173
cd frontend && npm install && npm run dev
```

Utenti demo (password `password123!`): `admin`, `pm`, `techlead`, `techlead2`, `user`, `sara`, `davide`, `elena`, `pm2`, `andrea`.

Test: `mvn test` (regole di permesso, flusso approvazioni, saldo, festività).

## Produzione

```bash
docker build -t holidays-plan .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host:5432/ferie -e DB_USER=... -e DB_PASSWORD=... \
  -e JWT_SECRET=<almeno 32 caratteri casuali> \
  holidays-plan
```

Variabili: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (obbligatorie), `JWT_TTL_HOURS` (default 8), `CORS_ALLOWED_ORIGINS` (solo se il frontend è su un altro dominio), `PORT`.

Lo schema del database è gestito da Flyway (`src/main/resources/db/migration`). Il primo Admin in produzione va inserito a mano (vedi sotto) e poi crea tutti gli altri dall'interfaccia.

```sql
-- password generata con BCrypt (cost 12)
INSERT INTO users (username, email, full_name, password_hash, role, active, annual_leave_days, annual_permit_hours)
VALUES ('admin', 'admin@azienda.it', 'Amministratore', '<hash bcrypt>', 'ADMIN', TRUE, 26, 32);
```

L'immagine Docker è portabile: gira uguale su Render, AWS (ECS/App Runner + RDS PostgreSQL) o GCP (Cloud Run + Cloud SQL).
